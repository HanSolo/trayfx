package eu.hansolo.trayfx.example;

import eu.hansolo.trayfx.IconSpec;
import eu.hansolo.trayfx.TrayFX;
import eu.hansolo.trayfx.TrayIcon;
import eu.hansolo.trayfx.TrayIconGraphics;
import eu.hansolo.trayfx.TrayIconGraphics.BackgroundShape;
import eu.hansolo.trayfx.menu.MenuItem;
import eu.hansolo.trayfx.menu.TrayMenu;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


public class TrayFXExample extends Application {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private TrayIcon                 tray;
    private ScheduledExecutorService scheduler;
    private Stage                    stage;
    private Label                    statusLabel;



    @Override public void start(final Stage stage) {
        this.stage = stage;

        statusLabel = new Label("Waiting for tray events…");
        statusLabel.setFont(Font.font("System", FontWeight.NORMAL, 14));

        final Label hint = new Label("The tray icon is in your menu bar / system tray.\nClick it or use the menu to interact.");
        hint.setFont(Font.font("System", FontWeight.NORMAL, 12));
        hint.setTextAlignment(TextAlignment.CENTER);
        hint.setWrapText(true);

        final VBox root = new VBox(16, statusLabel, hint);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(24));

        stage.setTitle("TrayFX Example");
        stage.setScene(new Scene(root, 380, 180));
        stage.setOnCloseRequest(e -> { e.consume(); stage.hide(); });
        stage.show();

        // ── Install tray icon — one call, no threading concerns ────────────
        tray = TrayFX.trayIcon().icon(buildClockIcon(LocalTime.now()))
                                .text(LocalTime.now().format(TIME_FMT))
                                .textColor(Color.DODGERBLUE)
                                .menu(TrayMenu.builder().item(MenuItem.of("Show window",  () -> Platform.runLater(this::showWindow)))
                                                        .item(MenuItem.of("Hide window",  () -> Platform.runLater(this::hideWindow)))
                                                        .separator()
                                                        .item(MenuItem.of("Clock icon",   () -> setClockIcon(Color.DODGERBLUE)))
                                                        .separator()
                                                        .item(MenuItem.of("Normal (5.4)", () -> setGlucose("5.4", Color.MEDIUMSEAGREEN)))
                                                        .item(MenuItem.of("Low    (3.2)", () -> setGlucose("LO",  Color.TOMATO)))
                                                        .item(MenuItem.of("High   (14)",  () -> setGlucose("HI",  Color.ORANGE)))
                                                        .separator()
                                                        .item(MenuItem.of("Quit",         () -> Platform.runLater(this::quit)))
                                                        .build())
                                .onLeftClick(e -> Platform.runLater(this::toggleWindow))
                                .install();

        // Tick every minute
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            final Thread t = new Thread(r, "trayfx-clock");
            t.setDaemon(true);
            return t;
        });
        final long secsUntilNextMinute = 60 - LocalTime.now().getSecond();
        scheduler.scheduleAtFixedRate(this::updateClock, secsUntilNextMinute, 60, TimeUnit.SECONDS);
    }

    @Override public void stop() {
        if (scheduler != null) { scheduler.shutdownNow(); }
        if (tray      != null) { tray.uninstall(); }
    }


    private void toggleWindow() {
        if (stage.isShowing()) { hideWindow(); } else { showWindow(); }
    }

    private void showWindow() {
        stage.show();
        stage.toFront();
        setStatus("Window shown");
    }

    private void hideWindow()  { stage.hide(); }

    private void setClockIcon(final Color accent) {
        Platform.runLater(() -> {
            tray.setIcon(buildClockIcon(LocalTime.now(), accent));
            tray.setTextColor(accent);
            setStatus("Clock icon");
        });
    }

    private void setGlucose(final String value, final Color bgColor) {
        Platform.runLater(() -> {
            final Image icon = TrayIconGraphics.create()
                                               .text(value)
                                               .textColor(Color.WHITE)
                                               .background(bgColor, BackgroundShape.ROUNDED_RECT)
                                               .shapeInset(1)
                                               .cornerRadius(0.40)
                                               .build();
            tray.setIcon(icon);
            tray.setText(value);
            setStatus("Glucose: " + value);
        });
    }

    private void updateClock() {
        final LocalTime now = LocalTime.now();
        // buildClockIcon uses JavaFX Canvas — must run on FX thread
        Platform.runLater(() -> {
            tray.setIcon(buildClockIcon(now));
            tray.setText(now.format(TIME_FMT));
        });
    }

    private void quit() {
        tray.uninstall();
        Platform.exit();
    }

    private void setStatus(final String msg) { statusLabel.setText(msg); }


    private static Image buildClockIcon(final LocalTime time) {
        return buildClockIcon(time, Color.DODGERBLUE);
    }

    private static Image buildClockIcon(final LocalTime time, final Color accent) {
        final IconSpec spec = IconSpec.forCurrentPlatform();
        final int      size = spec.getPreferredWidth();

        final Canvas          canvas = new Canvas(size, size);
        final GraphicsContext ctx    = canvas.getGraphicsContext2D();
        final double centerX = size / 2.0;
        final double centerY = size / 2.0;
        final double radius  = size / 2.0 - 1;

        ctx.clearRect(0, 0, size, size);

        final boolean isLinux = eu.hansolo.trayfx.Platform.current() == eu.hansolo.trayfx.Platform.LINUX;
        if (isLinux) { // On Linux the snapshot always has a white background, no matter what snapshot parameters used, therefore we fill the clock background
            ctx.setFill(accent.darker());
            ctx.fillOval(0, 0, size, size);
        } else {
            ctx.setFill(Color.WHITE.deriveColor(0, 1, 1, 0.85));
            ctx.fillOval(1, 1, size - 2, size - 2);
            ctx.setStroke(accent);
            ctx.setLineWidth(Math.max(1, size / 16.0));
            ctx.strokeOval(1, 1, size - 2, size - 2);
        }

        final double hourAngle  = Math.toRadians((time.getHour() % 12) * 30.0 + time.getMinute() * 0.5 - 90);
        ctx.setStroke(isLinux ? Color.WHITE : Color.DARKSLATEGRAY);
        ctx.setLineWidth(Math.max(1.5, size / 11.0));
        ctx.strokeLine(centerX, centerY, centerX + Math.cos(hourAngle) * radius * 0.5, centerY + Math.sin(hourAngle) * radius * 0.5);

        final double minAngle = Math.toRadians(time.getMinute() * 6.0 - 90);
        ctx.setStroke(isLinux ? Color.WHITE : accent);
        ctx.setLineWidth(Math.max(1, size / 14.0));
        ctx.strokeLine(centerX, centerY, centerX + Math.cos(minAngle) * radius * 0.72, centerY + Math.sin(minAngle) * radius * 0.72);

        final double dotR = Math.max(1.5, size / 10.0);
        ctx.setFill(isLinux ? Color.WHITE : accent);
        ctx.fillOval(centerX - dotR, centerY - dotR, dotR * 2, dotR * 2);

        final WritableImage      snapshot = new WritableImage(size, size);
        final SnapshotParameters params   = new SnapshotParameters();
        params.setFill(Color.TRANSPARENT);
        canvas.snapshot(params, snapshot);
        return snapshot;
    }


    public static void main(final String[] args) {
        Application.launch(TrayFXExample.class, args);
    }
}
