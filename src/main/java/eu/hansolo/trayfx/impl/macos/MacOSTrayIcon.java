package eu.hansolo.trayfx.impl.macos;

import eu.hansolo.trayfx.AbstractTrayIcon;
import eu.hansolo.trayfx.menu.MenuItem;
import eu.hansolo.trayfx.menu.TrayMenu;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;

import java.awt.AWTException;
import java.awt.SystemTray;
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
                // Signal the base class that native init is complete any queued updates (setIcon, setText, setMenu called before install finished) will now be drained and applied in order.
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
        final java.awt.TrayIcon t = awtTrayIcon;
        if (t == null) { return; }

        final TrayMenu menu = getMenu();
        if (menu == null || menu.isEmpty()) {
            t.setPopupMenu(null);
            return;
        }

        final java.awt.PopupMenu popup = new java.awt.PopupMenu();
        for (final MenuItem item : menu.getItems()) {
            if (item.isSeparator()) {
                popup.addSeparator();
            } else {
                final java.awt.MenuItem awtItem = new java.awt.MenuItem(item.getLabel());
                awtItem.setEnabled(item.isEnabled());
                awtItem.addActionListener(e -> Platform.runLater(item::fire));
                popup.add(awtItem);
            }
        }
        t.setPopupMenu(popup);
    }

    private static BufferedImage toBufferedImage(final Image fxImage) {
        if (fxImage == null) { return new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB); }
        final int w = (int) fxImage.getWidth();
        final int h = (int) fxImage.getHeight();
        final BufferedImage argb = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        SwingFXUtils.fromFXImage(fxImage, argb);
        return argb;
    }

    /** Runs a task on a short-lived daemon thread. */
    private static void offThread(final Runnable task) {
        final Thread t = new Thread(task, "trayfx-awt");
        t.setDaemon(true);
        t.start();
    }
}
