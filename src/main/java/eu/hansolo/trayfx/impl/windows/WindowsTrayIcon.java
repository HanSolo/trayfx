package eu.hansolo.trayfx.impl.windows;

import eu.hansolo.trayfx.AbstractTrayIcon;
import eu.hansolo.trayfx.menu.TrayMenu;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;

import java.awt.*;
import java.awt.image.BufferedImage;


/**
 * Windows implementation of {@link eu.hansolo.trayfx.TrayIcon}.
 *
 * <p>Backed by {@code java.awt.SystemTray}, which maps to the Windows
 * notification area (system tray) via the Shell notification icon API.
 *
 * <p>Text is shown as a tooltip on hover. Colored text is not a native
 * Windows tray feature — encode color information into the icon instead
 * using {@link eu.hansolo.trayfx.TrayIconGraphics}.
 */
public final class WindowsTrayIcon extends AbstractTrayIcon {
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
                throw new RuntimeException("Failed to install Windows tray icon", e);
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

    @Override protected void nativeShowNotification(final String title, final String message) {
        offThread(() -> {
            final java.awt.TrayIcon t = awtTrayIcon;
            if (t != null) { t.displayMessage(title, message, java.awt.TrayIcon.MessageType.INFO); }
        });
    }


    private void applyAwtMenu() {
        final java.awt.TrayIcon t = awtTrayIcon;
        if (t == null) { return; }

        final TrayMenu trayMenu = getMenu();
        if (trayMenu == null || trayMenu.isEmpty()) {
            t.setPopupMenu(null);
            return;
        }

        final java.awt.PopupMenu popup = new java.awt.PopupMenu();
        trayMenu.getItems().forEach(item -> {
            if (item.isSeparator()) {
                popup.addSeparator();
            } else if (item.isCheckItem()) {
                final java.awt.CheckboxMenuItem chk = new java.awt.CheckboxMenuItem(item.getLabel(), item.isChecked());
                chk.setEnabled(item.isEnabled());
                chk.addItemListener(e -> Platform.runLater(item::fire));
                popup.add(chk);
            } else {
                final MenuItem awtItem = new MenuItem(item.getLabel());
                awtItem.setEnabled(item.isEnabled());
                awtItem.addActionListener(e -> Platform.runLater(item::fire));
                popup.add(awtItem);
            }
        });
        t.setPopupMenu(popup);
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

    private static void offThread(final Runnable task) {
        Thread.ofVirtual().name("trayfx-awt").start(task);
    }
}
