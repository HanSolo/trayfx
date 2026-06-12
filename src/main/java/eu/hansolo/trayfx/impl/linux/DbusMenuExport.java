package eu.hansolo.trayfx.impl.linux;

import eu.hansolo.trayfx.menu.MenuItem;
import eu.hansolo.trayfx.menu.TrayMenu;
import javafx.application.Platform;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.interfaces.Introspectable;
import org.freedesktop.dbus.types.UInt32;
import org.freedesktop.dbus.types.Variant;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * Implementation of {@code com.canonical.dbusmenu} exported over D-Bus.
 *
 * <h2>GetLayout return type</h2>
 * {@code GetLayout} must return D-Bus type {@code (u(ia{sv}av))}. This is
 * achieved using {@link GetLayoutResult} which extends {@link org.freedesktop.dbus.Tuple}
 * with unbounded generic parameters — the only combination that works in
 * dbus-java 5.x without double-wrapping or ClassCastException.
 *
 * <h2>Struct field visibility</h2>
 * {@link MenuLayoutItem} and {@link GetGroupPropertiesResult} use {@code private}
 * fields with {@code @Position} annotations — dbus-java 5.x requires this for
 * correct field ordering during serialisation.
 */
public final class DbusMenuExport implements DbusMenuInterface, Introspectable {

    private static final String INTROSPECTION_XML =
        "<!DOCTYPE node PUBLIC \"-//freedesktop//DTD D-BUS Object Introspection 1.0//EN\"\n" +
        "\"http://www.freedesktop.org/standards/dbus/1.0/introspect.dtd\">\n" +
        "<node>\n" +
        "  <interface name=\"com.canonical.dbusmenu\">\n" +
        "    <method name=\"GetLayout\">\n" +
        "      <arg type=\"i\" name=\"parentId\" direction=\"in\"/>\n" +
        "      <arg type=\"i\" name=\"recursionDepth\" direction=\"in\"/>\n" +
        "      <arg type=\"as\" name=\"propertyNames\" direction=\"in\"/>\n" +
        "      <arg type=\"u\" name=\"revision\" direction=\"out\"/>\n" +
        "      <arg type=\"(ia{sv}av)\" name=\"layout\" direction=\"out\"/>\n" +
        "    </method>\n" +
        "    <method name=\"GetGroupProperties\">\n" +
        "      <arg type=\"ai\" name=\"ids\" direction=\"in\"/>\n" +
        "      <arg type=\"as\" name=\"propertyNames\" direction=\"in\"/>\n" +
        "      <arg type=\"a{sv}\" name=\"properties\" direction=\"out\"/>\n" +
        "    </method>\n" +
        "    <method name=\"Event\">\n" +
        "      <arg type=\"i\" name=\"id\" direction=\"in\"/>\n" +
        "      <arg type=\"s\" name=\"eventId\" direction=\"in\"/>\n" +
        "      <arg type=\"v\" name=\"data\" direction=\"in\"/>\n" +
        "      <arg type=\"u\" name=\"timestamp\" direction=\"in\"/>\n" +
        "    </method>\n" +
        "    <method name=\"AboutToShow\">\n" +
        "      <arg type=\"i\" name=\"id\" direction=\"in\"/>\n" +
        "      <arg type=\"b\" name=\"needsUpdate\" direction=\"out\"/>\n" +
        "    </method>\n" +
        "    <signal name=\"LayoutUpdated\">\n" +
        "      <arg type=\"u\" name=\"revision\"/>\n" +
        "      <arg type=\"i\" name=\"parent\"/>\n" +
        "    </signal>\n" +
        "  </interface>\n" +
        "</node>";

    private final DBusConnection  connection;
    private final AtomicInteger   revision = new AtomicInteger(1);
    private volatile TrayMenu     menu;


    DbusMenuExport(final DBusConnection connection) {
        this.connection = connection;
    }


    void setMenu(final TrayMenu menu) {
        this.menu = menu;
        revision.incrementAndGet();
        emitLayoutUpdated();
    }

    @Override public boolean isRemote() { return false; }

    @Override public String  getObjectPath() { return StatusNotifierItemExport.MENU_PATH; }

    @Override public String Introspect() { return INTROSPECTION_XML; }

    @Override public GetLayoutResult<UInt32, MenuLayoutItem> GetLayout(final int parentId, final int recursionDepth, final List<String> propertyNames) {
        final TrayMenu         trayMenu = menu;
        final List<Variant<?>> children = new ArrayList<>();

        if (trayMenu != null) {
            int id = 1;
            for (final MenuItem item : trayMenu.getItems()) {
                final Map<String, Variant<?>> props = new HashMap<>();
                if (item.isSeparator()) {
                    props.put("type",    new Variant<>("separator"));
                    props.put("enabled", new Variant<>(Boolean.FALSE));
                } else {
                    props.put("label",   new Variant<>(item.getLabel()));
                    props.put("enabled", new Variant<>(item.isEnabled()));
                    props.put("visible", new Variant<>(Boolean.TRUE));
                }
                final MenuLayoutItem child = new MenuLayoutItem(id, props, Collections.emptyList());
                children.add(new Variant<>(child));
                id++;
            }
        }

        final Map<String, Variant<?>> rootProps = new HashMap<>();
        rootProps.put("children-display", new Variant<>("submenu"));
        final MenuLayoutItem          root      = new MenuLayoutItem(0, rootProps, children);

        return new GetLayoutResult<>(new UInt32(revision.get()), root);
    }

    @Override public List<GetGroupPropertiesResult> GetGroupProperties(final List<Integer> ids, final List<String>  propertyNames) {
        final List<GetGroupPropertiesResult> result   = new ArrayList<>();
        final TrayMenu                       trayMenu = menu;
        if (trayMenu == null) { return result; }

        final List<MenuItem> items = trayMenu.getItems();
        ids.forEach(id -> {
            final int idx = id - 1;
            if (idx < 0 || idx >= items.size()) { return; }
            final MenuItem                item  = items.get(idx);
            final Map<String, Variant<?>> props = new HashMap<>();
            if (item.isSeparator()) {
                props.put("type", new Variant<>("separator"));
                props.put("enabled", new Variant<>(Boolean.FALSE));
            } else {
                props.put("label", new Variant<>(item.getLabel()));
                props.put("enabled", new Variant<>(item.isEnabled()));
                props.put("visible", new Variant<>(Boolean.TRUE));
            }
            result.add(new GetGroupPropertiesResult(id, props));
        });
        return result;
    }

    @Override public void Event(final int id, final String eventId, final Variant<?> data, final UInt32 timestamp) {
        if (!"clicked".equals(eventId)) { return; }
        final TrayMenu trayMenu = menu;
        if (trayMenu == null) { return; }
        final int            idx   = id - 1;
        final List<MenuItem> items = trayMenu.getItems();
        if (idx >= 0 && idx < items.size()) {
            final MenuItem item = items.get(idx);
            if (!item.isSeparator() && item.isEnabled()) {
                Platform.runLater(item::fire);
            }
        }
    }

    @Override public Boolean AboutToShow(final int id) { return Boolean.TRUE; }

    private void emitLayoutUpdated() {
        try {
            connection.sendMessage(new DbusMenuInterface.LayoutUpdated(StatusNotifierItemExport.MENU_PATH, new UInt32(revision.get()), 0));
        } catch (final Exception ignored) {}
    }
}
