package eu.hansolo.trayfx;

import eu.hansolo.trayfx.event.TrayEvent;
import eu.hansolo.trayfx.impl.macos.MacOSTrayIcon;
import eu.hansolo.trayfx.impl.linux.LinuxTrayIcon;
import eu.hansolo.trayfx.impl.windows.WindowsTrayIcon;
import eu.hansolo.trayfx.menu.TrayMenu;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;

import java.util.function.Consumer;


/**
 * Entry point for TrayFX.
 *
 * <p>Create a tray icon with a single fluent chain — no threading,
 * no boilerplate, no platform conditionals needed from the caller:
 *
 * <pre>{@code
 * TrayIcon tray = TrayFX.trayIcon()
 *     .icon(myImage)
 *     .text("72°F")
 *     .menu(TrayMenu.of(
 *         MenuItem.of("Open",  () -> stage.show()),
 *         MenuItem.of("Quit",  javafx.application.Platform::exit)
 *     ))
 *     .onLeftClick(e -> stage.show())
 *     .install();
 * }</pre>
 *
 * <h2>Threading</h2>
 * Safe to call from any thread. Any subsequent calls to
 * {@link TrayIcon#setIcon}, {@link TrayIcon#setText}, etc. are also
 * thread-safe — updates that arrive before the native icon is ready
 * are queued and applied in order once installation completes.
 *
 * <h2>Lifecycle</h2>
 * {@link Builder#install()} automatically sets
 * {@code Platform.setImplicitExit(false)} so the app stays alive when
 * the last window closes. Call {@link TrayIcon#uninstall()} to remove
 * the icon and allow the JVM to exit normally.
 */
public final class TrayFX {

    static {
        final String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("mac") || os.contains("darwin")) {
            if (System.getProperty("apple.awt.UIElement") == null) {
                System.setProperty("apple.awt.UIElement", "true");
            }
        }
    }

    private TrayFX() {}


    // Returns a new {@link Builder}
    public static Builder trayIcon() {
        return new Builder();
    }

    // Returns the platform TrayFX is running on
    public static Platform currentPlatform() {
        return Platform.current();
    }

    // Returns {@code true} if TrayFX is supported on the current platform
    public static boolean isSupported() {
        return Platform.current() != Platform.UNSUPPORTED;
    }


    public static final class Builder {
        private Image                icon;
        private String               text;
        private Color                textColor  = Color.BLACK;
        private TrayMenu             menu;
        private Consumer<TrayEvent>  onLeftClick;
        private Consumer<TrayEvent>  onRightClick;
        private String               appName    = "TrayFX";

        private Builder() {}

        public Builder icon(final Image icon) {
            this.icon = icon;
            return this;
        }

        public Builder text(final String text) {
            this.text = text;
            return this;
        }

        public Builder textColor(final Color color) {
            this.textColor = color;
            return this;
        }

        public Builder menu(final TrayMenu menu) {
            this.menu = menu;
            return this;
        }

        public Builder onLeftClick(final Consumer<TrayEvent> handler) {
            this.onLeftClick = handler;
            return this;
        }

        public Builder appName(final String appName) {
            this.appName = appName != null ? appName : "TrayFX";
            return this;
        }

        public Builder onRightClick(final Consumer<TrayEvent> handler) {
            this.onRightClick = handler;
            return this;
        }

        /**
         * Builds the platform-appropriate {@link TrayIcon}, applies all
         * configured properties, installs it, and returns the handle.
         *
         * <p>Safe to call from any thread. Internally sets
         * {@code Platform.setImplicitExit(false)} and triggers async
         * native installation. Updates queued before the native icon is
         * ready are applied in order once installation completes.
         *
         * @return the installed {@link TrayIcon} handle
         * @throws UnsupportedOperationException if the platform is unsupported
         */
        public TrayIcon install() {
            if (!isSupported()) { throw new UnsupportedOperationException("TrayFX is not supported on: " + System.getProperty("os.name")); }

            javafx.application.Platform.setImplicitExit(false); // Keep the app alive when the last window closes
            System.setProperty("trayfx.app.name", appName);     // Store app name for notification backends

            final AbstractTrayIcon impl = createImpl();
            if (icon         != null) { impl.setIcon(icon); }
            if (text         != null) { impl.setText(text); }
            if (textColor    != null) { impl.setTextColor(textColor); }
            if (menu         != null) { impl.setMenu(menu); }
            if (onLeftClick  != null) { impl.setOnLeftClick(onLeftClick); }
            if (onRightClick != null) { impl.setOnRightClick(onRightClick); }

            impl.install();
            return impl;
        }

        private AbstractTrayIcon createImpl() {
            return switch (Platform.current()) {
                case MACOS       -> new MacOSTrayIcon();
                case WINDOWS     -> new WindowsTrayIcon();
                case LINUX       -> new LinuxTrayIcon();
                case UNSUPPORTED -> throw new UnsupportedOperationException();
            };
        }
    }
}
