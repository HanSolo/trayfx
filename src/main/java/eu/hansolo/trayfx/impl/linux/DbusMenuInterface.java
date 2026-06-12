package eu.hansolo.trayfx.impl.linux;

import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.freedesktop.dbus.interfaces.DBusInterface;
import org.freedesktop.dbus.messages.DBusSignal;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.types.UInt32;
import org.freedesktop.dbus.types.Variant;

import java.util.List;
import java.util.Map;


@DBusInterfaceName("com.canonical.dbusmenu")
public interface DbusMenuInterface extends DBusInterface {

    /**
     * Returns the menu layout as a struct (u(ia{sv}av)).
     * Returns GetLayoutResult which extends Struct with two @Position fields.
     */
    GetLayoutResult<UInt32, MenuLayoutItem> GetLayout(int parentId, int recursionDepth, List<String> propertyNames);

    List<GetGroupPropertiesResult> GetGroupProperties(List<Integer> ids, List<String>  propertyNames);

    void Event(int id, String eventId, Variant<?> data, UInt32 timestamp);

    Boolean AboutToShow(int id);


    class LayoutUpdated extends DBusSignal {
        public final UInt32 revision;
        public final int    parent;

        public LayoutUpdated(final String path, final UInt32 revision, final int    parent) throws DBusException {
            super(path, revision, parent);
            this.revision = revision;
            this.parent   = parent;
        }
    }
}
