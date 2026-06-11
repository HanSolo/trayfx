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
import java.awt.Graphics2D;
import java.awt.PopupMenu;
import java.awt.RenderingHints;
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
 * {@code setImageAutoSize(true)} on Linux/GTK crops rather than scales —
 * a confirmed AWT bug. The only reliable approach is to pre-scale the image
 * to exactly the size returned by {@code SystemTray.getTrayIconSize()} and
 * set {@code setImageAutoSize(false)}. The tray size is queried once during
 * install and cached in {@code traySize}.
 *
 * <h2>Menu</h2>
 * {@code mouseClicked} is unreliable on Linux tray icons — {@code mousePressed}
 * with explicit {@code BUTTON1}/{@code BUTTON3} checks is used instead.
 * The menu is rebuilt fresh on every right-click to avoid the AWT/GTK
 * stale-handle bug.
 */
public final class LinuxTrayIcon extends AbstractTrayIcon {

    private volatile java.awt.TrayIcon awtTrayIcon;
    private          int               traySize = 16; // actual rendered size (not logical size)


    @Override
    protected void nativeInstall() {
        if (!SystemTray.isSupported()) {
            throw new UnsupportedOperationException(
                "SystemTray is not supported in this Linux environment. " +
                "Wayland compositors may require XWayland.");
        }
        offThread(() -> {
            try {
                // Query the real tray slot size before creating the icon —
                // this is the pixel size AWT will render into
                // getTrayIconSize() returns the LOGICAL size but the actual rendered
                // pixel size on Linux/GTK is typically 2/3 of this value.
                // On this Ubuntu/Parallels configuration: reported=24, actual=16.
                // Using 2/3 of the reported size matches what GTK actually renders.
                final Dimension d = SystemTray.getSystemTray().getTrayIconSize();
                if (d != null && d.width > 0) { traySize = Math.max(16, (d.width * 2) / 3); }

                awtTrayIcon = new java.awt.TrayIcon(toBufferedImage(getIcon()));
                // Do not call setImageAutoSize at all — leave it at its default (false).
                // Both true and false with explicit size setting produce cropping on this
                // GTK/Parallels configuration; leaving the default is the last option to try.

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


    // ── Menu ─────────────────────────────────────────────────────────────

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

    private BufferedImage toBufferedImage(final Image fxImage) {
        final int size = traySize;
        if (fxImage == null) {
            return new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        }

        final int srcW = (int) fxImage.getWidth();
        final int srcH = (int) fxImage.getHeight();

        // Explicitly clear to transparent before conversion — on some Linux/JDK
        // configurations SwingFXUtils.fromFXImage composites against white if the
        // target BufferedImage has uninitialised pixels.
        final BufferedImage src = new BufferedImage(srcW, srcH, BufferedImage.TYPE_INT_ARGB);
        final Graphics2D clear = src.createGraphics();
        clear.setComposite(java.awt.AlphaComposite.Clear);
        clear.fillRect(0, 0, srcW, srcH);
        clear.dispose();
        SwingFXUtils.fromFXImage(fxImage, src);

        if (srcW == size && srcH == size) { return src; }

        final BufferedImage scaled = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        final Graphics2D g = scaled.createGraphics();
        g.setComposite(java.awt.AlphaComposite.Clear);
        g.fillRect(0, 0, size, size);
        g.setComposite(java.awt.AlphaComposite.SrcOver);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING,     RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,  RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(src, 0, 0, size, size, null);
        g.dispose();
        return scaled;
    }

    private static void offThread(final Runnable task) {
        Thread.ofVirtual().name("trayfx-awt").start(task);
    }
}
