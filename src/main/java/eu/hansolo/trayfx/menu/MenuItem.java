package eu.hansolo.trayfx.menu;

import javafx.scene.image.Image;

import java.util.Objects;
import java.util.function.Consumer;


/**
 * Represents a single item in a TrayFX dropdown menu.
 *
 * <p>Three kinds of items:
 * <ul>
 *   <li>Regular item — {@link #of(String, Runnable)} or {@link #of(String, Image, Runnable)}</li>
 *   <li>Check item  — {@link #checkItem(String, boolean, Consumer)}: shows a checkmark, toggles on click</li>
 *   <li>Separator   — {@link #separator()}: a visual divider</li>
 * </ul>
 *
 * <p>Enabled state can be changed at runtime via {@link #setEnabled(boolean)}.
 * The owning {@link TrayMenu} must be re-applied to the tray icon for the
 * change to take effect: {@code tray.setMenu(tray.getMenu())}.
 */
public final class MenuItem {
    public enum Type { REGULAR, CHECK, SEPARATOR }

    private final String   label;
    private final Image    icon;
    private final Runnable action;
    private final Consumer<Boolean> onChecked;   // null for non-check items
    private final Type              type;
    private       boolean  enabled;
    private       boolean           checked;     // only meaningful for CHECK items


    private MenuItem(final String            label,
                     final Image             icon,
                     final Runnable          action,
                     final Consumer<Boolean> onChecked,
                     final Type              type,
                     final boolean           checked) {
        this.label     = label;
        this.icon      = icon;
        this.action    = action;
        this.onChecked = onChecked;
        this.type      = type;
        this.enabled   = true;
        this.checked   = checked;
    }


    public static MenuItem of(final String label, final Runnable action) {
        Objects.requireNonNull(label,  "label must not be null");
        Objects.requireNonNull(action, "action must not be null");
        return new MenuItem(label, null, action, null, Type.REGULAR, false);
    }

    public static MenuItem of(final String label, final Image icon, final Runnable action) {
        Objects.requireNonNull(label,  "label must not be null");
        Objects.requireNonNull(action, "action must not be null");
        return new MenuItem(label, icon, action, null, Type.REGULAR, false);
    }

    /**
     * Creates a check menu item that toggles between checked and unchecked.
     *
     * @param label     the display label
     * @param checked   the initial checked state
     * @param onChecked called with the new checked state whenever the item is clicked
     */
    public static MenuItem checkItem(final String label, final boolean checked, final Consumer<Boolean> onChecked) {
        Objects.requireNonNull(label,     "label must not be null");
        Objects.requireNonNull(onChecked, "onChecked must not be null");
        return new MenuItem(label, null, null, onChecked, Type.CHECK, checked);
    }

    public static MenuItem separator() {
        return new MenuItem(null, null, null, null, Type.SEPARATOR, false);
    }


    public String getLabel() { return label; }
    public Image getIcon() { return icon; }
    public Type    getType()      { return type;      }
    public boolean isSeparator()  { return type == Type.SEPARATOR; }
    public boolean isCheckItem()  { return type == Type.CHECK;     }
    public boolean isEnabled()    { return enabled; }
    public boolean isChecked()    { return checked; }

    // Runtime enable/disable — call tray.setMenu(tray.getMenu()) after changing
    public void setEnabled(final boolean enabled) { this.enabled = enabled; }

    // Fluent enable/disable for use in builder chains
    public MenuItem enabled(final boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    public void fire() {
        if (type == Type.SEPARATOR || !enabled) { return; }
        if (type == Type.CHECK) {
            checked = !checked;
            if (onChecked != null) { onChecked.accept(checked); }
        } else if (action != null) {
            action.run();
        }
    }

    @Override public String toString() {
        return switch (type) {
            case SEPARATOR -> "MenuItem[SEPARATOR]";
            case CHECK     -> "MenuItem[CHECK label=" + label + ", checked=" + checked + ", enabled=" + enabled + "]";
            default        -> "MenuItem[label=" + label + ", enabled=" + enabled + "]";
        };
    }
}
