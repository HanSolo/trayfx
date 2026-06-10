package eu.hansolo.trayfx;

import eu.hansolo.trayfx.event.TrayEvent;
import eu.hansolo.trayfx.menu.TrayMenu;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;

import java.util.function.Consumer;


/**
 * Platform-independent handle for a tray / menu-bar icon.
 *
 * <p>Obtain an instance via {@link TrayFX#trayIcon()} and chain
 * {@link TrayFX.Builder#install()} to register it with the OS.
 */
public interface TrayIcon {

    // ── Lifecycle ────────────────────────────────────────────────────────────

    /**
     * Registers the icon with the OS and makes it visible.
     * Must be called after the JavaFX toolkit has started.
     */
    void install();

    /**
     * Removes the icon from the OS tray / menu bar and releases native
     * resources. Safe to call more than once.
     */
    void uninstall();

    /** Returns {@code true} if {@link #install()} has been called and
     *  {@link #uninstall()} has not yet been called. */
    boolean isInstalled();


    // ── Icon ─────────────────────────────────────────────────────────────────

    /** Replaces the displayed icon. Pass {@code null} to show text only. */
    void setIcon(Image icon);

    Image getIcon();


    // ── Text ─────────────────────────────────────────────────────────────────

    /**
     * Sets the text label shown next to (macOS) or as tooltip (Windows/Linux).
     * Pass {@code null} or empty string to hide text.
     */
    void setText(String text);

    String getText();

    /**
     * Sets the color of the text label (best-effort; not all platforms support
     * arbitrary color; falls back to system default where unsupported).
     */
    void setTextColor(Color color);

    Color getTextColor();


    // ── Menu ─────────────────────────────────────────────────────────────────

    /** Replaces the popup / dropdown menu. Pass {@code null} to remove it. */
    void setMenu(TrayMenu menu);

    TrayMenu getMenu();


    // ── Events ───────────────────────────────────────────────────────────────

    void setOnLeftClick(Consumer<TrayEvent> handler);
    void setOnRightClick(Consumer<TrayEvent> handler);

    Consumer<TrayEvent> getOnLeftClick();
    Consumer<TrayEvent> getOnRightClick();
}
