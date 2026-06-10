# TrayFX

A clean, JavaFX-friendly library for adding a native tray / menu bar icon to
a JavaFX application. Works on macOS, Windows, and Linux with a single
fluent API — no threading, no boilerplate, no platform conditionals required
from the caller.

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

`install()` is safe to call from any thread. The icon appears asynchronously;
any `setIcon` / `setText` / `setMenu` calls made before the native icon is
ready are queued and applied in order automatically.

---

## Dynamic icon with TrayIconGraphics

```java
// Blood glucose badge — color-coded rounded rectangle
Image icon = TrayIconGraphics.create()
    .text("5.4")
    .textColor(Color.WHITE)
    .background(Color.MEDIUMSEAGREEN, BackgroundShape.ROUNDED_RECT)
    .shapeInset(1)       // 1 px gap all around
    .cornerRadius(0.40)
    .build();

tray.setIcon(icon);      // safe from any thread
```

---

## Platform support

| Platform | Backend                  |
|----------|--------------------------|
| macOS    | `java.awt.SystemTray`    |
| Windows  | `java.awt.SystemTray`    |
| Linux    | `java.awt.SystemTray`    |

All three platforms use `java.awt.SystemTray` as their backend, backed by
`javafx.embed.swing.SwingFXUtils` for image conversion. All AWT calls are
dispatched on a background daemon thread to avoid deadlocks with the JavaFX
Application Thread.

On macOS, `apple.awt.UIElement=true` is set automatically by the library
before the first `SystemTray` call so the icon appears in the menu bar
without a Dock presence.

---

## Runtime requirements

- JDK 25+
- JavaFX 26+
- `javafx.swing` on the module path (included in the Gradle dependencies)
- macOS: no extra setup — `apple.awt.UIElement` is handled automatically
- Windows: no extra setup
- Linux: requires a desktop environment with system tray support; some
  Wayland compositors may need XWayland

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
├── TrayIcon                Public interface (handle returned by install())
├── TrayIconGraphics        Renders text+shape icons without touching Canvas
├── AbstractTrayIcon        Base class — pending-update queue, thread safety
├── IconSpec                Platform icon size constraints
├── Platform                OS detection
├── event/TrayEvent         LEFT_CLICK / RIGHT_CLICK events
├── menu/
│   ├── MenuItem            Label + Runnable + separator support
│   └── TrayMenu            Ordered list of MenuItems, fluent builder
├── impl/macos/MacOSTrayIcon
├── impl/windows/WindowsTrayIcon
└── impl/linux/LinuxTrayIcon
```

---

## License

Apache 2.0
