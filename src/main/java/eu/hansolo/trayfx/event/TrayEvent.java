package eu.hansolo.trayfx.event;


/**
 * Fired when the user interacts with the tray / menu-bar icon.
 */
public final class TrayEvent {
    private final TrayEventType type;


    public TrayEvent(final TrayEventType type) {
        this.type = type;
    }


    public TrayEventType getType() { return type; }

    @Override public String toString() {
        return "TrayEvent[type=" + type + "]";
    }
}
