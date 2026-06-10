package eu.hansolo.trayfx.impl.linux;

import eu.hansolo.trayfx.AbstractTrayIcon;
import eu.hansolo.trayfx.menu.MenuItem;
import eu.hansolo.trayfx.menu.TrayMenu;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;

import java.awt.AWTException;
import java.awt.SystemTray;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;


/**
 * Linux implementation of {@link eu.hansolo.trayfx.TrayIcon}.
 *
 * <h2>Icon size</h2>
 * Linux system trays vary by desktop environment and DPI. Rather than
 * supplying a small image and relying on upscaling (which looks blurry),
 * we supply a larger image (64×64) and let the tray scale it down.
 * {@code setImageAutoSize(true)} is kept so the tray handles any remaining
 * size adjustment without clipping.
 *
 * <h2>Menu</h2>
 * AWT's {@code PopupMenu} on Linux/GTK enters a broken state after being
 * shown once — subsequent invocations render an empty or incomplete menu.
 * The workaround is to rebuild the {@code PopupMenu} fresh on every
 * right-click via a {@code MouseListener}, rather than setting it once
 * via {@code setPopupMenu}.
 */
public final class LinuxTrayIcon extends AbstractTrayIcon {
    private volatile java.awt.TrayIcon awtTrayIcon;

    @Override protected void nativeInstall() {
        if (!SystemTray.isSupported()) {
            throw new UnsupportedOperationException(
                "SystemTray is not supported in this Linux environment. " +
                "Wayland compositors may require XWayland or a compatible " +
                "StatusNotifierItem implementation.");
        }
        offThread(() -> {
            try {
                awtTrayIcon = new java.awt.TrayIcon(toBufferedImage(getIcon()));
                awtTrayIcon.setImageAutoSize(true);
                if (getText() != null) { awtTrayIcon.setToolTip(getText()); }

                // Rebuild menu fresh on every right-click to work around the
                // AWT/GTK PopupMenu reuse bug where the menu shows incomplete
                // or not at all on second and subsequent invocations.
                awtTrayIcon.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mousePressed(final MouseEvent e) {
                        if (e.isPopupTrigger()) { showFreshMenu(e); }
                    }
                    @Override
                    public void mouseReleased(final MouseEvent e) {
                        if (e.isPopupTrigger()) { showFreshMenu(e); }
                    }
                });

                // Left-click handler
                awtTrayIcon.addActionListener(e -> fireLeftClick());

                SystemTray.getSystemTray().add(awtTrayIcon);
                onNativeReady();
            } catch (AWTException e) {
                throw new RuntimeException("Failed to install Linux tray icon", e);
            }
        });
    }

    @Override protected void nativeUninstall() {
        offThread(() -> {
            final java.awt.TrayIcon icon = awtTrayIcon;
            awtTrayIcon = null;
            if (icon != null) { SystemTray.getSystemTray().remove(icon); }
        });
    }

    @Override protected void nativeUpdateIcon(final Image icon) {
        offThread(() -> {
            final java.awt.TrayIcon t = awtTrayIcon;
            if (t != null) { t.setImage(toBufferedImage(icon)); }
        });
    }

    @Override protected void nativeUpdateText(final String text, final Color color) {
        offThread(() -> {
            final java.awt.TrayIcon t = awtTrayIcon;
            if (t != null) { t.setToolTip(text); }
        });
    }

    @Override
    protected void nativeUpdateMenu(final TrayMenu menu) {
        // Nothing to do here — menu is built fresh on each click in showFreshMenu()
    }

    private void showFreshMenu(final MouseEvent e) {
        final TrayMenu menu = getMenu();
        if (menu == null || menu.isEmpty()) { return; }

        // Build a brand-new PopupMenu each time — reusing the same instance
        // causes GTK to show a broken/empty menu on second invocation
        final java.awt.PopupMenu popup = new java.awt.PopupMenu();
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

        // Attach fresh menu, show it, then detach — prevents the stale
        // native menu handle from being held between invocations
        final java.awt.TrayIcon t = awtTrayIcon;
        if (t == null) { return; }
        t.setPopupMenu(popup);

        // Use a tiny hidden Frame as the popup parent — required on some
        // Linux desktop environments for PopupMenu.show() to work correctly
        final java.awt.Frame frame = new java.awt.Frame();
        frame.setUndecorated(true);
        frame.setSize(1, 1);
        frame.setLocation(e.getXOnScreen(), e.getYOnScreen());
        frame.setVisible(true);
        popup.show(frame, 0, 0);
        frame.addWindowFocusListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowLostFocus(final java.awt.event.WindowEvent we) {
                frame.dispose();
            }
        });
    }

    private static BufferedImage toBufferedImage(final Image fxImage) {
        if (fxImage == null) { return new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB); }
        // Use 64×64 as the source size — large enough that downscaling by
        // the tray looks crisp, avoiding the blurriness of upscaling a small image
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
