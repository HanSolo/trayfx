package eu.hansolo.trayfx;

import javafx.scene.image.Image;


/**
 * Controls how {@link IconSpec#fit(Image)} scales an oversized image.
 */
public enum ScalePolicy {
    FIT_KEEP_ASPECT, // Scale down proportionally so the image fits within max dimensions
    FIT_STRETCH,     // Scale to fill the max box exactly, potentially distorting the image
    NONE             // Don't scale and return the image unchanged
}
