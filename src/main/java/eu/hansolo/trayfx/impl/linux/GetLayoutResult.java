package eu.hansolo.trayfx.impl.linux;

import org.freedesktop.dbus.Struct;
import org.freedesktop.dbus.annotations.Position;
import org.freedesktop.dbus.types.UInt32;


/**
 * Return type for {@code com.canonical.dbusmenu.GetLayout}.
 * D-Bus type signature: {@code (u(ia{sv}av))}
 */
public final class GetLayoutResult extends Struct {

    @Position(0)
    public final UInt32          revision;

    @Position(1)
    public final MenuLayoutItem  layout;

    public GetLayoutResult(final UInt32         revision,
                           final MenuLayoutItem layout) {
        this.revision = revision;
        this.layout   = layout;
    }
}
