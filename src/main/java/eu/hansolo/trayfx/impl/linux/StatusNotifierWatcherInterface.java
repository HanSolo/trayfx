package eu.hansolo.trayfx.impl.linux;

import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.freedesktop.dbus.interfaces.DBusInterface;


/**
 * Proxy interface for the {@code org.kde.StatusNotifierWatcher} service.
 *
 * <p>The watcher is a service provided by the desktop environment (e.g.
 * GNOME Shell with AppIndicator extension, KDE Plasma) that acts as a
 * registry for StatusNotifierItem instances. Applications register with
 * it and the tray host discovers items through it.
 */
@DBusInterfaceName("org.kde.StatusNotifierWatcher")
public interface StatusNotifierWatcherInterface extends DBusInterface {

    /**
     * Registers a StatusNotifierItem with the watcher.
     *
     * @param service the D-Bus bus name of the item, e.g.
     *                {@code org.kde.StatusNotifierItem-12345-1}
     */
    void RegisterStatusNotifierItem(String service);
}
