package eu.hansolo.trayfx;

import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;


/**
 * Describes the icon size constraints for the current platform and provides
 * utilities to fit a JavaFX {@link Image} within those constraints.
 *
 * <h2>Platform constraints</h2>
 *
 * <h3>macOS</h3>
 * The menu bar is 24 pt tall (22 pt on older macOS).  Icons should be
 * <strong>16×16 pt</strong> at 1× (non-Retina) and <strong>32×32 px</strong>
 * on Retina (@2×) displays.  Apple recommends supplying a template image (black
 * with alpha) at 18×18 pt so the system can tint it automatically; arbitrary
 * colours are supported via {@code NSStatusBarButton.attributedTitle} but the
 * icon itself should still be square and no taller than 22 px (1×).
 * Providing a non-square or oversized image causes AppKit to scale it down,
 * often with visible quality loss. You can also set a maxWidth parameter on MacOS
 * which will limit the icon width to 178px.
 *
 * <h3>Windows</h3>
 * The notification area (system tray) uses icons whose size tracks the DPI
 * setting.  At 96 DPI (100 %) the shell uses <strong>16×16 px</strong>.  At
 * 120 DPI (125 %) it uses 20×20 px, and at 144 DPI (150 %) it uses 24×24 px.
 * {@code Shell_NotifyIconW} accepts an {@code HICON}; supplying a larger bitmap
 * results in the shell scaling it down with nearest-neighbour interpolation
 * unless you provide an icon at the exact size.  The safe cross-DPI choice is
 * to supply 16, 20, and 24 px variants, or a single 24 px image and let the
 * shell scale down to smaller sizes.
 *
 * <h3>Linux (libappindicator)</h3>
 * GNOME Shell and Unity typically render indicators at <strong>22×22 px</strong>
 * inside a 24 px panel.  KDE Plasma uses <strong>22×22 px</strong> as well.
 * libappindicator resolves icons by name from the system icon theme, so the
 * library writes the image to a temp file and passes the full path; the size
 * must match what the panel expects, otherwise the compositor scales it.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * IconSpec spec = IconSpec.forCurrentPlatform();
 *
 * // Check before passing to the builder
 * if (!spec.isSuitable(myImage)) {
 *     myImage = spec.fit(myImage);
 * }
 *
 * TrayFX.create()
 *     .icon(myImage)
 *     ...
 * }</pre>
 */
public final class IconSpec {
    public static final IconSpec MACOS   = new IconSpec("macOS menu bar", 22, 22, 44, 44, ScalePolicy.FIT_KEEP_ASPECT, 178);
    public static final IconSpec WINDOWS = new IconSpec("Windows notification area", 16, 16, 24, 24, ScalePolicy.FIT_KEEP_ASPECT, -1);
    public static final IconSpec LINUX   = new IconSpec("Linux panel (appindicator)", 16, 16, 16, 16, ScalePolicy.FIT_KEEP_ASPECT, -1);
    public static final IconSpec NONE    = new IconSpec("No constraint", Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, ScalePolicy.NONE, -1);


    private final String      description;
    private final int         minWidth;
    private final int         minHeight;
    private final int         maxWidth;
    private final int         maxHeight;
    private final ScalePolicy scalePolicy;
    private final int         maxShapeWidth;


    private IconSpec(final String description, final int minWidth, final int minHeight, final int maxWidth, final int maxHeight, final ScalePolicy scalePolicy, final int maxShapeWidth) {
        this.description   = description;
        this.minWidth      = minWidth;
        this.minHeight     = minHeight;
        this.maxWidth      = maxWidth;
        this.maxHeight     = maxHeight;
        this.scalePolicy   = scalePolicy;
        this.maxShapeWidth = maxShapeWidth;
    }


    // Returns the {@link IconSpec} appropriate for the current platform
    public static IconSpec forCurrentPlatform() {
        return switch (Platform.current()) {
            case MACOS       -> MACOS;
            case WINDOWS     -> WINDOWS;
            case LINUX       -> LINUX;
            case UNSUPPORTED -> NONE;
        };
    }

    // Human-readable description of this spec (e.g. "macOS menu bar")
    public String getDescription() { return description; }

    // Recommended minimum icon width in pixels, smaller images will appear blurry due to upscaling by the OS
    public int getMinWidth()  { return minWidth;  }
    public int getMinHeight() { return minHeight; }


    // Maximum icon width in pixels, images wider than this will be scaled down (with quality loss) by the OS
    public int getMaxWidth()  { return maxWidth;  }
    public int getMaxHeight() { return maxHeight; }

    // Max canvas width for ROUNDED_RECT / RECT shapes. -1 = unconstrained.
    public int getMaxShapeWidth() { return maxShapeWidth; }

    // Returns the preferred (ideal) width, currently the maximum
    public int getPreferredWidth()  { return maxWidth;  }
    public int getPreferredHeight() { return maxHeight; }


    /**
     * Returns {@code true} if {@code image} fits within this spec's
     * maximum dimensions without any scaling needed.
     *
     * @throws IllegalArgumentException if {@code image} is null
     */
    public boolean isSuitable(final Image image) {
        if (image == null) { throw new IllegalArgumentException("image must not be null"); }
        return (int) image.getWidth()  <= maxWidth && (int) image.getHeight() <= maxHeight;
    }


    // Returns {@code true} if {@code image} meets the minimum size requirement (i.e. will not be blurry due to upscaling).
    public boolean isLargeEnough(final Image image) {
        if (image == null) { throw new IllegalArgumentException("image must not be null"); }
        return (int) image.getWidth()  >= minWidth && (int) image.getHeight() >= minHeight;
    }

    /**
     * Returns {@code image} unchanged if it already fits this spec, otherwise
     * returns a rescaled copy that fits within the maximum dimensions while
     * honouring the spec's {@link ScalePolicy}.
     *
     * <p>The scaling is performed in Java (nearest-neighbour for speed);
     * for production use you may want to apply a higher-quality filter before
     * handing the image to the library.
     *
     * @param image the source image; must not be {@code null}
     * @return a possibly new {@link Image} that fits this spec
     */
    public Image fit(final Image image) {
        if (image == null) { throw new IllegalArgumentException("image must not be null"); }
        if (scalePolicy == ScalePolicy.NONE || isSuitable(image)) { return image; }

        final int sourceWidth  = (int) image.getWidth();
        final int sourceHeight = (int) image.getHeight();
        final int destinationWidth;
        final int destinationHeight;

        if (scalePolicy == ScalePolicy.FIT_KEEP_ASPECT) {
            final double scale = Math.min((double) maxWidth / sourceWidth, (double) maxHeight / sourceHeight);
            destinationWidth  = Math.max(1, (int) Math.round(sourceWidth * scale));
            destinationHeight = Math.max(1, (int) Math.round(sourceHeight * scale));
        } else {
            destinationWidth  = maxWidth;
            destinationHeight = maxHeight;
        }
        return scale(image, sourceWidth, sourceHeight, destinationWidth, destinationHeight);
    }

    // Like {@link #fit(Image)}, but scales to exactly the preferred (maximum) dimensions, stretching if necessary
    public Image fitExact(final Image image) {
        if (image == null) { throw new IllegalArgumentException("image must not be null"); }
        final int sourceWidth  = (int) image.getWidth();
        final int sourceHeight = (int) image.getHeight();
        if (sourceWidth == maxWidth && sourceHeight == maxHeight) { return image; }
        return scale(image, sourceWidth, sourceHeight, maxWidth, maxHeight);
    }


    private static Image scale(final Image src, final int srcWidth, final int srcHeight, final int destWidth, final int destHeight) {
        final WritableImage destination = new WritableImage(destWidth, destHeight);
        final PixelReader   reader      = src.getPixelReader();
        final PixelWriter   writer      = destination.getPixelWriter();

        for (int y = 0; y < destHeight; y++) {
            for (int x = 0; x < destWidth; x++) {
                final int sourceX = Math.min((int) ((double) x / destWidth * srcWidth), srcWidth - 1);
                final int sourceY = Math.min((int) ((double) y / destHeight * srcHeight), srcHeight - 1);
                writer.setArgb(x, y, reader.getArgb(sourceX, sourceY));
            }
        }
        return destination;
    }

    @Override public String toString() {
        return "IconSpec[" + description + ", min=" + minWidth + "×" + minHeight + ", max=" + maxWidth + "×" + maxHeight + "]";
    }
}
