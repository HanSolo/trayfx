package eu.hansolo.trayfx.impl.linux;

import org.freedesktop.dbus.Tuple;
import org.freedesktop.dbus.annotations.Position;
import org.freedesktop.dbus.types.UInt32;


/**
 * Return type for GetLayout. D-Bus type: (u(ia{sv}av))
 *
 * Uses Tuple with unbounded generic type parameters, this is the pattern
 * that works in dbus-java 5.x. Bounded generics (<A extends UInt32>) cause
 * ClassCastException during introspection. Unbounded generics with concrete
 * field declarations allow dbus-java to reflect on the actual field types.
 */
public final class GetLayoutResult<A, B> extends Tuple {

    @Position(0)
    public final A first;

    @Position(1)
    public final B second;

    public GetLayoutResult(final A first, final B second) {
        this.first  = first;
        this.second = second;
    }
}
