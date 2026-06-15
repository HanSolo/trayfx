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

`install()` is safe to call from any thread. Any subsequent calls to
`setIcon`, `setText`, `setMenu` etc. are also thread-safe — updates that
arrive before the native icon is ready are queued and applied in order
automatically.

---

## TrayIconGraphics

Renders a tray icon image from declarative properties — text, colors,
background shape — without touching a `Canvas` directly:

```java
// Blood glucose badge — color-coded rounded rectangle, auto-width
Image icon = TrayIconGraphics.create()
                             .text("94 ↓↓")
                             .textColor(Color.WHITE)
                             .background(Color.web("#2e7d32"), BackgroundShape.ROUNDED_RECT)
                             .shapeInset(1)
                             .cornerRadius(0.40)
                             .build();

tray.setIcon(icon);      // safe from any thread
```

### Background shapes
`ROUNDED_RECT`, `CIRCLE`, `RECT`, `NONE`

### Variable width
For `ROUNDED_RECT` and `RECT` the canvas width expands automatically to
fit the text content. The height stays fixed at the platform's preferred
icon height. On macOS the width is capped at 178px (89 logical points @2×).

### Gradients
```java
final LinearGradient gradient = new LinearGradient(0, 0, 0, 1, true,
    CycleMethod.NO_CYCLE,
    new Stop(0.0, Color.web("#1565C0")),
    new Stop(1.0, Color.web("#42A5F5")));

TrayIconGraphics.create()
                .text("FX")
                .textColor(Color.WHITE)
                .backgroundGradient(gradient, BackgroundShape.ROUNDED_RECT)
                .build();
```

### Scale any image to platform size
```java
// Scales to the platform's preferred tray icon size with bicubic interpolation
tray.setIcon(TrayIconGraphics.ofImage(myImage));

// Or explicit target size
tray.setIcon(TrayIconGraphics.ofImage(myImage, 44, 44));
```

---

## Menu

```java
TrayMenu menu = TrayMenu.builder()
                        .item(MenuItem.of("Show window",  () -> stage.show()))
                        .item(MenuItem.of("Hide window",  () -> stage.hide()))
                        .separator()
                        // Check item — toggles on click, fires consumer with new state
                        .item(MenuItem.checkItem("Show notifications", true, checked -> {}))
                        .separator()
                        .item(MenuItem.of("Quit", javafx.application.Platform::exit))
                        .build();
```

### Runtime enable/disable
```java
final MenuItem item = MenuItem.of("Upload", this::upload);
item.setEnabled(false);   // disable at runtime
// ...
item.setEnabled(true);    // re-enable
tray.setMenu(tray.getMenu()); // push the update to the native menu
```

### Menu items with icon
```java
MenuItem.of("Open", myIconImage, () -> stage.show())
```
Note: AWT `PopupMenu` does not render icon images natively on all platforms.
The icon field is available for use in custom menu implementations.

---

## Notifications

```java
tray.showNotification("TrayFX", "Blood glucose is low!");
```

| Platform | Backend | Custom icon |
|---|---|---|
| macOS | `TrayFXNotifier.app` helper (if bundled) → falls back to `osascript` | ✅ as attachment |
| Windows | AWT `displayMessage()` balloon tip | ❌ |
| Linux | AWT `displayMessage()` passive popup | ❌ |

### macOS — TrayFXNotifier helper
For macOS notifications with custom icon support, build and bundle the
included Swift helper app:

1. Open `helper/TrayFXNotifier` in Xcode
2. Set bundle ID to `eu.hansolo.trayfx.notifier`
3. Build and sign with your Developer ID
4. Place `TrayFXNotifier.app` at:
   `src/main/resources/eu/hansolo/trayfx/macos/TrayFXNotifier.app`

Without the helper, `showNotification` falls back to `osascript` which
works without any setup but shows a generic icon in the notification header.

**Known limitation:** the notification header icon (top-left of the
notification) is blank for non-App-Store apps on macOS regardless of
signing — this is a macOS restriction. The tray icon is shown correctly
as an attachment thumbnail on the right side of the notification.

---

## Platform support

| Platform | Backend | Transparent icon | Variable width |
|---|---|---|---|
| macOS | `java.awt.SystemTray` + FFM `NSStatusItem` | ✅ | ✅ max 178px |
| Windows | `java.awt.SystemTray` | ✅ | ✅ |
| Linux | D-Bus `StatusNotifierItem` (primary) | ✅ | ✅ |
| Linux | `java.awt.SystemTray` (fallback) | ⚠️ limited | ❌ |

### macOS
Uses `java.awt.SystemTray`. `apple.awt.UIElement=true` is set automatically.
FFM is used to call `setLength:NSVariableStatusItemLength` on the underlying
`NSStatusItem` so wide icons (glucose badges etc.) display correctly.

### Windows
Uses `java.awt.SystemTray` mapping to the Windows notification area.

### Linux
Primary backend: `org.kde.StatusNotifierItem` D-Bus protocol via
`dbus-java`. Provides correct ARGB32 transparency and native menu rendering.
Requires GNOME Shell with the AppIndicator extension:
```bash
sudo apt install gnome-shell-extension-appindicator
gnome-extensions enable ubuntu-appindicators@ubuntu.com
```
Falls back to `java.awt.SystemTray` (XEmbed) if D-Bus is unavailable.

---

## Runtime requirements

- JDK 25+
- JavaFX 26+
- macOS: no extra setup
- Windows: no extra setup
- Linux: D-Bus session bus + AppIndicator extension for primary backend

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
├── TrayFX                  Entry point — fluent builder
├── TrayIcon                Public interface
├── TrayIconGraphics        Renders text+shape+gradient icons
├── AbstractTrayIcon        Base class — pending-update queue, thread safety
├── IconSpec                Platform icon size constraints
├── Platform                OS detection
├── ScalePolicy             Icon scaling behaviour
├── event/
│   ├── TrayEvent           Fired on left/right click
│   └── TrayEventType       LEFT_CLICK, RIGHT_CLICK
├── menu/
│   ├── MenuItem            Regular, CheckItem, and Separator items
│   └── TrayMenu            Ordered list of MenuItems, fluent builder
├── impl/macos/
│   └── MacOSTrayIcon       AWT + FFM NSStatusItem variable length
├── impl/windows/
│   └── WindowsTrayIcon     AWT SystemTray
├── impl/linux/
│   ├── LinuxTrayIcon       D-Bus primary / AWT fallback
│   ├── LinuxDbusImpl       StatusNotifierItem D-Bus backend
│   ├── StatusNotifierItemExport
│   ├── StatusNotifierItemInterface
│   ├── StatusNotifierWatcherInterface
│   ├── DbusMenuExport      com.canonical.dbusmenu implementation
│   ├── DbusMenuInterface
│   ├── MenuLayoutItem      Struct for GetLayout (ia{sv}av)
│   ├── GetLayoutResult     Tuple return for GetLayout (u(ia{sv}av))
│   └── GetGroupPropertiesResult  Struct for GetGroupProperties (ia{sv})
└── example/
    └── TrayFXExample       Clock + glucose + gradient + notification demo

helper/
└── TrayFXNotifier/         Swift helper app for macOS notifications
    ├── Package.swift
    └── Sources/TrayFXNotifier/
        ├── main.swift
        └── Info.plist
```

---

## dbus-java type system notes

Key patterns required for correct D-Bus marshalling in dbus-java 5.x:

- `GetLayoutResult` must extend `Tuple` with **unbounded** generic parameters
  `<A, B>` — bounded generics cause `ClassCastException`, non-generic `Tuple`
  produces empty `()` return
- Struct subclasses (`MenuLayoutItem`, `GetGroupPropertiesResult`) must use
  **private** fields with `@Position` annotations
- `GetGroupProperties` must return `List<Struct>` giving D-Bus type `a(ia{sv})`
- The `Menu` property must use D-Bus type `o` (object path) not `s` (string)
- Object paths must use Ayatana format `/org/ayatana/NotificationItem/name`

---

## License

Apache 2.0
