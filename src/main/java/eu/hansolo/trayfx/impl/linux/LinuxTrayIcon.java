package eu.hansolo.trayfx.impl.linux;

import eu.hansolo.trayfx.AbstractTrayIcon;
import eu.hansolo.trayfx.menu.MenuItem;
import eu.hansolo.trayfx.menu.TrayMenu;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;

import java.awt.AWTException;
import java.awt.Dimension;
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
 * <h2>Icon scaling</h2>
 * AWT's {@code setImageAutoSize} crops rather than scales on Linux/GTK.
 * We pre-scale to the exact size reported by {@code SystemTray.getTrayIconSize()}
 * using bicubic interpolation and set {@code setImageAutoSize(false)}.
 * The tray size is queried once after the icon is added and cached.
 *
 * <h2>Menu</h2>
 * AWT {@code PopupMenu} on Linux/GTK becomes stale after the first display.
 * We rebuild it fresh on every right-click (detected via {@code BUTTON3}) and
 * show it via a minimal hidden {@code Frame} — the most reliable approach
 * across GNOME, KDE and XFCE without triggering recursive mouse events.
 */
public final class LinuxTrayIcon extends AbstractTrayIcon {

    private volatile java.awt.TrayIcon awtTrayIcon;
    private volatile int               trayIconSize = 22; // updated after add()


    @Override protected void nativeInstall() {
        if (!SystemTray.isSupported()) {
            throw new UnsupportedOperationException(
                "SystemTray is not supported in this Linux environment. " +
                "Wayland compositors may require XWayland.");
        }
        offThread(() -> {
            try {
                // Query tray size before creating the icon
                final Dimension d = SystemTray.getSystemTray().getTrayIconSize();
                if (d != null && d.width > 0) { trayIconSize = d.width; }

                awtTrayIcon = new java.awt.TrayIcon(toBufferedImage(getIcon()));
                awtTrayIcon.setImageAutoSize(false); // we pre-scale to exact tray size

                if (getText() != null) { awtTrayIcon.setToolTip(getText()); }

                awtTrayIcon.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(final MouseEvent e) {
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
        // Menu is rebuilt fresh on each right-click in showFreshMenu()
    }

    private void showFreshMenu(final MouseEvent e) {
        final TrayMenu menu = getMenu();
        if (menu == null || menu.isEmpty()) { return; }

        // Build a fresh PopupMenu — GTK corrupts reused native menu handles
        final PopupMenu popup = buildPopupMenu(menu);

        // Show via a minimal hidden Frame positioned at the click location.
        // This is the most reliable approach on Linux — avoids the recursive
        // mouse event loop that Robot injection causes, and works across
        // GNOME, KDE, and XFCE.
        final Frame frame = new Frame();
        frame.setUndecorated(true);
        frame.setSize(1, 1);
        frame.setLocation(e.getXOnScreen(), e.getYOnScreen());
        frame.add(popup);
        frame.setVisible(true);
        popup.show(frame, 0, 0);
        frame.addWindowFocusListener(new WindowAdapter() {
            @Override public void windowLostFocus(final WindowEvent we) {
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

    private BufferedImage toBufferedImage(final Image fxImage) {
        if (fxImage == null) {
            return new BufferedImage(trayIconSize, trayIconSize, BufferedImage.TYPE_INT_ARGB);
        }

        final int srcW = (int) fxImage.getWidth();
        final int srcH = (int) fxImage.getHeight();
        final BufferedImage src = new BufferedImage(srcW, srcH, BufferedImage.TYPE_INT_ARGB);
        SwingFXUtils.fromFXImage(fxImage, src);

        final int size = trayIconSize;
        if (srcW == size && srcH == size) { return src; }

        // Scale to exact tray size using bicubic interpolation
        final BufferedImage scaled = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        final java.awt.Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                           java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING,
                           java.awt.RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                           java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(src, 0, 0, size, size, null);
        g.dispose();
        return scaled;
    }

    private static void offThread(final Runnable task) {
        final Thread t = new Thread(task, "trayfx-awt");
        t.setDaemon(true);
        t.start();
    }
}
