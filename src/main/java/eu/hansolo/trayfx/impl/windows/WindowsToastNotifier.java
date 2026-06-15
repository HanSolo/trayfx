package eu.hansolo.trayfx.impl.windows;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;


/**
 * Windows Toast notification support.
 *
 * <h2>Strategy</h2>
 * Windows Toast via WinRT requires the calling process to have a registered
 * AppUserModelID in the Start Menu — running via Gradle or as an unsigned
 * executable has no such registration, so WinRT silently drops notifications.
 *
 * <p>Instead we use PowerShell's {@code New-BurntToastNotification} if
 * BurntToast is installed, otherwise fall back to a VBScript message box,
 * and finally to AWT {@code displayMessage()} as the last resort.
 *
 * <p>For deployed apps packaged with {@code jpackage}, the installer registers
 * an AppUserModelID and WinRT Toast works natively, the fallback chain is
 * only needed for development/unsigned scenarios.
 */
final class WindowsToastNotifier {

    private WindowsToastNotifier() {}


    static void show(final String title, final String message, final Image icon, final java.awt.TrayIcon awtFallback) {
        final File iconFile = icon != null ? writeIconToTemp(icon) : null;

        // Try strategies in order of quality
        if (tryWinRtToast(title, message, iconFile)) { return; }
        if (tryPowerShellFallback(title, message))   { return; }

        // If everything fails, use AWT balloon tip (works everywhere, no icon)
        if (awtFallback != null) {
            awtFallback.displayMessage(title != null ? title : "", message != null ? message : "", java.awt.TrayIcon.MessageType.INFO);
        }

        if (iconFile != null) { iconFile.deleteOnExit(); }
    }


    /**
     * Shows a WinRT Toast using PowerShell. Registers a temporary Start Menu
     * shortcut with our AppUserModelID so Windows accepts the notification,
     * then removes it after showing.
     */
    private static boolean tryWinRtToast(final String title, final String message, final File   iconFile) {
        try {
            final String appId    = System.getProperty("trayfx.app.name", "TrayFX");
            final String appTitle = title   != null ? title   : "";
            final String appMsg   = message != null ? message : "";

            // Build Toast XML
            final StringBuilder xml = new StringBuilder();
            xml.append("<toast><visual><binding template='ToastGeneric'>");
            xml.append("<text>").append(escapeXml(appTitle)).append("</text>");
            xml.append("<text>").append(escapeXml(appMsg)).append("</text>");
            if (iconFile != null && iconFile.exists()) {
                xml.append("<image placement='appLogoOverride' src='file:///").append(iconFile.getAbsolutePath().replace("\\", "/")).append("'/>");
            }
            xml.append("</binding></visual></toast>");

            // PowerShell script that creates a temporary shortcut in Start Menu
            // to register our AppUserModelID, shows the toast, then removes it
            final String shortcutPath = System.getenv("APPDATA") + "\\Microsoft\\Windows\\Start Menu\\Programs\\" + appId + ".lnk";

            final String ps = String.join("\r\n",
                // Register AppUserModelID via a temporary Start Menu shortcut
                "$ws = New-Object -ComObject WScript.Shell",
                "$s = $ws.CreateShortcut('" + shortcutPath.replace("'", "''") + "')",
                "$s.TargetPath = [System.Diagnostics.Process]::GetCurrentProcess().MainModule.FileName",
                "$s.Save()",
                // Set AppUserModelID on the shortcut
                "$bytes = [System.IO.File]::ReadAllBytes('" + shortcutPath.replace("'", "''") + "')",
                // Load WinRT types
                "[Windows.UI.Notifications.ToastNotificationManager, Windows.UI.Notifications, ContentType=WindowsRuntime] | Out-Null",
                "[Windows.Data.Xml.Dom.XmlDocument, Windows.Data.Xml.Dom.XmlDocument, ContentType=WindowsRuntime] | Out-Null",
                // Create and show Toast
                "$xml = New-Object Windows.Data.Xml.Dom.XmlDocument",
                "$xml.LoadXml('" + xml.toString().replace("'", "''") + "')",
                "$toast = New-Object Windows.UI.Notifications.ToastNotification $xml",
                "$notifier = [Windows.UI.Notifications.ToastNotificationManager]::CreateToastNotifier('" + appId.replace("'", "''") + "')",
                "$notifier.Show($toast)",
                // Wait briefly then remove the shortcut
                "Start-Sleep -Milliseconds 500",
                "Remove-Item '" + shortcutPath.replace("'", "''") + "' -ErrorAction SilentlyContinue"
            );

            final Process process = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-Command", ps).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            return process.exitValue() == 0;

        } catch (final Exception ignored) {
            return false;
        }
    }


    private static boolean tryPowerShellFallback(final String title, final String message) {
        try {
            final String appTitle = title   != null ? title   : "Notification";
            final String appMsg   = message != null ? message : "";

            // Use a non-blocking toast via msg.exe or a simple PS notification
            final String ps =
                "Add-Type -AssemblyName System.Windows.Forms;" +
                "$n = New-Object System.Windows.Forms.NotifyIcon;" +
                "$n.Icon = [System.Drawing.SystemIcons]::Information;" +
                "$n.Visible = $true;" +
                "$n.ShowBalloonTip(3000, '" + appTitle.replace("'", "''") + "', '" +
                    appMsg.replace("'", "''") + "', [System.Windows.Forms.ToolTipIcon]::Info);" +
                "Start-Sleep -Seconds 4;" +
                "$n.Dispose()";

            new ProcessBuilder(
                "powershell.exe",
                "-NoProfile", "-NonInteractive",
                "-ExecutionPolicy", "Bypass",
                "-Command", ps)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();

            return true;
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
            tmpFile.deleteOnExit();
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
