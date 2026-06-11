package eu.hansolo.trayfx.impl.linux;

import eu.hansolo.trayfx.menu.MenuItem;
import eu.hansolo.trayfx.menu.TrayMenu;
import javafx.application.Platform;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.types.UInt32;
import org.freedesktop.dbus.types.Variant;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * Implementation of {@code com.canonical.dbusmenu}.
 *
 * The layout format expected by AppIndicator/GNOME Shell is:
 * {@code (revision, (id, props, children))} where each item is
 * {@code (int id, Map<String,Variant> props, List<Variant> children)}.
 *
 * We return this as a {@code Map<String, Variant>} with keys
 * "revision" and "layout" to avoid dbus-java struct serialisation issues.
 */
public final class DbusMenuExport implements DbusMenuInterface {

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

    @Override public boolean isRemote()      { return false; }
    @Override public String  getObjectPath() { return StatusNotifierItemExport.MENU_PATH; }


    // ── GetLayout ─────────────────────────────────────────────────────────

    @Override
    public Map<String, Variant<?>> GetLayout(final int          parentId,
                                              final int          recursionDepth,
                                              final List<String> propertyNames) {
        final Map<String, Variant<?>> result = new HashMap<>();
        result.put("revision", new Variant<>(new UInt32(revision.get())));

        // Root item: id=0, props with children-display=submenu, children=menu items
        final Map<String, Variant<?>> rootProps = new HashMap<>();
        rootProps.put("children-display", new Variant<>("submenu"));

        final List<Variant<?>> children = new ArrayList<>();
        final TrayMenu m = menu;
        if (m != null) {
            int id = 1;
            for (final MenuItem item : m.getItems()) {
                final Map<String, Variant<?>> props = new HashMap<>();
                if (item.isSeparator()) {
                    props.put("type",    new Variant<>("separator"));
                    props.put("enabled", new Variant<>(Boolean.FALSE));
                } else {
                    props.put("label",   new Variant<>(item.getLabel()));
                    props.put("enabled", new Variant<>(item.isEnabled()));
                    props.put("visible", new Variant<>(Boolean.TRUE));
                }
                // Each child is a struct (int, Map<String,Variant>, List<Variant>)
                // wrapped as a Variant so it can go in the children list
                final List<Object> itemStruct = new ArrayList<>();
                itemStruct.add(id);
                itemStruct.add(props);
                itemStruct.add(Collections.emptyList());
                children.add(new Variant<>(itemStruct, "(ia{sv}av)"));
                id++;
            }
        }

        // Root layout struct: (0, rootProps, children)
        final List<Object> rootStruct = new ArrayList<>();
        rootStruct.add(0);
        rootStruct.add(rootProps);
        rootStruct.add(children);
        result.put("layout", new Variant<>(rootStruct, "(ia{sv}av)"));

        return result;
    }


    // ── GetGroupProperties ────────────────────────────────────────────────

    @Override
    public Map<String, Variant<?>> GetGroupProperties(final List<Integer> ids,
                                                       final List<String>  propertyNames) {
        return Collections.emptyMap();
    }


    // ── Event (item clicked) ──────────────────────────────────────────────

    @Override
    public void Event(final int        id,
                      final String     eventId,
                      final Variant<?> data,
                      final UInt32     timestamp) {
        if (!"clicked".equals(eventId)) { return; }
        final TrayMenu m = menu;
        if (m == null) { return; }

        final int idx = id - 1;
        final List<MenuItem> items = m.getItems();
        if (idx >= 0 && idx < items.size()) {
            final MenuItem item = items.get(idx);
            if (!item.isSeparator() && item.isEnabled()) {
                Platform.runLater(item::fire);
            }
        }
    }

    @Override
    public boolean AboutToShow(final int id) { return false; }


    // ── Signal ────────────────────────────────────────────────────────────

    private void emitLayoutUpdated() {
        try {
            connection.sendMessage(new DbusMenuInterface.LayoutUpdated(
                StatusNotifierItemExport.MENU_PATH,
                new UInt32(revision.get()), 0));
        } catch (final Exception ignored) {}
    }
}
