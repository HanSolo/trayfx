package eu.hansolo.trayfx.impl.linux;

import eu.hansolo.trayfx.AbstractTrayIcon;
import eu.hansolo.trayfx.menu.TrayMenu;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder;


/**
 * D-Bus StatusNotifierItem backend for Linux.
 *
 * <p>This class is only compiled on Linux (excluded on macOS/Windows via
 * {@code build.gradle}). It is loaded reflectively by {@link LinuxTrayIcon}
 * so that {@code LinuxTrayIcon} itself compiles on all platforms without
 * importing dbus-java types.
 * <p>Constructor takes the parent {@link LinuxTrayIcon} as argument so that
 * {@code onNativeReady()} can be called on the correct base instance.
 */
public final class LinuxDbusImpl extends AbstractTrayIcon {
    private static final String                   SNI_WATCHER_BUS  = "org.kde.StatusNotifierWatcher";
    private static final String                   SNI_WATCHER_PATH = "/StatusNotifierWatcher";

    private        final AbstractTrayIcon         parent;
    private              DBusConnection           connection;
    private              StatusNotifierItemExport sniExport;


    public LinuxDbusImpl(final AbstractTrayIcon parent) {
        this.parent = parent;
    }


    @Override protected void nativeInstall() {
        offThread(() -> {
            try {
                connection = DBusConnectionBuilder.forSessionBus().withShared(false).build();

                final String pid     = getPid();
                final String busName = "org.kde.StatusNotifierItem-" + pid + "-1";
                connection.requestBusName(busName);

                sniExport = new StatusNotifierItemExport(connection, v -> AbstractTrayIcon.doFireLeftClick(parent), v -> AbstractTrayIcon.doFireRightClick(parent));
                connection.exportObject(StatusNotifierItemExport.OBJECT_PATH, sniExport);
                connection.exportObject(StatusNotifierItemExport.MENU_PATH, sniExport.getMenuExport());

                if (getIcon() != null) { sniExport.setIcon(getIcon()); }
                if (getText() != null) {
                    sniExport.setTitle(getText());
                    sniExport.setToolTip(getText());
                }
                if (getMenu() != null) { sniExport.setMenu(getMenu()); }

                // Register with object path format expected by Ayatana/Ubuntu AppIndicator
                registerWithWatcher(StatusNotifierItemExport.OBJECT_PATH);

                // Signal both this impl and the parent that we're ready
                onNativeReady();
                AbstractTrayIcon.doOnNativeReady(parent);

            } catch (final Exception e) {
                throw new RuntimeException("D-Bus StatusNotifierItem install failed", e);
            }
        });
    }

    @Override protected void nativeUninstall() {
        offThread(() -> {
            if (connection != null) {
                try { connection.disconnect(); } catch (final Exception ignored) {}
                connection = null;
                sniExport  = null;
            }
        });
    }

    @Override protected void nativeUpdateIcon(final Image icon) {
        offThread(() -> {
            final StatusNotifierItemExport s = sniExport;
            if (s != null) { s.setIcon(icon); }
        });
    }

    @Override protected void nativeUpdateText(final String text, final Color color) {
        offThread(() -> {
            final StatusNotifierItemExport s = sniExport;
            if (s != null) {
                s.setTitle(text   != null ? text : "");
                s.setToolTip(text != null ? text : "");
            }
        });
    }

    @Override protected void nativeUpdateMenu(final TrayMenu menu) {
        offThread(() -> {
            final StatusNotifierItemExport s = sniExport;
            if (s != null) { s.setMenu(menu); }
        });
    }

    @Override protected void nativeShowNotification(final String title, final String message) {
        offThread(() -> {
            try {
                final Image currentIcon = getIcon();
                if (currentIcon != null) {
                    // Write icon to temp file and pass to notify-send
                    final java.io.File tmp = writeIconToTemp(currentIcon);
                    if (tmp != null) {
                        new ProcessBuilder("notify-send", "--icon", tmp.getAbsolutePath(), "--app-name", "TrayFX", title != null ? title : "", message != null ? message : "").redirectErrorStream(true).start();
                        tmp.deleteOnExit();
                        return;
                    }
                }
                // No icon — send without
                new ProcessBuilder("notify-send", "--app-name", "TrayFX", title != null ? title : "", message != null ? message : "").redirectErrorStream(true).start();
            } catch (final Exception ignored) {}
        });
    }

    private static java.io.File writeIconToTemp(final Image fxImage) {
        try {
            final java.io.File                 tmpFile       = java.io.File.createTempFile("trayfx-icon-", ".png");
            final java.awt.image.BufferedImage bufferedImage = new java.awt.image.BufferedImage((int) fxImage.getWidth(), (int) fxImage.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
            final java.awt.Graphics2D          g2d           = bufferedImage.createGraphics();
            g2d.setComposite(java.awt.AlphaComposite.Clear);
            g2d.fillRect(0, 0, bufferedImage.getWidth(), bufferedImage.getHeight());
            g2d.dispose();
            javafx.embed.swing.SwingFXUtils.fromFXImage(fxImage, bufferedImage);
            javax.imageio.ImageIO.write(bufferedImage, "png", tmpFile);
            return tmpFile;
        } catch (final Exception ignored) {
            return null;
        }
    }

    private void registerWithWatcher(final String busName) {
        try {
            final StatusNotifierWatcherInterface watcher = connection.getRemoteObject(SNI_WATCHER_BUS, SNI_WATCHER_PATH, StatusNotifierWatcherInterface.class);
            watcher.RegisterStatusNotifierItem(busName);
        } catch (final Exception ignored) {
            // Watcher may not be available — icon will appear when a tray host starts
        }
    }

    private static String getPid() {
        return String.valueOf(ProcessHandle.current().pid());
    }

    private static void offThread(final Runnable task) {
        Thread.ofVirtual().name("trayfx-dbus").start(task);
    }
}
