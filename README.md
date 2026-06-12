# TrayFX

A JavaFX-friendly library for adding a native tray / menu bar icon to a JavaFX application. 
Works on macOS, Windows, and Linux with a single fluent API.

---

## Quick start

```java
TrayIcon tray = TrayFX.trayIcon()
                      .icon(myImage)
                      .text("72°F")
                      .menu(TrayMenu.of(
                          MenuItem.of("Show",  () -> stage.show()),
                          MenuItem.of("Quit",  javafx.application.Platform::exit)
                      ))
                      .onLeftClick(e -> stage.show())
                      .install();
```

`install()` is safe to call from any thread. The icon appears asynchronously.
Any call to `setIcon`, `setText` or `setMenu` made before the native icon is
ready are queued and applied in order automatically.

---

## Dynamic icon with TrayIconGraphics

`TrayIconGraphics` renders a tray icon image from declarative properties as text, colors, background shape, without touching a `Canvas` directly:

```java
// Blood glucose badge with color coded rounded rectangle
Image icon = TrayIconGraphics.create()
                             .text("5.4")
                             .textColor(Color.WHITE)
                             .background(Color.MEDIUMSEAGREEN, BackgroundShape.ROUNDED_RECT)
                             .shapeInset(1)       // 1 px gap all around
                             .cornerRadius(0.40)
                             .build();

tray.setIcon(icon);      // safe from any thread
```

Background shapes: `ROUNDED_RECT`, `CIRCLE`, `RECT`, `NONE`.
Font is auto-sized to fit, supply your own via `.font(Font)` if needed.

---

## Platform support

| Platform | Backend                              | Transparency |
|----------|--------------------------------------|--------------|
| macOS    | `java.awt.SystemTray`                | ✅            |
| Windows  | `java.awt.SystemTray`                | ✅            |
| Linux    | D-Bus `StatusNotifierItem` (primary) | ✅            |
| Linux    | `java.awt.SystemTray` (fallback)     | ⚠️ limited   |

### macOS
Uses `java.awt.SystemTray`. `apple.awt.UIElement=true` is set automatically
so the icon appears in the menu bar without a Dock entry. All AWT calls are
dispatched on a background thread to avoid deadlocking with the JavaFX
Application Thread.

### Windows
Uses `java.awt.SystemTray` mapping to the Windows notification area via the
Shell notification icon API.

### Linux
Attempts to use the `org.kde.StatusNotifierItem` D-Bus protocol (via
`dbus-java`) which provides full ARGB32 transparency and native menu
rendering via `com.canonical.dbusmenu`. This is the protocol used by all
modern Linux desktop environments (GNOME with AppIndicator extension,
KDE Plasma, XFCE, etc.).

Falls back to `java.awt.SystemTray` (XEmbed) if `dbus-java` is not on the
classpath or the D-Bus session bus is unavailable. The AWT fallback has
limited transparency support due to a GTK/XEmbed limitation.

**GNOME Shell requirement:** the `ubuntu-appindicators` extension must be
installed and enabled for the icon to appear in GNOME Shell:
```bash
sudo apt install gnome-shell-extension-appindicator
gnome-extensions enable ubuntu-appindicators@ubuntu.com
```

---

## Runtime requirements

- JDK 25+
- JavaFX 26+
- macOS  : no extra setup, `apple.awt.UIElement` is handled automatically
- Windows: no extra setup
- Linux (D-Bus backend): `dbus-java-core` + `dbus-java-transport-junixsocket`
  are included automatically by the Gradle build on Linux; requires a running
  D-Bus session bus and a compatible tray host
- Linux (AWT fallback): requires a desktop environment with XEmbed system
  tray support; some Wayland compositors may need XWayland

---

## Building

```bash
./gradlew build
```

## Running the example

```bash
./gradlew runExample
```

---

## Project structure

```
eu.hansolo.trayfx
├── TrayFX                  Entry point (fluent builder)
├── TrayIcon                Public interface (handle returned by install())
├── TrayIconGraphics        Renders text+shape icons without touching Canvas
├── AbstractTrayIcon        Base class (pending-update queue, thread safety)
├── IconSpec                Platform icon size constraints
├── Platform                OS detection
├── ScalePolicy             Icon scaling behaviour enum
├── event/
│   ├── TrayEvent           Fired on left/right click
│   └── TrayEventType       LEFT_CLICK, RIGHT_CLICK
├── menu/
│   ├── MenuItem            Label + Runnable + separator support
│   └── TrayMenu            Ordered list of MenuItems, fluent builder
├── impl/macos/
│   └── MacOSTrayIcon       AWT SystemTray implementation
├── impl/windows/
│   └── WindowsTrayIcon     AWT SystemTray implementation
├── impl/linux/
│   ├── LinuxTrayIcon       Delegates to D-Bus impl or AWT fallback
│   ├── LinuxDbusImpl       D-Bus StatusNotifierItem backend
│   ├── StatusNotifierItemExport    org.kde.StatusNotifierItem object
│   ├── StatusNotifierItemInterface D-Bus interface definition
│   ├── StatusNotifierWatcherInterface  Watcher proxy
│   ├── DbusMenuExport      com.canonical.dbusmenu object
│   ├── DbusMenuInterface   D-Bus interface definition
│   ├── MenuLayoutItem      Struct for GetLayout tree nodes (ia{sv}av)
│   ├── GetLayoutResult     Tuple return type for GetLayout (u(ia{sv}av))
│   └── GetGroupPropertiesResult  Struct for GetGroupProperties (ia{sv})
└── example/
    └── TrayFXExample       Clock + glucose badge demo
```

---

## dbus-java type system notes

Getting `GetLayout` to return the correct D-Bus type `(u(ia{sv}av))` from
dbus-java 5.x requires specific patterns that are not obvious from the docs:

- `GetLayoutResult` must extend `Tuple` with **unbounded** generic parameters
  (`<A, B>`) — bounded generics cause `ClassCastException`, non-generic
  `Tuple` subclasses produce empty `()` return
- `Struct` subclasses (`MenuLayoutItem`, `GetGroupPropertiesResult`) must use
  **private** fields with `@Position` annotations for correct serialisation
  order — public fields may be enumerated in wrong order by the JVM
- `GetGroupProperties` must return `List<Struct>` giving D-Bus type `a(ia{sv})`
- The `Menu` property in `StatusNotifierItem` must use D-Bus type `o`
  (object path) not `s` (string) — AppIndicator uses the type to connect
  to the menu object

---

## License

Apache 2.0
