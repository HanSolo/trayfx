package eu.hansolo.trayfx.impl.linux;

import org.freedesktop.dbus.Struct;
import org.freedesktop.dbus.annotations.Position;
import org.freedesktop.dbus.types.Variant;

import java.util.List;
import java.util.Map;


/**
 * Represents a single item in the dbusmenu layout tree.
 * D-Bus type signature: {@code (ia{sv}av)}
 *
 * <p>dbus-java requires a {@link Struct} subclass with {@link Position}
 * annotations to marshall this type correctly.
 */
public final class MenuLayoutItem extends Struct {

    /** The item's unique integer ID. Root item is always 0. */
    @Position(0)
    public final int id;

    /** Item properties — label, type, enabled, visible etc. */
    @Position(1)
    public final Map<String, Variant<?>> properties;

    /**
     * Child items, each wrapped in a {@link Variant} with type {@code (ia{sv}av)}.
     * Leaf items have an empty list.
     */
    @Position(2)
    public final List<Variant<?>> children;

    public MenuLayoutItem(final int                     id,
                          final Map<String, Variant<?>> properties,
                          final List<Variant<?>>        children) {
        this.id         = id;
        this.properties = properties;
        this.children   = children;
    }
}
