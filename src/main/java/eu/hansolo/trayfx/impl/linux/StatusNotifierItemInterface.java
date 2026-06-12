package eu.hansolo.trayfx.impl.linux;

import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.freedesktop.dbus.interfaces.DBusInterface;
import org.freedesktop.dbus.messages.DBusSignal;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.types.UInt32;
import org.freedesktop.dbus.types.Variant;

import java.util.List;
import java.util.Map;


/**
 * D-Bus interface definition for {@code org.kde.StatusNotifierItem}.
 *
 * <p>This is the interface that GNOME Shell's AppIndicator extension and KDE
 * Plasma's system tray use to discover and display tray icons. The interface
 * name {@code org.kde.StatusNotifierItem} is used in practice despite the
 * freedesktop.org spec suggesting {@code org.freedesktop.StatusNotifierItem}, all major DEs use the KDE name for compatibility reasons.
 */
@DBusInterfaceName("org.kde.StatusNotifierItem")
public interface StatusNotifierItemInterface extends DBusInterface {

    /** Called by the tray host when the user left clicks the icon. */
    void Activate(int x, int y);

    /** Called by the tray host when the user right clicks the icon. */
    void ContextMenu(int x, int y);

    /** Called by the tray host when the user middle clicks the icon. */
    void SecondaryActivate(int x, int y);

    /** Ayatana extension, secondary activation with timestamp. */
    void XAyatanaSecondaryActivate(UInt32 timestamp);

    /** Called by the tray host when the user scrolls over the icon. */
    void Scroll(int delta, String orientation);


    class NewIcon extends DBusSignal {
        public NewIcon(final String path) throws DBusException {
            super(path);
        }
    }

    class NewTitle extends DBusSignal {
        public NewTitle(final String path) throws DBusException {
            super(path);
        }
    }

    class NewToolTip extends DBusSignal {
        public NewToolTip(final String path) throws DBusException {
            super(path);
        }
    }

    class NewStatus extends DBusSignal {
        public final String status;


        public NewStatus(final String path, final String status) throws DBusException {
            super(path, status);
            this.status = status;
        }
    }

    class XAyatanaNewLabel extends DBusSignal {
        public final String label;
        public final String guide;


        public XAyatanaNewLabel(final String path, final String label, final String guide) throws DBusException {
            super(path, label, guide);
            this.label = label;
            this.guide = guide;
        }
    }
}
