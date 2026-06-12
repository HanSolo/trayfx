package eu.hansolo.trayfx.impl.linux;

import org.freedesktop.dbus.Struct;
import org.freedesktop.dbus.annotations.Position;
import org.freedesktop.dbus.types.Variant;

import java.util.Map;


/**
 * Single item in the GetGroupProperties result array.
 * D-Bus type: {@code (ia{sv})} -> item ID + properties map.
 */
public final class GetGroupPropertiesResult extends Struct {

    @Position(0)
    private final Integer id;

    @Position(1)
    private final Map<String, Variant<?>> properties;

    public GetGroupPropertiesResult(final Integer id, final Map<String, Variant<?>> properties) {
        this.id         = id;
        this.properties = properties;
    }

    public Integer getId() { return id; }

    public Map<String, Variant<?>> getProperties() { return properties; }
}
