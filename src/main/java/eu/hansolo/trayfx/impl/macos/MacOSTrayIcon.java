package eu.hansolo.trayfx.impl.macos;

import eu.hansolo.trayfx.AbstractTrayIcon;
import eu.hansolo.trayfx.menu.TrayMenu;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;

import java.awt.*;
import java.awt.image.BufferedImage;


/**
 * macOS implementation backed by {@code java.awt.SystemTray}.
 *
 * <h2>Threading</h2>
 * {@code SystemTray.add/remove} must never be called from the JavaFX
 * Application Thread on macOS — doing so deadlocks because AWT internally
 * dispatches to the Cocoa main thread which is already occupied.
 * All AWT calls are made from a dedicated daemon thread.
 * All menu action callbacks are delivered on the JavaFX Application Thread
 * via {@code Platform.runLater}.
 */
public final class MacOSTrayIcon extends AbstractTrayIcon {
    private volatile java.awt.TrayIcon awtTrayIcon;

    @Override protected void nativeInstall() {
        offThread(() -> {
            try {
                awtTrayIcon = new java.awt.TrayIcon(toBufferedImage(getIcon()));
                awtTrayIcon.setImageAutoSize(true);
                if (getText() != null) { awtTrayIcon.setToolTip(getText()); }
                applyAwtMenu();
                SystemTray.getSystemTray().add(awtTrayIcon);
                onNativeReady();
            } catch (AWTException e) {
                throw new RuntimeException("Failed to install tray icon", e);
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

    @Override protected void nativeUpdateMenu(final TrayMenu menu) { offThread(this::applyAwtMenu); }


    private void applyAwtMenu() {
        final java.awt.TrayIcon trayIcon = awtTrayIcon;
        if (trayIcon == null) { return; }

        final TrayMenu trayMenu = getMenu();
        if (trayMenu == null || trayMenu.isEmpty()) {
            trayIcon.setPopupMenu(null);
            return;
        }

        final java.awt.PopupMenu popup = new java.awt.PopupMenu();
        trayMenu.getItems().forEach(item -> {
            if (item.isSeparator()) {
                popup.addSeparator();
            } else {
                final MenuItem awtItem = new MenuItem(item.getLabel());
                awtItem.setEnabled(item.isEnabled());
                awtItem.addActionListener(e -> Platform.runLater(item::fire));
                popup.add(awtItem);
            }
        });
        trayIcon.setPopupMenu(popup);
    }

    private static BufferedImage toBufferedImage(final Image fxImage) {
        if (fxImage == null) { return new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB); }
        final int                 width  = (int) fxImage.getWidth();
        final int                 height = (int) fxImage.getHeight();
        final BufferedImage       argb   = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        final java.awt.Graphics2D g2d    = argb.createGraphics();
        g2d.setComposite(java.awt.AlphaComposite.Clear);
        g2d.fillRect(0, 0, width, height);
        g2d.dispose();
        SwingFXUtils.fromFXImage(fxImage, argb);
        return argb;
    }

    /** Runs a task on a short-lived daemon thread. */
    private static void offThread(final Runnable task) {
        Thread.ofVirtual().name("trayfx-awt").start(task);
    }
}
