package eu.hansolo.trayfx.impl.macos;

import eu.hansolo.trayfx.AbstractTrayIcon;
import eu.hansolo.trayfx.menu.TrayMenu;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;


/**
 * macOS implementation backed by {@code java.awt.SystemTray}.
 *
 * <h2>Variable-width icons</h2>
 * AWT's SystemTray always creates an NSStatusItem with squareLength, which
 * constrains the icon to a square slot. For wide images (e.g. glucose badges)
 * we use FFM to call setLength:NSVariableStatusItemLength on the underlying
 * NSStatusItem after AWT has created it, allowing the icon to expand
 * horizontally to fit the image width.
 *
 * <h2>Threading</h2>
 * SystemTray.add/remove must never be called from the JavaFX Application Thread on macOS,
 * doing so deadlocks because AWT internally dispatches to the Cocoa main thread which is already occupied.
 */
public final class MacOSTrayIcon extends AbstractTrayIcon {

    private static final double NS_VARIABLE_STATUS_ITEM_LENGTH = -1.0;

    private volatile java.awt.TrayIcon awtTrayIcon;


    @Override protected void nativeInstall() {
        offThread(() -> {
            try {
                awtTrayIcon = new java.awt.TrayIcon(toBufferedImage(getIcon()));
                awtTrayIcon.setImageAutoSize(false);
                if (getText() != null) { awtTrayIcon.setToolTip(getText()); }
                applyAwtMenu();
                SystemTray.getSystemTray().add(awtTrayIcon);
                setVariableLength();
                onNativeReady();
            } catch (AWTException e) {
                throw new RuntimeException("Failed to install tray icon", e);
            }
        });
    }

    @Override protected void nativeUninstall() {
        offThread(() -> {
            final java.awt.TrayIcon icon = awtTrayIcon;
            awtTrayIcon = null;
            if (icon != null) { SystemTray.getSystemTray().remove(icon); }
        });
    }

    @Override protected void nativeUpdateIcon(final Image icon) {
        offThread(() -> {
            final java.awt.TrayIcon t = awtTrayIcon;
            if (t != null) { t.setImage(toBufferedImage(icon)); }
        });
    }

    @Override protected void nativeUpdateText(final String text, final Color color) {
        offThread(() -> {
            final java.awt.TrayIcon t = awtTrayIcon;
            if (t != null) { t.setToolTip(text); }
        });
    }

    @Override protected void nativeUpdateMenu(final TrayMenu menu) { offThread(this::applyAwtMenu); }

    @Override protected void nativeShowNotification(final String title, final String message) {
        offThread(() -> {
            if (!tryNotifierApp(title, message)) {
                tryOsascript(title, message);
            }
        });
    }

    private boolean tryNotifierApp(final String title, final String message) {
        try {
            final java.net.URL helperUrl = MacOSTrayIcon.class.getResource(
            "/eu/hansolo/trayfx/macos/TrayFXNotifier.app/Contents/MacOS/TrayFXNotifier");

            if (helperUrl == null) { return false; }

            final String helperPath = new java.io.File(helperUrl.toURI()).getAbsolutePath();
            if (!new java.io.File(helperPath).canExecute()) { return false; }

            final java.util.List<String> cmd = new java.util.ArrayList<>();
            cmd.add(helperPath);
            cmd.add("--title");
            cmd.add(title   != null ? title   : "");
            cmd.add("--message");
            cmd.add(message != null ? message : "");

            final Image currentIcon = getIcon();
            if (currentIcon != null) {
                final java.io.File tmp = writeIconToTemp(currentIcon);
                if (tmp != null) {
                    cmd.add("--icon");
                    cmd.add(tmp.getAbsolutePath());
                    tmp.deleteOnExit();
                }
            }

            new ProcessBuilder(cmd).redirectErrorStream(true).start();
            return true;
        } catch (final Exception ignored) {
            return false;
        }
    }

    private boolean tryOsascript(final String title, final String message) {
        try {
            final String script = String.format("display notification %s with title %s", quotedAppleScript(message), quotedAppleScript(title));
            new ProcessBuilder("osascript", "-e", script).redirectErrorStream(true).start();
            return true;
        } catch (final Exception ignored) {
            return false;
        }
    }

    private static java.io.File writeIconToTemp(final Image fxImage) {
        try {
            final java.io.File tmp = java.io.File.createTempFile("trayfx-icon-", ".png");
            final BufferedImage buf = toBufferedImage(fxImage);
            javax.imageio.ImageIO.write(buf, "png", tmp);
            return tmp;
        } catch (final Exception ignored) {
            return null;
        }
    }

    // Wraps a string for AppleScript and escapes backslashes and double quotes
    private static String quotedAppleScript(final String s) {
        if (s == null) { return "\"\""; }
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }


    private void applyAwtMenu() {
        final java.awt.TrayIcon trayIcon = awtTrayIcon;
        if (trayIcon == null) { return; }

        final TrayMenu trayMenu = getMenu();
        if (trayMenu == null || trayMenu.isEmpty()) {
            trayIcon.setPopupMenu(null);
            return;
        }

        final java.awt.PopupMenu popup = new java.awt.PopupMenu();
        trayMenu.getItems().forEach(item -> {
            if (item.isSeparator()) {
                popup.addSeparator();
            } else if (item.isCheckItem()) {
                // AWT CheckboxMenuItem for check items
                final java.awt.CheckboxMenuItem chk = new java.awt.CheckboxMenuItem(item.getLabel(), item.isChecked());
                chk.setEnabled(item.isEnabled());
                chk.addItemListener(e -> Platform.runLater(item::fire));
                popup.add(chk);
            } else {
                // Regular item — prefix label with icon emoji if an icon image is set
                // (AWT PopupMenu does not support image icons natively)
                final String label = item.getLabel();
                final MenuItem awtItem = new MenuItem(label);
                awtItem.setEnabled(item.isEnabled());
                awtItem.addActionListener(e -> Platform.runLater(item::fire));
                popup.add(awtItem);
            }
        });
        trayIcon.setPopupMenu(popup);
    }


    /**
     * Uses FFM to set NSVariableStatusItemLength (-1.0) on the NSStatusItem
     * that AWT created internally. This allows wide images to expand the
     * menu bar slot beyond the default square size.
     *
     * We reach the NSStatusItem via reflection into AWT's peer class, then
     * call setLength: via the Objective-C runtime.
     */
    private void setVariableLength() {
        try {
            // Get the peer from AWT's TrayIcon via reflection
            final java.lang.reflect.Field peerField = java.awt.TrayIcon.class.getDeclaredField("peer");
            peerField.setAccessible(true);
            final Object peer = peerField.get(awtTrayIcon);
            if (peer == null) { return; }

            // The peer holds a reference to the native NSStatusItem pointer
            // Try common field names used in different JDK implementations
            long statusItemPtr = 0;
            for (final String fieldName : new String[]{ "ptr", "statusItem", "nativeStatusItem", "model" }) {
                try {
                    final java.lang.reflect.Field f = peer.getClass().getDeclaredField(fieldName);
                    f.setAccessible(true);
                    final Object val = f.get(peer);
                    if (val instanceof Long l) { statusItemPtr = l; break; }
                } catch (final NoSuchFieldException ignored) {}
            }

            if (statusItemPtr == 0) {
                // Fall back: find any long field that looks like a pointer
                for (final java.lang.reflect.Field f : peer.getClass().getDeclaredFields()) {
                    if (f.getType() == long.class) {
                        f.setAccessible(true);
                        final long val = f.getLong(peer);
                        if (val != 0) { statusItemPtr = val; break; }
                    }
                }
            }

            if (statusItemPtr == 0) { return; }

            // Call [statusItem setLength:NSVariableStatusItemLength] via FFM
            try (final Arena arena = Arena.ofConfined()) {
                final Linker linker = Linker.nativeLinker();

                // Load Objective-C runtime
                final SymbolLookup objc = SymbolLookup.libraryLookup("libobjc.A.dylib", arena);

                // objc_msgSend for setLength: (double arg)
                final MethodHandle msgSendDouble = linker.downcallHandle(
                    objc.find("objc_msgSend").orElseThrow(),
                    FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_DOUBLE)
                );

                // Get selector for setLength:
                final MethodHandle selRegister = linker.downcallHandle(
                    objc.find("sel_registerName").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS)
                );

                final MemorySegment selName = arena.allocateFrom("setLength:");
                final MemorySegment sel     = (MemorySegment) selRegister.invoke(selName);
                final MemorySegment item    = MemorySegment.ofAddress(statusItemPtr);

                msgSendDouble.invoke(item, sel, NS_VARIABLE_STATUS_ITEM_LENGTH);
            }
        } catch (final Throwable ignored) {
            // FFM not available or reflection failed — icon stays square
        }
    }


    private static BufferedImage toBufferedImage(final Image fxImage) {
        if (fxImage == null) { return new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB); }
        final int                 width  = (int) fxImage.getWidth();
        final int                 height = (int) fxImage.getHeight();
        final BufferedImage       argb   = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        final java.awt.Graphics2D g2d    = argb.createGraphics();
        g2d.setComposite(java.awt.AlphaComposite.Clear);
        g2d.fillRect(0, 0, width, height);
        g2d.dispose();
        SwingFXUtils.fromFXImage(fxImage, argb);
        return argb;
    }

    private static void offThread(final Runnable task) {
        Thread.ofVirtual().name("trayfx-awt").start(task);
    }
}
