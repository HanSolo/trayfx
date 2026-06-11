package eu.hansolo.trayfx;

import eu.hansolo.trayfx.event.TrayEvent;
import eu.hansolo.trayfx.event.TrayEventType;
import eu.hansolo.trayfx.menu.TrayMenu;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;


/**
 * Base class for all platform implementations.
 *
 * <h2>Threading model</h2>
 * All public setters are safe to call from any thread. Updates arriving
 * before {@link #install()} completes are queued and applied in order once
 * the native icon is live. Updates after installation are applied immediately
 * via the platform's native thread mechanism.
 *
 * <h2>Pending-update queue</h2>
 * Rather than a heavyweight blocking queue, we use a simple list of
 * {@code Runnable} tasks that capture the update to apply. Once
 * {@link #onNativeReady()} is called by the subclass the queue is drained
 * in order and then discarded.
 */
public abstract class AbstractTrayIcon implements TrayIcon {
    private final    AtomicBoolean                  installed      = new AtomicBoolean(false);
    private final    AtomicBoolean                  nativeReady    = new AtomicBoolean(false);
    private final    CopyOnWriteArrayList<Runnable> pendingUpdates = new CopyOnWriteArrayList<>();
    private volatile Color                          textColor      = Color.BLACK;
    private volatile Image                          icon;
    private volatile String                         text;
    private volatile TrayMenu                       menu;
    private volatile Consumer<TrayEvent>            onLeftClick;
    private volatile Consumer<TrayEvent>            onRightClick;


    @Override public final void install() {
        if (installed.compareAndSet(false, true)) {
            nativeInstall();
        }
    }

    @Override public final void uninstall() {
        if (installed.compareAndSet(true, false)) {
            nativeReady.set(false);
            pendingUpdates.clear();
            nativeUninstall();
        }
    }

    @Override public final boolean isInstalled() { return installed.get(); }

    /**
     * Subclasses call this once the native icon is visible and ready to
     * accept updates. Drains any updates that arrived before native init
     * completed, then marks the icon as fully live.
     */
    protected final void onNativeReady() {
        nativeReady.set(true);
        pendingUpdates.forEach(Runnable::run);
        pendingUpdates.clear();
    }

    /** Enqueue or apply immediately depending on native readiness. */
    private void applyOrQueue(final Runnable update) {
        if (nativeReady.get()) {
            update.run();
        } else {
            pendingUpdates.add(update);
        }
    }

    protected abstract void nativeInstall();
    protected abstract void nativeUninstall();


    // ********** Icon ********************************************************
    @Override public Image getIcon() { return icon; }
    @Override public void setIcon(final Image icon) {
        this.icon = icon;
        applyOrQueue(() -> nativeUpdateIcon(icon));
    }

    protected abstract void nativeUpdateIcon(Image icon);


    // ********** Text ********************************************************
    @Override public String getText() { return text; }
    @Override public void setText(final String text) {
        this.text = text;
        applyOrQueue(() -> nativeUpdateText(text, textColor));
    }

    @Override public Color getTextColor() { return textColor; }
    @Override public void setTextColor(final Color color) {
        this.textColor = (color != null) ? color : Color.BLACK;
        applyOrQueue(() -> nativeUpdateText(text, this.textColor));
    }

    protected abstract void nativeUpdateText(String text, Color color);


    // ********** Menu ********************************************************
    @Override public TrayMenu getMenu() { return menu; }
    @Override public void setMenu(final TrayMenu menu) {
        this.menu = menu;
        applyOrQueue(() -> nativeUpdateMenu(menu));
    }

    protected abstract void nativeUpdateMenu(TrayMenu menu);


    // ********** Events ******************************************************
    @Override public Consumer<TrayEvent> getOnLeftClick() { return onLeftClick;  }
    @Override public void setOnLeftClick(final Consumer<TrayEvent> consumer)  { onLeftClick  = consumer; }

    @Override public void setOnRightClick(final Consumer<TrayEvent> consumer) { onRightClick = consumer; }
    @Override public Consumer<TrayEvent> getOnRightClick() { return onRightClick; }


    protected void fireLeftClick() {
        final Consumer<TrayEvent> h = onLeftClick;
        if (h != null) { h.accept(new TrayEvent(TrayEventType.LEFT_CLICK)); }
    }

    protected void fireRightClick() {
        final Consumer<TrayEvent> h = onRightClick;
        if (h != null) { h.accept(new TrayEvent(TrayEventType.RIGHT_CLICK)); }
    }

    /**
     * Static bridge methods so platform impls in sub-packages can call
     * protected methods on a parent instance without reflection.
     * Used by {@code LinuxDbusImpl} to call back into {@code LinuxTrayIcon}.
     */
    public static void doFireLeftClick(final AbstractTrayIcon icon)  { icon.fireLeftClick(); }
    public static void doFireRightClick(final AbstractTrayIcon icon) { icon.fireRightClick(); }
    public static void doOnNativeReady(final AbstractTrayIcon icon)  { icon.onNativeReady(); }
}
