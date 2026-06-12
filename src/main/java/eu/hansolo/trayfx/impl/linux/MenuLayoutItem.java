package eu.hansolo.trayfx.impl.linux;

import org.freedesktop.dbus.Struct;
import org.freedesktop.dbus.annotations.Position;
import org.freedesktop.dbus.types.Variant;

import java.util.List;
import java.util.Map;


/**
 * A single item in the dbusmenu layout tree.
 * D-Bus type signature: {@code (ia{sv}av)}
 *
 * Fields must be private in dbus-java 5.x — public fields are not
 * found by the reflection-based serialisation in some versions.
 * The constructor must take all fields in @Position order.
 */
public final class MenuLayoutItem extends Struct {

    @Position(0)
    private final Integer id;

    @Position(1)
    private final Map<String, Variant<?>> properties;

    @Position(2)
    private final List<Variant<?>> children;


    public MenuLayoutItem(final Integer id, final Map<String, Variant<?>> properties, final List<Variant<?>> children) {
        this.id         = id;
        this.properties = properties;
        this.children   = children;
    }


    public Integer getId() { return id; }

    public Map<String, Variant<?>> getProperties() { return properties; }

    public List<Variant<?>> getChildren() { return children; }
}
