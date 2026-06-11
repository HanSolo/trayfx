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
 * Implementation of {@code com.canonical.dbusmenu} exported over D-Bus.
 *
 * The menu layout is represented as a tree rooted at ID 0. When the menu
 * changes the revision counter increments and {@link DbusMenuInterface.LayoutUpdated}
 * is emitted so the tray host re-fetches the layout.
 */
public final class DbusMenuExport implements DbusMenuInterface {

    private final DBusConnection  connection;
    private final AtomicInteger   revision = new AtomicInteger(1);

    private volatile TrayMenu     menu;
    // Maps D-Bus item ID → index in menu.getItems()
    private volatile int[]        idToIndex = new int[0];


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
        result.put("layout",   new Variant<>(buildLayoutVariant(), "v"));
        return result;
    }

    /**
     * Builds the menu layout as a Variant-wrapped struct to avoid
     * dbus-java's Object introspection restriction.
     * Format: list of [id, props-map, children-list] per item.
     */
    private Variant<?> buildLayoutVariant() {
        final TrayMenu m = menu;
        final List<Map<String, Variant<?>>> items = new ArrayList<>();

        // Root container item
        final Map<String, Variant<?>> rootProps = new HashMap<>();
        rootProps.put("children-display", new Variant<>("submenu"));
        items.add(rootProps);

        if (m != null) {
            final List<MenuItem> menuItems = m.getItems();
            final int[]          ids       = new int[menuItems.size()];
            for (int i = 0; i < menuItems.size(); i++) {
                final MenuItem item = menuItems.get(i);
                final Map<String, Variant<?>> props = new HashMap<>();
                if (item.isSeparator()) {
                    props.put("type",    new Variant<>("separator"));
                    props.put("enabled", new Variant<>(Boolean.FALSE));
                } else {
                    props.put("label",   new Variant<>(item.getLabel()));
                    props.put("enabled", new Variant<>(item.isEnabled()));
                    props.put("visible", new Variant<>(Boolean.TRUE));
                }
                items.add(props);
                ids[i] = i + 1; // item IDs start at 1
            }
            idToIndex = ids;
        }

        return new Variant<>(items, "aa{sv}");
    }


    // ── GetGroupProperties ────────────────────────────────────────────────

    @Override
    public Map<String, Variant<?>> GetGroupProperties(final List<Integer> ids,
                                                       final List<String>  propertyNames) {
        return Collections.emptyMap();
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

        final int idx = id - 1; // IDs start at 1, indices at 0
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
