module eu.hansolo.trayfx {
    requires java.desktop;

    requires javafx.base;
    requires javafx.graphics;
    requires javafx.controls;
    requires javafx.swing;

    // D-Bus for Linux StatusNotifierItem — optional at runtime (static),
    // only present when dbus-java-core is on the module path (Linux builds).
    // The correct module name is confirmed from the compiler error message.
    requires static org.freedesktop.dbus;

    exports eu.hansolo.trayfx;
    exports eu.hansolo.trayfx.event;
    exports eu.hansolo.trayfx.menu;
    exports eu.hansolo.trayfx.example;
}
