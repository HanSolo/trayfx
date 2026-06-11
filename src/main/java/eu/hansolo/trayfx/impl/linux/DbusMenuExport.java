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
 * D-Bus export for the {@code com.canonical.dbusmenu} interface.
 *
 * <p>The menu is represented as a tree of items. Each item has an integer ID.
 * The root item always has ID 0. When the menu changes, we increment the
 * revision counter and emit {@link DbusMenuInterface.LayoutUpdated} so the
 * tray host re-fetches the layout.
 *
 * <h2>Item format</h2>
 * Each menu item in the layout is a struct {@code (id, props, children)} where:
 * <ul>
 *   <li>{@code id} — integer ID</li>
 *   <li>{@code props} — map of string→variant properties</li>
 *   <li>{@code children} — list of child items (recursive)</li>
 * </ul>
 */
public final class DbusMenuExport implements DbusMenuInterface {

    private final DBusConnection  connection;
    private final AtomicInteger   revision  = new AtomicInteger(1);
    private final AtomicInteger   nextId    = new AtomicInteger(1);

    private volatile TrayMenu     menu;
    private volatile List<int[]>  itemIds   = Collections.emptyList(); // [id, menuIndex]


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
    public Map<String, Object> GetLayout(final int    parentId,
                                         final int    recursionDepth,
                                         final List<String> propertyNames) {
        final Map<String, Object> result = new HashMap<>();
        result.put("revision", new UInt32(revision.get()));
        result.put("layout",   buildLayout());
        return result;
    }

    private Object[] buildLayout() {
        final TrayMenu m = menu;

        // Root item — invisible, just a container
        final Map<String, Variant<?>> rootProps = new HashMap<>();
        rootProps.put("children-display", new Variant<>("submenu"));

        final List<Object[]> children = new ArrayList<>();
        final List<int[]>    ids      = new ArrayList<>();

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
                children.add(new Object[]{ id, props, Collections.emptyList() });
                ids.add(new int[]{ id, m.getItems().indexOf(item) });
                id++;
            }
        }

        itemIds = ids;
        return new Object[]{ 0, rootProps, children };
    }


    // ── GetGroupProperties ────────────────────────────────────────────────

    @Override
    public Map<String, Variant<?>> GetGroupProperties(final List<Integer>    ids,
                                                       final List<String>     propertyNames) {
        return Collections.emptyMap();
    }


    // ── Event (item clicked) ──────────────────────────────────────────────

    @Override
    public void Event(final int       id,
                      final String    eventId,
                      final Variant<?> data,
                      final UInt32    timestamp) {
        if (!"clicked".equals(eventId)) { return; }
        final TrayMenu m = menu;
        if (m == null) { return; }

        // Find the menu item for this id
        for (final int[] entry : itemIds) {
            if (entry[0] == id) {
                final int idx = entry[1];
                if (idx >= 0 && idx < m.getItems().size()) {
                    final MenuItem item = m.getItems().get(idx);
                    if (!item.isSeparator() && item.isEnabled()) {
                        Platform.runLater(item::fire);
                    }
                }
                break;
            }
        }
    }

    @Override
    public List<Integer> EventGroup(final List<Object[]> events) {
        return Collections.emptyList();
    }

    @Override
    public boolean AboutToShow(final int id) { return false; }

    @Override
    public Map<String, List<Integer>> AboutToShowGroup(final List<Integer> ids) {
        return Collections.emptyMap();
    }


    // ── Signal ────────────────────────────────────────────────────────────

    private void emitLayoutUpdated() {
        try {
            connection.sendMessage(new DbusMenuInterface.LayoutUpdated(
                StatusNotifierItemExport.MENU_PATH,
                new UInt32(revision.get()), 0));
        } catch (final Exception e) {
            // non-fatal
        }
    }
}
