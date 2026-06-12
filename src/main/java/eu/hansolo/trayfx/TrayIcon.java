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


    Image getIcon();
    void setIcon(Image icon);

    String getText();
    void setText(String text);

    Color getTextColor();
    void setTextColor(Color color);

    TrayMenu getMenu();
    void setMenu(TrayMenu menu);

    Consumer<TrayEvent> getOnLeftClick();
    void setOnLeftClick(Consumer<TrayEvent> handler);

    Consumer<TrayEvent> getOnRightClick();
    void setOnRightClick(Consumer<TrayEvent> handler);
}
