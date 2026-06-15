package eu.hansolo.trayfx.impl.windows;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;

import java.awt.Graphics2D;
import java.awt.AlphaComposite;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;


/**
 * Windows Toast notification support via PowerShell WinRT bridge.
 *
 * <p>Uses PowerShell to invoke the Windows.UI.Notifications WinRT API,
 * which produces modern Toast notifications on Windows 10/11. This avoids
 * the need for direct COM/WinRT FFM calls (which require COM initialization,
 * GUID tables, and vtable dispatch) while still producing native Toast UI.
 * <p>Falls back to AWT {@code displayMessage()} balloon tip if PowerShell
 * is unavailable or the WinRT call fails.
 */
final class WindowsToastNotifier {
    private static final String APP_ID = "eu.hansolo.TrayFX";


    private WindowsToastNotifier() {}


    static void show(final String title, final String message, final Image icon, final java.awt.TrayIcon awtFallback) {
        // Write icon to temp file if available
        File iconFile = null;
        if (icon != null) { iconFile = writeIconToTemp(icon); }

        if (!tryToast(title, message, iconFile)) {
            // Fall back to AWT balloon tip
            if (awtFallback != null) {
                awtFallback.displayMessage(title != null ? title   : "", message != null ? message : "", java.awt.TrayIcon.MessageType.INFO);
            }
        }

        if (iconFile != null) { iconFile.deleteOnExit(); }
    }


    private static boolean tryToast(final String title, final String message, final File   iconFile) {
        try {
            // Build the Toast XML — Windows.UI.Notifications uses XML templates
            final StringBuilder xml = new StringBuilder();
            xml.append("<toast>");
            xml.append("<visual><binding template='ToastGeneric'>");
            xml.append("<text>").append(escapeXml(title   != null ? title   : "")).append("</text>");
            xml.append("<text>").append(escapeXml(message != null ? message : "")).append("</text>");
            if (iconFile != null && iconFile.exists()) {
                xml.append("<image placement='appLogoOverride' src='").append(iconFile.getAbsolutePath().replace("\\", "\\\\")).append("'/>");
            }
            xml.append("</binding></visual></toast>");

            // PowerShell script to show a WinRT Toast notification
            final String ps = String.join("\n",
                "[Windows.UI.Notifications.ToastNotificationManager, Windows.UI.Notifications, ContentType=WindowsRuntime] | Out-Null",
                "[Windows.Data.Xml.Dom.XmlDocument, Windows.Data.Xml.Dom.XmlDocument, ContentType=WindowsRuntime] | Out-Null",
                "$xml = New-Object Windows.Data.Xml.Dom.XmlDocument",
                "$xml.LoadXml('" + xml.toString().replace("'", "''") + "')",
                "$toast = New-Object Windows.UI.Notifications.ToastNotification $xml",
                "[Windows.UI.Notifications.ToastNotificationManager]::CreateToastNotifier('" + APP_ID + "').Show($toast)"
            );

            final Process process = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", ps).redirectErrorStream(true).start();

            // Wait briefly to catch immediate failures
            final boolean done = process.waitFor(3, java.util.concurrent.TimeUnit.SECONDS);
            return done && process.exitValue() == 0;

        } catch (final Exception ignored) {
            return false;
        }
    }


    private static File writeIconToTemp(final Image fxImage) {
        try {
            final File          tmpFile       = File.createTempFile("trayfx-icon-", ".png");
            final int           width         = (int) fxImage.getWidth();
            final int           height        = (int) fxImage.getHeight();
            final BufferedImage bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            final Graphics2D    g2d           = bufferedImage.createGraphics();
            g2d.setComposite(AlphaComposite.Clear);
            g2d.fillRect(0, 0, width, height);
            g2d.dispose();
            SwingFXUtils.fromFXImage(fxImage, bufferedImage);
            ImageIO.write(bufferedImage, "png", tmpFile);
            return tmpFile;
        } catch (final Exception ignored) {
            return null;
        }
    }

    private static String escapeXml(final String s) {
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
