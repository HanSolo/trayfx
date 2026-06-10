package eu.hansolo.trayfx.menu;

import javafx.scene.image.Image;

import java.util.Objects;


/**
 * Represents a single item in a TrayFX dropdown menu.
 * Use {@link MenuItem#separator()} to create a visual divider between items.
 */
public final class MenuItem {

    private final String   label;
    private final Image    icon;
    private final Runnable action;
    private final boolean  separator;
    private       boolean  enabled;


    private MenuItem(final String label, final Image icon, final Runnable action, final boolean separator) {
        this.label     = label;
        this.icon      = icon;
        this.action    = action;
        this.separator = separator;
        this.enabled   = true;
    }


    // ── Factory methods ──────────────────────────────────────────────────────

    public static MenuItem of(final String label, final Runnable action) {
        Objects.requireNonNull(label,  "label must not be null");
        Objects.requireNonNull(action, "action must not be null");
        return new MenuItem(label, null, action, false);
    }

    public static MenuItem of(final String label, final Image icon, final Runnable action) {
        Objects.requireNonNull(label,  "label must not be null");
        Objects.requireNonNull(action, "action must not be null");
        return new MenuItem(label, icon, action, false);
    }

    public static MenuItem separator() {
        return new MenuItem(null, null, null, true);
    }


    // ── Accessors ────────────────────────────────────────────────────────────

    public String   getLabel()     { return label; }
    public Image    getIcon()      { return icon; }
    public Runnable getAction()    { return action; }
    public boolean  isSeparator()  { return separator; }
    public boolean  isEnabled()    { return enabled; }

    public MenuItem enabled(final boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    public void fire() {
        if (!separator && enabled && action != null) {
            action.run();
        }
    }


    @Override public String toString() {
        if (separator) { return "MenuItem[SEPARATOR]"; }
        return "MenuItem[label=" + label + ", enabled=" + enabled + "]";
    }
}
