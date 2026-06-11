package eu.hansolo.trayfx.impl.linux;

import eu.hansolo.trayfx.AbstractTrayIcon;
import eu.hansolo.trayfx.menu.TrayMenu;
import javafx.application.Platform;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder;

import java.lang.management.ManagementFactory;


/**
 * Linux implementation of {@link eu.hansolo.trayfx.TrayIcon}.
 *
 * <h2>Backend: StatusNotifierItem via D-Bus</h2>
 * Uses the {@code org.kde.StatusNotifierItem} D-Bus protocol rather than
 * {@code java.awt.SystemTray}. This provides:
 * <ul>
 *   <li>Correct ARGB32 transparency — icons are passed as raw pixel arrays
 *       over D-Bus, bypassing the GTK/XEmbed alpha-compositing bug that
 *       causes white backgrounds with AWT SystemTray.</li>
 *   <li>Native menu rendering via {@code com.canonical.dbusmenu}.</li>
 *   <li>Works on all modern Linux DEs (GNOME with AppIndicator extension,
 *       KDE Plasma, XFCE, etc.)</li>
 * </ul>
 *
 * <h2>Registration flow</h2>
 * <ol>
 *   <li>Connect to the D-Bus session bus.</li>
 *   <li>Request the bus name {@code org.kde.StatusNotifierItem-{pid}-1}.</li>
 *   <li>Export the {@code /StatusNotifierItem} object.</li>
 *   <li>Export the {@code /StatusNotifierItem/Menu} object.</li>
 *   <li>Call {@code RegisterStatusNotifierItem} on
 *       {@code org.kde.StatusNotifierWatcher} so the tray host discovers us.</li>
 * </ol>
 */
public final class LinuxTrayIcon extends AbstractTrayIcon {

    private static final String SNI_WATCHER_BUS  = "org.kde.StatusNotifierWatcher";
    private static final String SNI_WATCHER_PATH = "/StatusNotifierWatcher";
    private static final String SNI_WATCHER_IFACE = "org.kde.StatusNotifierWatcher";

    private DBusConnection             connection;
    private StatusNotifierItemExport   sniExport;


    @Override
    protected void nativeInstall() {
        offThread(() -> {
            try {
                // Connect to the D-Bus session bus
                connection = DBusConnectionBuilder.forSessionBus()
                    .withShared(false)
                    .build();

                // Request unique bus name: org.kde.StatusNotifierItem-{pid}-1
                final String pid     = getPid();
                final String busName = "org.kde.StatusNotifierItem-" + pid + "-1";
                connection.requestBusName(busName);

                // Create and export the StatusNotifierItem object
                sniExport = new StatusNotifierItemExport(
                    connection,
                    v -> fireLeftClick(),
                    v -> fireRightClick()
                );
                connection.exportObject(StatusNotifierItemExport.OBJECT_PATH, sniExport);
                connection.exportObject(StatusNotifierItemExport.MENU_PATH,
                    sniExport.getMenuExport());

                // Apply any properties set before install()
                if (getIcon() != null) { sniExport.setIcon(getIcon()); }
                if (getText() != null) { sniExport.setTitle(getText());
                                         sniExport.setToolTip(getText()); }
                if (getMenu() != null) { sniExport.setMenu(getMenu()); }

                // Register with the StatusNotifierWatcher so the tray discovers us
                registerWithWatcher(busName);

                // Notify the base class that native init is complete —
                // any queued updates will now be drained in order
                onNativeReady();

            } catch (final Exception e) {
                throw new RuntimeException("Failed to install Linux tray icon via D-Bus", e);
            }
        });
    }

    @Override
    protected void nativeUninstall() {
        offThread(() -> {
            if (connection != null) {
                try { connection.disconnect(); } catch (final Exception ignored) {}
                connection = null;
                sniExport  = null;
            }
        });
    }

    @Override
    protected void nativeUpdateIcon(final Image icon) {
        offThread(() -> {
            final StatusNotifierItemExport s = sniExport;
            if (s != null) { s.setIcon(icon); }
        });
    }

    @Override
    protected void nativeUpdateText(final String text, final Color color) {
        offThread(() -> {
            final StatusNotifierItemExport s = sniExport;
            if (s != null) {
                s.setTitle(text != null ? text : "");
                s.setToolTip(text != null ? text : "");
            }
        });
    }

    @Override
    protected void nativeUpdateMenu(final TrayMenu menu) {
        offThread(() -> {
            final StatusNotifierItemExport s = sniExport;
            if (s != null) { s.setMenu(menu); }
        });
    }


    // ── Registration ─────────────────────────────────────────────────────

    /**
     * Calls {@code RegisterStatusNotifierItem} on the StatusNotifierWatcher.
     * If the watcher is not available (no tray host running), we silently
     * continue — the icon will appear when a tray host connects later.
     */
    private void registerWithWatcher(final String busName) {
        try {
            final StatusNotifierWatcherInterface watcher =
                connection.getRemoteObject(SNI_WATCHER_BUS, SNI_WATCHER_PATH,
                    StatusNotifierWatcherInterface.class);
            watcher.RegisterStatusNotifierItem(busName);
        } catch (final Exception e) {
            // Watcher may not be available — not fatal, icon will register
            // when a tray host starts or when the watcher becomes available
            System.err.println("[TrayFX] StatusNotifierWatcher not available: " + e.getMessage());
        }
    }

    private static String getPid() {
        try {
            return String.valueOf(ProcessHandle.current().pid());
        } catch (final Exception e) {
            return String.valueOf(ManagementFactory.getRuntimeMXBean()
                .getName().split("@")[0]);
        }
    }

    private static void offThread(final Runnable task) {
        Thread.ofVirtual().name("trayfx-dbus").start(task);
    }
}
