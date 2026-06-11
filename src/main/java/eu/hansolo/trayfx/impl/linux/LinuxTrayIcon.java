package eu.hansolo.trayfx.impl.linux;

import eu.hansolo.trayfx.AbstractTrayIcon;
import eu.hansolo.trayfx.menu.TrayMenu;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;

import java.awt.AWTException;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Graphics2D;
import java.awt.PopupMenu;
import java.awt.RenderingHints;
import java.awt.SystemTray;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;


/**
 * Linux implementation of {@link eu.hansolo.trayfx.TrayIcon}.
 *
 * <h2>Backend selection</h2>
 * Attempts to use the {@code org.kde.StatusNotifierItem} D-Bus protocol first,
 * which provides correct ARGB32 transparency and native menu rendering.
 * Falls back to {@code java.awt.SystemTray} (AWT/XEmbed) if D-Bus is not
 * available or the {@code dbus-java} library is not on the classpath.
 *
 * <p>The D-Bus implementation lives in {@link LinuxDbusImpl} which is only
 * compiled on Linux builds (excluded via {@code build.gradle} on macOS/Windows).
 * This class loads it reflectively so the source file itself compiles on all
 * platforms without importing dbus-java types directly.
 */
public final class LinuxTrayIcon extends AbstractTrayIcon {

    /** The actual backend — either D-Bus or AWT, chosen at install time. */
    private AbstractTrayIcon delegate;


    @Override
    protected void nativeInstall() {
        // Try D-Bus first (only available on Linux with dbus-java on classpath)
        if (isDbusAvailable()) {
            delegate = createDbusDelegate();
        }
        if (delegate == null) {
            // Fall back to AWT
            delegate = new AwtTrayIconDelegate();
        }

        // Wire up the delegate — copy current state and event handlers
        delegate.setOnLeftClick(getOnLeftClick());
        delegate.setOnRightClick(getOnRightClick());
        delegate.install();
    }

    @Override
    protected void nativeUninstall() {
        final AbstractTrayIcon d = delegate;
        if (d != null) { d.uninstall(); }
    }

    @Override
    protected void nativeUpdateIcon(final Image icon) {
        final AbstractTrayIcon d = delegate;
        if (d != null) { d.setIcon(icon); }
    }

    @Override
    protected void nativeUpdateText(final String text, final Color color) {
        final AbstractTrayIcon d = delegate;
        if (d != null) { d.setText(text); d.setTextColor(color); }
    }

    @Override
    protected void nativeUpdateMenu(final TrayMenu menu) {
        final AbstractTrayIcon d = delegate;
        if (d != null) { d.setMenu(menu); }
    }


    // ── Backend selection ─────────────────────────────────────────────────

    private static boolean isDbusAvailable() {
        try {
            Class.forName("org.freedesktop.dbus.connections.impl.DBusConnection");
            Class.forName("eu.hansolo.trayfx.impl.linux.LinuxDbusImpl");
            return true;
        } catch (final ClassNotFoundException e) {
            return false;
        }
    }

    private AbstractTrayIcon createDbusDelegate() {
        try {
            final Class<?> cls = Class.forName(
                "eu.hansolo.trayfx.impl.linux.LinuxDbusImpl");
            final AbstractTrayIcon impl = (AbstractTrayIcon) cls
                .getDeclaredConstructor(AbstractTrayIcon.class)
                .newInstance(this);
            return impl;
        } catch (final Exception e) {
            System.err.println("[TrayFX] D-Bus backend unavailable, falling back to AWT: " + e.getMessage());
            return null;
        }
    }


    // ── AWT fallback ──────────────────────────────────────────────────────

    /**
     * AWT/SystemTray fallback used when dbus-java is not available.
     * Keeps the old implementation as an inner class.
     */
    private static final class AwtTrayIconDelegate extends AbstractTrayIcon {

        private volatile java.awt.TrayIcon awtTrayIcon;
        private          int               traySize = 24;

        @Override
        protected void nativeInstall() {
            if (!SystemTray.isSupported()) {
                throw new UnsupportedOperationException(
                    "SystemTray not supported and D-Bus unavailable.");
            }
            offThread(() -> {
                try {
                    final Dimension d = SystemTray.getSystemTray().getTrayIconSize();
                    if (d != null && d.width > 0) {
                        traySize = Math.max(16, (d.width * 2) / 3);
                    }
                    awtTrayIcon = new java.awt.TrayIcon(toBufferedImage(getIcon()));
                    awtTrayIcon.setImageAutoSize(false);
                    if (getText() != null) { awtTrayIcon.setToolTip(getText()); }
                    awtTrayIcon.addMouseListener(new MouseAdapter() {
                        @Override public void mousePressed(final MouseEvent e) {
                            if (e.getButton() == MouseEvent.BUTTON1)      { fireLeftClick(); }
                            else if (e.getButton() == MouseEvent.BUTTON3) { showFreshMenu(e); }
                        }
                    });
                    SystemTray.getSystemTray().add(awtTrayIcon);
                    onNativeReady();
                } catch (AWTException e) {
                    throw new RuntimeException(e);
                }
            });
        }

        @Override protected void nativeUninstall() {
            offThread(() -> {
                final java.awt.TrayIcon t = awtTrayIcon; awtTrayIcon = null;
                if (t != null) { SystemTray.getSystemTray().remove(t); }
            });
        }

        @Override protected void nativeUpdateIcon(final Image icon) {
            offThread(() -> { final java.awt.TrayIcon t = awtTrayIcon;
                if (t != null) { t.setImage(toBufferedImage(icon)); } });
        }

        @Override protected void nativeUpdateText(final String text, final Color color) {
            offThread(() -> { final java.awt.TrayIcon t = awtTrayIcon;
                if (t != null) { t.setToolTip(text); } });
        }

        @Override protected void nativeUpdateMenu(final TrayMenu menu) {
            offThread(this::applyMenu);
        }

        private void applyMenu() {
            final java.awt.TrayIcon t = awtTrayIcon; if (t == null) { return; }
            final TrayMenu m = getMenu();
            if (m == null || m.isEmpty()) { t.setPopupMenu(null); return; }
            final PopupMenu popup = new PopupMenu();
            for (final eu.hansolo.trayfx.menu.MenuItem item : m.getItems()) {
                if (item.isSeparator()) { popup.addSeparator(); }
                else {
                    final java.awt.MenuItem ai = new java.awt.MenuItem(item.getLabel());
                    ai.setEnabled(item.isEnabled());
                    ai.addActionListener(e -> Platform.runLater(item::fire));
                    popup.add(ai);
                }
            }
            t.setPopupMenu(popup);
        }

        private void showFreshMenu(final MouseEvent e) {
            final TrayMenu m = getMenu(); if (m == null || m.isEmpty()) { return; }
            final java.awt.TrayIcon t = awtTrayIcon; if (t == null) { return; }
            final PopupMenu popup = new PopupMenu();
            for (final eu.hansolo.trayfx.menu.MenuItem item : m.getItems()) {
                if (item.isSeparator()) { popup.addSeparator(); }
                else {
                    final java.awt.MenuItem ai = new java.awt.MenuItem(item.getLabel());
                    ai.setEnabled(item.isEnabled());
                    ai.addActionListener(ev -> Platform.runLater(item::fire));
                    popup.add(ai);
                }
            }
            final Frame frame = new Frame();
            frame.setUndecorated(true); frame.setSize(1, 1);
            frame.setLocation(e.getXOnScreen(), e.getYOnScreen());
            frame.add(popup); frame.setVisible(true);
            popup.show(frame, 0, 0);
            frame.addWindowFocusListener(new WindowAdapter() {
                @Override public void windowLostFocus(final WindowEvent we) { frame.dispose(); }
            });
        }

        private BufferedImage toBufferedImage(final Image fxImage) {
            final int size = traySize;
            if (fxImage == null) { return new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB); }
            final int srcW = (int) fxImage.getWidth(), srcH = (int) fxImage.getHeight();
            final BufferedImage src = new BufferedImage(srcW, srcH, BufferedImage.TYPE_INT_ARGB);
            final Graphics2D gc = src.createGraphics();
            gc.setComposite(java.awt.AlphaComposite.Clear); gc.fillRect(0, 0, srcW, srcH);
            gc.setComposite(java.awt.AlphaComposite.SrcOver); gc.dispose();
            SwingFXUtils.fromFXImage(fxImage, src);
            if (srcW == size && srcH == size) { return src; }
            final BufferedImage scaled = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            final Graphics2D g = scaled.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING,     RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,  RenderingHints.VALUE_ANTIALIAS_ON);
            g.drawImage(src, 0, 0, size, size, null); g.dispose();
            return scaled;
        }

        private static void offThread(final Runnable task) {
            Thread.ofVirtual().name("trayfx-awt").start(task);
        }
    }
}
