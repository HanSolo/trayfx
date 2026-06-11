package eu.hansolo.trayfx.impl.linux;

import eu.hansolo.trayfx.menu.TrayMenu;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.interfaces.Properties;
import org.freedesktop.dbus.messages.DBusSignal;
import org.freedesktop.dbus.types.UInt32;
import org.freedesktop.dbus.types.Variant;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;


/**
 * The D-Bus object exported as {@code /StatusNotifierItem}.
 *
 * Implements {@code org.kde.StatusNotifierItem} and
 * {@code org.freedesktop.DBus.Properties} so the tray host can read
 * icon data, title, status etc.
 *
 * All method signatures use only concrete D-Bus-compatible types.
 * Generic return types like {@code Object} will cause dbus-java to throw
 * "Exporting non-exportable type" during introspection.
 */
public final class StatusNotifierItemExport
    implements StatusNotifierItemInterface, Properties {

    static final String OBJECT_PATH = "/org/ayatana/NotificationItem/trayfx";
    static final String MENU_PATH   = "/org/ayatana/NotificationItem/trayfx/Menu";
    static final String SNI_IFACE   = "org.kde.StatusNotifierItem";

    private final DBusConnection      connection;
    private final DbusMenuExport      menuExport;
    private final Consumer<Void>      onActivate;
    private final Consumer<Void>      onContextMenu;

    private volatile Image            icon;
    private volatile String           title   = "TrayFX";
    private volatile String           toolTip = "";
    private volatile List<Object[]>   iconPixmaps = Collections.emptyList();


    StatusNotifierItemExport(final DBusConnection  connection,
                             final Consumer<Void>  onActivate,
                             final Consumer<Void>  onContextMenu) {
        this.connection    = connection;
        this.onActivate    = onActivate;
        this.onContextMenu = onContextMenu;
        this.menuExport    = new DbusMenuExport(connection);
    }

    DbusMenuExport getMenuExport() { return menuExport; }


    // ── Icon / title / tooltip ────────────────────────────────────────────

    void setIcon(final Image fxImage) {
        this.icon        = fxImage;
        this.iconPixmaps = fxImage != null ? toArgb32Pixmaps(fxImage)
                                           : Collections.emptyList();
        try { emitSignal(new StatusNotifierItemInterface.NewIcon(OBJECT_PATH)); }
        catch (final Exception ignored) {}
    }

    void setTitle(final String title) {
        this.title = title != null ? title : "";
        try { emitSignal(new StatusNotifierItemInterface.NewTitle(OBJECT_PATH)); }
        catch (final Exception ignored) {}
    }

    void setToolTip(final String tip) {
        this.toolTip = tip != null ? tip : "";
        try { emitSignal(new StatusNotifierItemInterface.NewToolTip(OBJECT_PATH)); }
        catch (final Exception ignored) {}
    }

    void setMenu(final TrayMenu menu) {
        menuExport.setMenu(menu);
    }


    // ── StatusNotifierItem methods ────────────────────────────────────────

    @Override public boolean isRemote()      { return false; }
    @Override public String  getObjectPath() { return OBJECT_PATH; }

    @Override public void Activate(final int x, final int y) {
        if (onActivate    != null) { onActivate.accept(null); }
    }
    @Override public void ContextMenu(final int x, final int y) {
        if (onContextMenu != null) { onContextMenu.accept(null); }
    }
    @Override public void SecondaryActivate(final int x, final int y) {}
    @Override public void XAyatanaSecondaryActivate(final UInt32 timestamp) {}
    @Override public void Scroll(final int delta, final String orientation) {}


    // ── Properties interface ──────────────────────────────────────────────
    // Return types must be concrete — no raw generics or Object.

    @Override
    public <A> A Get(final String iface, final String name) {
        @SuppressWarnings("unchecked")
        A val = (A) getProperties().get(name);
        return val;
    }

    @Override
    public <A> void Set(final String iface, final String name, final A value) {}

    @Override
    public Map<String, Variant<?>> GetAll(final String iface) {
        if (!SNI_IFACE.equals(iface)) { return Collections.emptyMap(); }
        return getProperties();
    }

    private Map<String, Variant<?>> getProperties() {
        final Map<String, Variant<?>> props = new HashMap<>();
        props.put("Category",    new Variant<>("ApplicationStatus"));
        props.put("Id",          new Variant<>("trayfx"));
        props.put("Title",       new Variant<>(title));
        props.put("Status",      new Variant<>("Active"));
        props.put("WindowId",    new Variant<>(new UInt32(0)));
        props.put("IconName",    new Variant<>(""));
        props.put("IconPixmap",  new Variant<>(iconPixmaps, "a(iiay)"));
        props.put("OverlayIconName",     new Variant<>(""));
        props.put("OverlayIconPixmap",   new Variant<>(Collections.emptyList(), "a(iiay)"));
        props.put("AttentionIconName",   new Variant<>(""));
        props.put("AttentionIconPixmap", new Variant<>(Collections.emptyList(), "a(iiay)"));
        props.put("AttentionMovieName",  new Variant<>(""));
        props.put("ItemIsMenu",  new Variant<>(Boolean.TRUE));
        props.put("Menu",        new Variant<>(MENU_PATH, "o"));
        props.put("ToolTip",     new Variant<>(toolTip));
        // Ayatana/Ubuntu AppIndicator extensions
        props.put("XAyatanaLabel",          new Variant<>(""));
        props.put("XAyatanaLabelGuide",     new Variant<>(""));
        props.put("XAyatanaOrderingIndex",  new Variant<>(new UInt32(0)));
        props.put("IconThemePath",          new Variant<>(""));
        props.put("IconAccessibleDesc",     new Variant<>(""));
        props.put("AttentionIconName",      new Variant<>(""));
        props.put("AttentionAccessibleDesc", new Variant<>(""));
        return props;
    }


    // ── ARGB32 pixel conversion ───────────────────────────────────────────

    static List<Object[]> toArgb32Pixmaps(final Image fxImage) {
        final int w = (int) fxImage.getWidth();
        final int h = (int) fxImage.getHeight();

        final BufferedImage buf = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        final java.awt.Graphics2D g = buf.createGraphics();
        g.setComposite(java.awt.AlphaComposite.Clear);
        g.fillRect(0, 0, w, h);
        g.setComposite(java.awt.AlphaComposite.SrcOver);
        g.dispose();
        SwingFXUtils.fromFXImage(fxImage, buf);

        // Pack into big-endian ARGB byte array (network byte order)
        final byte[] bytes = new byte[w * h * 4];
        int i = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                final int argb = buf.getRGB(x, y);
                bytes[i++] = (byte) ((argb >> 24) & 0xFF); // A
                bytes[i++] = (byte) ((argb >> 16) & 0xFF); // R
                bytes[i++] = (byte) ((argb >>  8) & 0xFF); // G
                bytes[i++] = (byte) ( argb         & 0xFF); // B
            }
        }

        final List<Object[]> pixmaps = new ArrayList<>();
        pixmaps.add(new Object[]{ w, h, bytes });
        return pixmaps;
    }

    private void emitSignal(final DBusSignal signal) {
        try { connection.sendMessage(signal); }
        catch (final Exception ignored) {}
    }
}
