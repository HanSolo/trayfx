package eu.hansolo.trayfx.menu;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


/**
 * An ordered list of {@link MenuItem}s shown when the user interacts
 * with the tray / menu-bar icon.
 *
 * <pre>{@code
 * TrayMenu menu = TrayMenu.of(
 *     MenuItem.of("Open",  () -> stage.show()),
 *     MenuItem.separator(),
 *     MenuItem.of("Quit",  () -> Platform.exit())
 * );
 * }</pre>
 */
public final class TrayMenu {
    private final List<MenuItem> items;


    private TrayMenu(final List<MenuItem> items) {
        this.items = new ArrayList<>(items);
    }


    public static TrayMenu of(final MenuItem... items) { return new TrayMenu(List.of(items)); }

    public static TrayMenu of(final List<MenuItem> items) { return new TrayMenu(items); }

    public static Builder builder() { return new Builder(); }



    public List<MenuItem> getItems() { return Collections.unmodifiableList(items); }

    public boolean isEmpty() { return items.isEmpty(); }



    public static final class Builder {
        private final List<MenuItem> items = new ArrayList<>();

        public Builder item(final MenuItem item) {
            items.add(item);
            return this;
        }

        public Builder separator() {
            items.add(MenuItem.separator());
            return this;
        }

        public TrayMenu build() { return new TrayMenu(items); }
    }
}
