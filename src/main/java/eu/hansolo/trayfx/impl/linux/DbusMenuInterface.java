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
 * D-Bus interface definition for {@code com.canonical.dbusmenu}.
 *
 * <p>DBusMenu is the protocol used to pass menus from the application to the
 * tray host over D-Bus, so the host can render them natively. The menu is
 * represented as a tree of items with integer IDs.
 */
@DBusInterfaceName("com.canonical.dbusmenu")
public interface DbusMenuInterface extends DBusInterface {

    /**
     * Returns the layout of the menu starting from the given parent item.
     *
     * @param parentId  the ID of the parent item (0 = root)
     * @param recursionDepth  how many levels deep to return (-1 = all)
     * @param propertyNames  which properties to return (empty = all)
     * @return a tuple of (revision, layout-item)
     */
    Map<String, Object> GetLayout(int parentId, int recursionDepth, List<String> propertyNames);

    /**
     * Returns the properties of a single menu item.
     */
    Map<String, Variant<?>> GetGroupProperties(List<Integer> ids, List<String> propertyNames);

    /**
     * Notify the host that a menu item was activated by the application
     * (not by user interaction through the host).
     */
    void Event(int id, String eventId, Variant<?> data, UInt32 timestamp);

    /**
     * Notify the host that multiple events occurred.
     */
    List<Integer> EventGroup(List<Object[]> events);

    /**
     * Ask the item to perform the action associated with the menu item.
     */
    boolean AboutToShow(int id);

    /**
     * Notify about multiple items being about to show.
     */
    Map<String, List<Integer>> AboutToShowGroup(List<Integer> ids);


    // ── Signals ───────────────────────────────────────────────────────────

    /** Fired when the layout changes — host should re-fetch with GetLayout. */
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

    /** Fired when one or more item properties change. */
    class ItemsPropertiesUpdated extends DBusSignal {
        public ItemsPropertiesUpdated(final String path,
                                      final List<Object[]> updatedProps,
                                      final List<Object[]> removedProps) throws DBusException {
            super(path, updatedProps, removedProps);
        }
    }
}
