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
 * Falls back to AWT {@code displayMessage()} balloon tip if the helper is
 * not bundled or fails.
 *
 * <h2>Building the helper</h2>
 * Build TrayFXNotifier.exe in Visual Studio 2022 with .NET 10 targeting
 * net10.0-windows10.0.19041.0 and place it at:
 * {@code src/main/resources/eu/hansolo/trayfx/windows/TrayFXNotifier.exe}
 */
final class WindowsToastNotifier {

    private WindowsToastNotifier() {}


    static void show(final String            title,
                     final String            message,
                     final Image             icon,
                     final java.awt.TrayIcon awtFallback) {

        if (tryNotifierExe(title, message, icon)) { return; }

        // Fall back to AWT balloon tip
        if (awtFallback != null) {
            awtFallback.displayMessage(
                title   != null ? title   : "",
                message != null ? message : "",
                java.awt.TrayIcon.MessageType.INFO);
        }
    }


    private static boolean tryNotifierExe(final String title,
                                           final String message,
                                           final Image  icon) {
        try {
            final java.net.URL helperUrl = WindowsToastNotifier.class.getResource(
                "/eu/hansolo/trayfx/windows/TrayFXNotifier.exe");
            if (helperUrl == null) { return false; }

            final String helperPath = new File(helperUrl.toURI()).getAbsolutePath();
            if (!new File(helperPath).canExecute()) { return false; }

            final java.util.List<String> cmd = new java.util.ArrayList<>();
            cmd.add(helperPath);
            cmd.add("--title");   cmd.add(title   != null ? title   : "");
            cmd.add("--message"); cmd.add(message != null ? message : "");

            if (icon != null) {
                final File tmp = writeIconToTemp(icon);
                if (tmp != null) {
                    cmd.add("--icon");
                    cmd.add(tmp.getAbsolutePath());
                    tmp.deleteOnExit();
        }
    }

            new ProcessBuilder(cmd)
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
            final File tmp = File.createTempFile("trayfx-icon-", ".png");
            final int w = (int) fxImage.getWidth();
            final int h = (int) fxImage.getHeight();
            final BufferedImage buf = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            final Graphics2D g = buf.createGraphics();
            g.setComposite(AlphaComposite.Clear);
            g.fillRect(0, 0, w, h);
            g.dispose();
            SwingFXUtils.fromFXImage(fxImage, buf);
            ImageIO.write(buf, "png", tmp);
            tmp.deleteOnExit();
            return tmp;
        } catch (final Exception ignored) {
            return null;
        }
    }
}
