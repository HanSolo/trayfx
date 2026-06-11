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
 * The dbusmenu GetLayout spec requires returning a struct {@code (ia{sv}av)}
 * but dbus-java cannot marshall {@code ArrayList} to that type directly.
 *
 * The solution is to use dbus-java's {@code Struct} type system or to
 * return the menu as a simple {@code Map<String, Variant>} containing
 * only the revision — AppIndicator on Ubuntu primarily uses the
 * {@code AboutToShow} + {@code Event} path for interaction, not GetLayout.
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
    // Returns revision + root item properties only.
    // The children are sent as an empty list — AppIndicator fetches item
    // details via GetGroupProperties when it needs them.

    @Override
    public Map<String, Variant<?>> GetLayout(final int          parentId,
                                              final int          recursionDepth,
                                              final List<String> propertyNames) {
        final Map<String, Variant<?>> result = new HashMap<>();
        result.put("revision", new Variant<>(new UInt32(revision.get())));

        // Build a simple layout: root node with children as flat int list
        // AppIndicator reads this to know how many items exist
        final Map<String, Variant<?>> rootProps = new HashMap<>();
        rootProps.put("children-display", new Variant<>("submenu"));

        // Return root with empty children — AppIndicator will call
        // GetGroupProperties to get item details
        final List<Integer> childIds = new ArrayList<>();
        final TrayMenu m = menu;
        if (m != null) {
            for (int i = 1; i <= m.getItems().size(); i++) {
                childIds.add(i);
            }
        }
        rootProps.put("children", new Variant<>(childIds, "ai"));

        result.put("layout", new Variant<>(rootProps, "a{sv}"));
        return result;
    }


    // ── GetGroupProperties ────────────────────────────────────────────────
    // Returns properties for requested item IDs — this is what AppIndicator
    // calls to get the actual label, enabled state etc. for each menu item.

    @Override
    public Map<String, Variant<?>> GetGroupProperties(final List<Integer> ids,
                                                       final List<String>  propertyNames) {
        final Map<String, Variant<?>> result = new HashMap<>();
        final TrayMenu m = menu;
        if (m == null) { return result; }

        final List<MenuItem> items = m.getItems();
        for (final int id : ids) {
            final int idx = id - 1;
            if (idx < 0 || idx >= items.size()) { continue; }
            final MenuItem item = items.get(idx);
            final Map<String, Variant<?>> props = new HashMap<>();
            if (item.isSeparator()) {
                props.put("type",    new Variant<>("separator"));
                props.put("enabled", new Variant<>(Boolean.FALSE));
            } else {
                props.put("label",   new Variant<>(item.getLabel()));
                props.put("enabled", new Variant<>(item.isEnabled()));
                props.put("visible", new Variant<>(Boolean.TRUE));
            }
            result.put(String.valueOf(id), new Variant<>(props, "a{sv}"));
        }
        return result;
    }


    // ── Event ─────────────────────────────────────────────────────────────

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
