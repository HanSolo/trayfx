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
                "Wayland compositors may require XWayland.");
        }
        offThread(() -> {
            try {
                awtTrayIcon = new java.awt.TrayIcon(toBufferedImage(getIcon()));
                awtTrayIcon.setImageAutoSize(false); // we pre-scale to exact tray size

                if (getText() != null) { awtTrayIcon.setToolTip(getText()); }

                awtTrayIcon.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(final MouseEvent e) {
                        if (e.getButton() == MouseEvent.BUTTON1) {
                            // Left click — fire handler
                            fireLeftClick();
                        } else if (e.getButton() == MouseEvent.BUTTON3) {
                            // Right click — show fresh menu
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
        // Menu is built fresh on each right-click in showFreshMenu()
    }

    private void showFreshMenu(final MouseEvent e) {
        final TrayMenu menu = getMenu();
        if (menu == null || menu.isEmpty()) { return; }

        final java.awt.TrayIcon t = awtTrayIcon;
        if (t == null) { return; }

        // Build a fresh PopupMenu each time — GTK corrupts reused instances
        final java.awt.PopupMenu popup = buildPopupMenu(menu);

        // Set it on the tray icon — AWT/GTK will show it at the click location
        t.setPopupMenu(popup);

        // Simulate a right-click on the tray icon to trigger the popup.
        // We use Robot to inject the right-click event since there is no
        // direct API to programmatically show a TrayIcon's PopupMenu on Linux.
        try {
            final java.awt.Robot robot = new java.awt.Robot();
            robot.mouseMove(e.getXOnScreen(), e.getYOnScreen());
            robot.mousePress(java.awt.event.InputEvent.BUTTON3_DOWN_MASK);
            robot.mouseRelease(java.awt.event.InputEvent.BUTTON3_DOWN_MASK);
        } catch (java.awt.AWTException ex) {
            // Robot not available — fall back to direct popup.show()
            showPopupViaFrame(popup, e);
        }
    }

    private void showPopupViaFrame(final java.awt.PopupMenu popup, final MouseEvent e) {
        final java.awt.Frame frame = new java.awt.Frame();
        frame.setUndecorated(true);
        frame.setSize(1, 1);
        frame.setLocation(e.getXOnScreen(), e.getYOnScreen());
        frame.add(popup);
        frame.setVisible(true);
        popup.show(frame, 0, 0);
        frame.addWindowFocusListener(new java.awt.event.WindowAdapter() {
            @Override public void windowLostFocus(final java.awt.event.WindowEvent we) {
                frame.dispose();
            }
        });
    }

    private java.awt.PopupMenu buildPopupMenu(final TrayMenu menu) {
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
        return popup;
    }

    private static BufferedImage toBufferedImage(final Image fxImage) {
        if (fxImage == null) {
            return new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        }

        // Convert JavaFX image to BufferedImage
        final int srcW = (int) fxImage.getWidth();
        final int srcH = (int) fxImage.getHeight();
        final BufferedImage src = new BufferedImage(srcW, srcH, BufferedImage.TYPE_INT_ARGB);
        SwingFXUtils.fromFXImage(fxImage, src);

        // Scale to the exact size the tray expects
        final int traySize = getTrayIconSize();
        if (srcW == traySize && srcH == traySize) { return src; }

        final BufferedImage scaled = new BufferedImage(traySize, traySize, BufferedImage.TYPE_INT_ARGB);
        final java.awt.Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                           java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING,
                           java.awt.RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                           java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(src, 0, 0, traySize, traySize, null);
        g.dispose();
        return scaled;
    }

    private static int getTrayIconSize() {
        if (SystemTray.isSupported()) {
            final java.awt.Dimension d = SystemTray.getSystemTray().getTrayIconSize();
            if (d != null && d.width > 0) { return d.width; }
        }
        return 22;
    }

    private static void offThread(final Runnable task) {
        final Thread t = new Thread(task, "trayfx-awt");
        t.setDaemon(true);
        t.start();
    }
}
