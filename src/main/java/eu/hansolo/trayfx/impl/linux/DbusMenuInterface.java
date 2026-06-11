package eu.hansolo.trayfx.impl.linux;

import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.freedesktop.dbus.interfaces.DBusInterface;
import org.freedesktop.dbus.messages.DBusSignal;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.types.UInt32;
import org.freedesktop.dbus.types.Variant;

import java.util.List;
import java.util.Map;


/**
 * D-Bus interface for {@code com.canonical.dbusmenu}.
 *
 * Only the methods actually used by GNOME/KDE tray hosts are declared here.
 * All method signatures use concrete D-Bus-compatible types — no raw
 * {@code Object} or {@code Object[]} which dbus-java cannot introspect.
 */
@DBusInterfaceName("com.canonical.dbusmenu")
public interface DbusMenuInterface extends DBusInterface {

    /**
     * Returns the layout starting from parentId.
     * Result is a struct: (revision, layout-item) but we return as Variant
     * to avoid dbus-java introspection issues with nested structs.
     */
    Map<String, Variant<?>> GetLayout(int parentId, int recursionDepth,
                                      List<String> propertyNames);

    /** Returns properties for a list of item IDs. */
    Map<String, Variant<?>> GetGroupProperties(List<Integer> ids,
                                               List<String>  propertyNames);

    /** Notifies that a menu item event occurred (e.g. clicked). */
    void Event(int id, String eventId, Variant<?> data, UInt32 timestamp);

    /** Returns whether item is about to be shown. */
    boolean AboutToShow(int id);


    // ── Signals ───────────────────────────────────────────────────────────

    class LayoutUpdated extends DBusSignal {
        public final UInt32 revision;
        public final int    parent;

        public LayoutUpdated(final String path,
                             final UInt32 revision,
                             final int    parent) throws DBusException {
            super(path, revision, parent);
            this.revision = revision;
            this.parent   = parent;
        }
    }
}
