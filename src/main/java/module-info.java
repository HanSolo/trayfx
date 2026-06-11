module eu.hansolo.trayfx {
    requires java.desktop;
    requires java.management;

    requires javafx.base;
    requires javafx.graphics;
    requires javafx.controls;
    requires javafx.swing;

    requires static org.freedesktop.dbus;

    exports eu.hansolo.trayfx;
    exports eu.hansolo.trayfx.event;
    exports eu.hansolo.trayfx.menu;
    exports eu.hansolo.trayfx.example;
}
