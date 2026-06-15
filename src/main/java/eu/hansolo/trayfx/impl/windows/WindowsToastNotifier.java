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
 * Attempts to use the bundled TrayFXNotifier.exe helper which uses WinRT
 * ToastNotificationManager to show a modern Windows 10/11 Toast notification
 * with custom icon support.
 *
 * <p>The helper is a framework-dependent .NET 10 executable (~100KB) that
 * requires .NET 10 runtime to be installed. Falls back to AWT
 * {@code displayMessage()} balloon tip if the helper is not found or fails.
 *
 * <h2>Building the helper</h2>
 * In Visual Studio 2022 with .NET 10, publish as framework-dependent
 * (not self-contained) win-x64 and place the exe at:
 * {@code src/main/resources/eu/hansolo/trayfx/windows/TrayFXNotifier.exe}
 *
 * <h2>Note on AppUserModelID</h2>
 * WinRT Toast requires a registered AppUserModelID. When running unpackaged
 * (development via Gradle) Windows 11 may suppress notifications silently.
 * When packaged with jpackage the AppUserModelID is registered automatically
 * and Toast notifications work correctly.
 */
final class WindowsToastNotifier {

    private WindowsToastNotifier() {}


    static void show(final String title, final String message, final Image icon, final java.awt.TrayIcon awtFallback) {
        if (tryNotifierExe(title, message, icon)) { return; }

        // Fall back to AWT balloon tip
        if (awtFallback != null) {
            awtFallback.displayMessage(title != null ? title : "", message != null ? message : "", java.awt.TrayIcon.MessageType.INFO);
        }
    }


    // Cache dotnet availability so we only check once
    private static volatile Boolean dotnetAvailable = null;

    private static boolean isDotNetAvailable() {
        if (dotnetAvailable != null) { return dotnetAvailable; }
        try {
            final Process p = new ProcessBuilder("dotnet", "--version").redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            dotnetAvailable = p.waitFor(3, java.util.concurrent.TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (final Exception ignored) {
            dotnetAvailable = false;
        }
        return dotnetAvailable;
    }

    private static boolean tryNotifierExe(final String title, final String message, final Image icon) {
        try {
            // Fail fast if .NET runtime is not installed
            if (!isDotNetAvailable()) { return false; }

            final java.net.URL helperUrl = WindowsToastNotifier.class.getResource("/eu/hansolo/trayfx/windows/TrayFXNotifier.exe");
            if (helperUrl == null) { return false; }

            final String helperPath = new File(helperUrl.toURI()).getAbsolutePath();
            if (!new File(helperPath).exists()) { return false; }

            final java.util.List<String> cmd = new java.util.ArrayList<>();
            cmd.add(helperPath);
            cmd.add("--title");
            cmd.add(title != null ? title : "");
            cmd.add("--message");
            cmd.add(message != null ? message : "");
            cmd.add("--appname");
            cmd.add(System.getProperty("trayfx.app.name", "TrayFX"));

            if (icon != null) {
                final File tmp = writeIconToTemp(icon);
                if (tmp != null) {
                    cmd.add("--icon");
                    cmd.add(tmp.getAbsolutePath());
                    tmp.deleteOnExit();
                }
            }
            new ProcessBuilder(cmd).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
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
}
