package eu.hansolo.trayfx.impl.linux;

import eu.hansolo.trayfx.AbstractTrayIcon;
import eu.hansolo.trayfx.menu.MenuItem;
import eu.hansolo.trayfx.menu.TrayMenu;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;

import java.awt.AWTException;
import java.awt.Frame;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;


/**
 * Linux implementation of {@link eu.hansolo.trayfx.TrayIcon}.
 *
 * <h2>Icon sizing</h2>
 * We use {@code setImageAutoSize(true)} with a 48×48 source image.
 * {@code SystemTray.getTrayIconSize()} is unreliable across Linux DEs and
 * VM environments — it may return values that don't match the actual rendered
 * slot. Providing a moderately large image and letting AWT scale it down
 * produces correct results across GNOME, KDE, XFCE, and Parallels VMs.
 *
 * <h2>Menu</h2>
 * {@code mouseClicked} is unreliable on Linux system tray icons — we use
 * {@code mousePressed} with explicit {@code BUTTON1}/{@code BUTTON3} checks.
 * The menu is rebuilt fresh on every right-click to avoid the AWT/GTK
 * stale-handle bug where reused menus show empty on second invocation.
 */
public final class LinuxTrayIcon extends AbstractTrayIcon {

    private volatile java.awt.TrayIcon awtTrayIcon;


    @Override
    protected void nativeInstall() {
        if (!SystemTray.isSupported()) {
            throw new UnsupportedOperationException(
                "SystemTray is not supported in this Linux environment. " +
                "Wayland compositors may require XWayland.");
        }
        offThread(() -> {
            try {
                awtTrayIcon = new java.awt.TrayIcon(toBufferedImage(getIcon()));
                // setImageAutoSize(true) lets AWT/GTK scale our 48×48 image
                // down to whatever the actual tray slot size is at runtime.
                // This is more reliable than getTrayIconSize() which returns
                // inconsistent values across DEs and VM environments.
                awtTrayIcon.setImageAutoSize(true);

                if (getText() != null) { awtTrayIcon.setToolTip(getText()); }

                awtTrayIcon.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mousePressed(final MouseEvent e) {
                        if (e.getButton() == MouseEvent.BUTTON1) {
                            fireLeftClick();
                        } else if (e.getButton() == MouseEvent.BUTTON3) {
                            showFreshMenu(e);
                    }
                    }
                });

                SystemTray.getSystemTray().add(awtTrayIcon);
                onNativeReady();
            } catch (AWTException e) {
                throw new RuntimeException("Failed to install Linux tray icon", e);
            }
        });
    }

    @Override
    protected void nativeUninstall() {
        offThread(() -> {
            final java.awt.TrayIcon icon = awtTrayIcon;
            awtTrayIcon = null;
            if (icon != null) { SystemTray.getSystemTray().remove(icon); }
        });
    }

    @Override
    protected void nativeUpdateIcon(final Image icon) {
        offThread(() -> {
            final java.awt.TrayIcon t = awtTrayIcon;
            if (t != null) { t.setImage(toBufferedImage(icon)); }
        });
    }

    @Override
    protected void nativeUpdateText(final String text, final Color color) {
        offThread(() -> {
            final java.awt.TrayIcon t = awtTrayIcon;
            if (t != null) { t.setToolTip(text); }
        });
    }

    @Override
    protected void nativeUpdateMenu(final TrayMenu menu) {
        // Menu is rebuilt fresh on each right-click in showFreshMenu()
    }

    private void showFreshMenu(final MouseEvent e) {
        final TrayMenu menu = getMenu();
        if (menu == null || menu.isEmpty()) { return; }

        final java.awt.TrayIcon t = awtTrayIcon;
        if (t == null) { return; }

        // Build a fresh PopupMenu — GTK corrupts reused native menu handles
        final PopupMenu popup = buildPopupMenu(menu);

        // Show via a minimal hidden Frame at the click position
        final Frame frame = new Frame();
        frame.setUndecorated(true);
        frame.setSize(1, 1);
        frame.setLocation(e.getXOnScreen(), e.getYOnScreen());
        frame.add(popup);
        frame.setVisible(true);
        popup.show(frame, 0, 0);
        frame.addWindowFocusListener(new WindowAdapter() {
            @Override
            public void windowLostFocus(final WindowEvent we) {
                frame.dispose();
            }
        });
    }

    private PopupMenu buildPopupMenu(final TrayMenu menu) {
        final PopupMenu popup = new PopupMenu();
        for (final MenuItem item : menu.getItems()) {
            if (item.isSeparator()) {
                popup.addSeparator();
            } else {
                final java.awt.MenuItem awtItem = new java.awt.MenuItem(item.getLabel());
                awtItem.setEnabled(item.isEnabled());
                awtItem.addActionListener(ev -> Platform.runLater(item::fire));
                popup.add(awtItem);
            }
        }
        return popup;
    }


    // ── Image conversion ─────────────────────────────────────────────────

    private static BufferedImage toBufferedImage(final Image fxImage) {
        if (fxImage == null) {
            return new BufferedImage(48, 48, BufferedImage.TYPE_INT_ARGB);
        }
        final int w = (int) fxImage.getWidth();
        final int h = (int) fxImage.getHeight();
        final BufferedImage argb = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        SwingFXUtils.fromFXImage(fxImage, argb);
        return argb;
    }

    private static void offThread(final Runnable task) {
        final Thread t = new Thread(task, "trayfx-awt");
        t.setDaemon(true);
        t.start();
    }
}
