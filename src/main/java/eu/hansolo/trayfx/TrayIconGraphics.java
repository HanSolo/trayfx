package eu.hansolo.trayfx;

import javafx.geometry.VPos;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;


/**
 * Fluent builder that renders a tray icon {@link Image} from declarative
 * properties like text, foreground color, background shape, size and inset,
 * without the caller needing to touch a {@link Canvas} directly.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Blood glucose, full-width rounded rectangle, white text
 * Image icon = TrayIconGraphics.create()
 *     .text("94 ↓↓")
 *     .textColor(Color.WHITE)
 *     .background(Color.web("#2e7d32"), BackgroundShape.ROUNDED_RECT)
 *     .shapeInset(0.05)   // 5% inset on each side, almost full size
 *     .build();
 *
 * // Identical result using shapeSize instead of inset:
 * Image icon2 = TrayIconGraphics.create()
 *     .text("94 ↓↓")
 *     .textColor(Color.WHITE)
 *     .background(Color.web("#2e7d32"), BackgroundShape.ROUNDED_RECT)
 *     .shapeSize(0.90)    // shape fills 90% of icon width/height
 *     .build();
 * }</pre>
 */
public final class TrayIconGraphics {

    /**
     * The shape drawn behind the text content.
     */
    public enum BackgroundShape {
        /** No background, transparent, content only. */
        NONE,
        /** Full rectangle. */
        RECT,
        /** Circle inscribed in the shape bounds. */
        CIRCLE,
        /** Rounded rectangle, the most common choice for text badges. */
        ROUNDED_RECT
    }

    private String          text;
    private Color           textColor       = Color.WHITE;
    private Font            font;
    private Paint           backgroundColor = Color.DODGERBLUE;
    private BackgroundShape backgroundShape = BackgroundShape.ROUNDED_RECT;
    private double          cornerRadius    = 0.35;   // fraction of shape size
    private double          shapeSize       = 1.0;    // fraction of icon size (1.0 = full)
    private double          shapeInset      = 0.0;    // pixels inset on each side (0 = none)
    private double          padding         = 0.10;   // text padding as fraction of icon
    private int             iconSize        = -1;     // -1 = use IconSpec

    private TrayIconGraphics() {}

    // Returns a new builder
    public static TrayIconGraphics create() {
        return new TrayIconGraphics();
    }


    /**
     * Text to render (e.g. {@code "94 ↓↓"}, {@code "HI"}, {@code "5.4"}).
     * Pass {@code null} to render background shape only.
     */
    public TrayIconGraphics text(final String text) {
        this.text = text;
        return this;
    }

    // Text foreground color. Default: {@link Color#WHITE}
    public TrayIconGraphics textColor(final Color color) {
        this.textColor = color != null ? color : Color.WHITE;
        return this;
    }

    /**
     * Explicit font. If not set, the font is autosized to fill roughly
     * 60% of the shape height, shrinking until the text fits horizontally.
     */
    public TrayIconGraphics font(final Font font) {
        this.font = font;
        return this;
    }

    /**
     * Shorthand for {@link #backgroundColor} + {@link #backgroundShape}.
     */
    public TrayIconGraphics background(final Paint color, final BackgroundShape shape) {
        this.backgroundColor = color;
        this.backgroundShape = shape;
        return this;
    }

    // Background fill color only (keeps current shape)
    public TrayIconGraphics backgroundColor(final Paint color) {
        this.backgroundColor = color != null ? color : Color.TRANSPARENT;
        return this;
    }

    // Background shape only (keeps current color)
    public TrayIconGraphics backgroundShape(final BackgroundShape shape) {
        this.backgroundShape = shape != null ? shape : BackgroundShape.NONE;
        return this;
    }

    /**
     * Corner radius for {@link BackgroundShape#ROUNDED_RECT} as a fraction
     * of the <em>shape</em> size. {@code 0.35} (default) gives well-rounded
     * corners; {@code 0.5} gives a pill/stadium shape.
     */
    public TrayIconGraphics cornerRadius(final double fraction) {
        this.cornerRadius = Math.max(0, Math.min(0.5, fraction));
        return this;
    }

    /**
     * Sets the background shape size as a <em>fraction of the icon size</em>.
     * {@code 1.0} fills the entire icon (edge to edge). {@code 0.9} leaves a
     * 5% gap on each side. {@code 0.5} makes the shape half the icon size,
     * centered.
     *
     * <p>This overrides any previous {@link #shapeInset(double)} call and vice versa, the last one set wins.
     *
     * <p>Default: {@code 1.0} (full size).
     */
    public TrayIconGraphics shapeSize(final double fraction) {
        this.shapeSize  = Math.max(0.1, Math.min(1.0, fraction));
        this.shapeInset = 0; // clear inset — shapeSize takes precedence
        return this;
    }

    /**
     * Sets the gap between the icon edge and the background shape in pixels.
     * {@code 0} (default) means the shape fills the full icon.
     * {@code 1} leaves a 1 px gap all around. {@code 2} leaves 2 px, etc.
     *
     * <p>This is often the most intuitive way to control shape size when
     * you want a specific pixel gap rather than a proportional one.
     *
     * <p>This overrides any previous {@link #shapeSize(double)} call and
     * vice versa — the last one set wins.
     */
    public TrayIconGraphics shapeInset(final double pixels) {
        this.shapeInset = Math.max(0, pixels);
        this.shapeSize  = 1.0; // will be recalculated in build()
        return this;
    }

    /**
     * Padding between the shape edge and the text content, as a fraction
     * of the icon size. Default: {@code 0.10}.
     */
    public TrayIconGraphics padding(final double fraction) {
        this.padding = Math.max(0, Math.min(0.4, fraction));
        return this;
    }

    /**
     * Override the output image size in pixels. By default the platform's
     * preferred size from {@link IconSpec} is used.
     */
    public TrayIconGraphics size(final int pixels) {
        this.iconSize = pixels;
        return this;
    }


    // Renders the icon. Must be called on the JavaFX Application Thread
    public Image build() {
        final int    size        = iconSize > 0 ? iconSize : IconSpec.forCurrentPlatform().getPreferredWidth();
        final double inset       = shapeInset > 0 ? shapeInset : size * (1.0 - shapeSize) / 2.0;
        final double shapeWidth  = size - inset * 2;
        final double shapeHeight = size - inset * 2;
        final double textPad     = size * padding;

        final Canvas          canvas = new Canvas(size, size);
        final GraphicsContext ctx    = canvas.getGraphicsContext2D();

        ctx.clearRect(0, 0, size, size);

        if (backgroundShape != BackgroundShape.NONE && backgroundColor != null) {
            ctx.setFill(backgroundColor);
            switch (backgroundShape) {
                case RECT         -> ctx.fillRect(inset, inset, shapeWidth, shapeHeight);
                case CIRCLE       -> ctx.fillOval(inset, inset, shapeWidth, shapeHeight);
                case ROUNDED_RECT -> {
                    final double arc = Math.min(shapeWidth, shapeHeight) * cornerRadius * 2;
                    ctx.fillRoundRect(inset, inset, shapeWidth, shapeHeight, arc, arc);
                }
                default           -> {}
            }
        }

        if (text != null && !text.isEmpty()) {
            final double maxTextWidth = shapeWidth - textPad * 2;
            final Font   resolveFont  = resolveFont(size, shapeHeight, maxTextWidth);

            ctx.setFill(textColor);
            ctx.setFont(resolveFont);
            ctx.setTextBaseline(VPos.CENTER);
            ctx.setTextAlign(TextAlignment.CENTER);
            ctx.fillText(text, inset + shapeWidth / 2.0, inset + shapeHeight / 2.0);
        }

        final WritableImage snapshot    = new WritableImage(size, size);
        final int[]         transparent = new int[size * size];
        snapshot.getPixelWriter().setPixels(0, 0, size, size, javafx.scene.image.PixelFormat.getIntArgbInstance(), transparent, 0, size);
        final SnapshotParameters params = new SnapshotParameters();
        params.setFill(Color.TRANSPARENT);
        canvas.snapshot(params, snapshot);
        return snapshot;
    }

    private Font resolveFont(final int iconSize, final double shapeH, final double maxW) {
        if (font != null) { return font; }
        double fontSize = shapeH * 0.55;
        if (text != null && !text.isEmpty()) {
            for (int i = 0; i < 12; i++) {
                if (measureTextWidth(text, Font.font("System", FontWeight.BOLD, fontSize)) <= maxW) break;
                fontSize *= 0.85;
            }
        }
        return Font.font("System", FontWeight.BOLD, fontSize);
    }

    private static double measureTextWidth(final String text, final Font font) {
        final Text t = new Text(text);
        t.setFont(font);
        return t.getBoundsInLocal().getWidth();
    }
}
