package eu.hansolo.trayfx.impl.linux;

import eu.hansolo.trayfx.menu.MenuItem;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;


/**
 * The D-Bus object exported as {@code /StatusNotifierItem}.
 *
 * <p>Implements both {@code org.kde.StatusNotifierItem} and
 * {@code org.freedesktop.DBus.Properties} so the tray host can read icon
 * data, title, status etc. via the standard Properties interface.
 *
 * <h2>Icon format (ARGB32)</h2>
 * Icons are passed as an array of {@code (width, height, data[])} structs
 * where {@code data} is a flat array of 32-bit ARGB pixels in network byte
 * order (big-endian). Each pixel is packed as {@code 0xAARRGGBB}.
 */
public final class StatusNotifierItemExport
    implements StatusNotifierItemInterface, Properties {

    static final String OBJECT_PATH  = "/StatusNotifierItem";
    static final String MENU_PATH    = "/StatusNotifierItem/Menu";
    static final String SNI_IFACE    = "org.kde.StatusNotifierItem";

    private final DBusConnection     connection;
    private final DbusMenuExport     menuExport;
    private final Consumer<Void>     onActivate;
    private final Consumer<Void>     onContextMenu;

    private volatile Image           icon;
    private volatile String          title  = "TrayFX";
    private volatile String          toolTip = "";

    // Pixmap cache — only recomputed when icon changes
    private volatile List<Object[]>  iconPixmaps = Collections.emptyList();


    StatusNotifierItemExport(final DBusConnection     connection,
                             final Consumer<Void>     onActivate,
                             final Consumer<Void>     onContextMenu) {
        this.connection    = connection;
        this.onActivate    = onActivate;
        this.onContextMenu = onContextMenu;
        this.menuExport    = new DbusMenuExport(connection);
    }

    DbusMenuExport getMenuExport() { return menuExport; }

    // ── Icon ──────────────────────────────────────────────────────────────

    void setIcon(final Image fxImage) {
        this.icon        = fxImage;
        this.iconPixmaps = fxImage != null ? toArgb32Pixmaps(fxImage)
                                           : Collections.emptyList();
        try { emitSignal(new StatusNotifierItemInterface.NewIcon(OBJECT_PATH)); }
        catch (final Exception ignored) {}
    }

    // ── Title / tooltip ───────────────────────────────────────────────────

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

    // ── Menu ──────────────────────────────────────────────────────────────

    void setMenu(final TrayMenu menu) {
        menuExport.setMenu(menu);
    }


    // ── StatusNotifierItem methods ────────────────────────────────────────

    @Override public boolean isRemote() { return false; }
    @Override public String  getObjectPath() { return OBJECT_PATH; }

    @Override
    public void Activate(final int x, final int y) {
        if (onActivate != null) { onActivate.accept(null); }
    }

    @Override
    public void ContextMenu(final int x, final int y) {
        if (onContextMenu != null) { onContextMenu.accept(null); }
    }

    @Override
    public void SecondaryActivate(final int x, final int y) {}

    @Override
    public void Scroll(final int delta, final String orientation) {}


    // ── Properties interface ──────────────────────────────────────────────

    @Override
    @SuppressWarnings("unchecked")
    public <A> A Get(final String iface, final String name) {
        return (A) getProperties(iface).get(name).getValue();
    }

    @Override
    public <A> void Set(final String iface, final String name, final A value) {}

    @Override
    public Map<String, Variant<?>> GetAll(final String iface) {
        return getProperties(iface);
    }

    private Map<String, Variant<?>> getProperties(final String iface) {
        final Map<String, Variant<?>> props = new HashMap<>();
        if (!SNI_IFACE.equals(iface)) { return props; }

        props.put("Category",    new Variant<>("ApplicationStatus"));
        props.put("Id",          new Variant<>("trayfx"));
        props.put("Title",       new Variant<>(title));
        props.put("Status",      new Variant<>("Active"));
        props.put("WindowId",    new Variant<>(new UInt32(0)));
        props.put("IconName",    new Variant<>(""));
        props.put("IconPixmap",  new Variant<>(iconPixmaps, "a(iiay)"));
        props.put("OverlayIconName",    new Variant<>(""));
        props.put("OverlayIconPixmap",  new Variant<>(Collections.emptyList(), "a(iiay)"));
        props.put("AttentionIconName",  new Variant<>(""));
        props.put("AttentionIconPixmap",new Variant<>(Collections.emptyList(), "a(iiay)"));
        props.put("AttentionMovieName", new Variant<>(""));
        props.put("ToolTip",     new Variant<>(
            new Object[]{"", Collections.emptyList(), title, toolTip},
            "(sa(iiay)ss)"));
        props.put("ItemIsMenu",  new Variant<>(Boolean.FALSE));
        props.put("Menu",        new Variant<>(MENU_PATH));

        return props;
    }


    // ── ARGB32 conversion ─────────────────────────────────────────────────

    /**
     * Converts a JavaFX {@link Image} to the ARGB32 pixmap format expected
     * by the StatusNotifierItem spec: a list of {@code (width, height, bytes[])}
     * structs where bytes are in network (big-endian) byte order.
     *
     * <p>The spec format is {@code a(iiay)} — array of (int width, int height,
     * array of bytes). Each pixel is 4 bytes: Alpha, Red, Green, Blue.
     *
     * <p>This format natively supports transparency — no compositing against
     * white, no alpha channel loss. This is why StatusNotifierItem icons have
     * correct transparency while AWT SystemTray icons do not on Linux.
     */
    static List<Object[]> toArgb32Pixmaps(final Image fxImage) {
        final int w = (int) fxImage.getWidth();
        final int h = (int) fxImage.getHeight();

        // Convert to ARGB BufferedImage
        final BufferedImage buf = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        final java.awt.Graphics2D g = buf.createGraphics();
        g.setComposite(java.awt.AlphaComposite.Clear);
        g.fillRect(0, 0, w, h);
        g.setComposite(java.awt.AlphaComposite.SrcOver);
        g.dispose();
        SwingFXUtils.fromFXImage(fxImage, buf);

        // Pack pixels into big-endian byte array (network byte order)
        final byte[] bytes = new byte[w * h * 4];
        int i = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                final int argb = buf.getRGB(x, y);
                bytes[i++] = (byte) ((argb >> 24) & 0xFF); // Alpha
                bytes[i++] = (byte) ((argb >> 16) & 0xFF); // Red
                bytes[i++] = (byte) ((argb >>  8) & 0xFF); // Green
                bytes[i++] = (byte) ( argb         & 0xFF); // Blue
            }
        }

        final List<Object[]> pixmaps = new ArrayList<>();
        pixmaps.add(new Object[]{ w, h, bytes });
        return pixmaps;
    }

    // ── Signal helper ─────────────────────────────────────────────────────

    private void emitSignal(final DBusSignal signal) {
        try {
            connection.sendMessage(signal);
        } catch (final Exception e) {
            // Signal emission failure is non-fatal
        }
    }
}
