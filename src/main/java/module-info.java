module eu.hansolo.trayfx {
    requires java.desktop;

    requires javafx.base;
    requires javafx.graphics;
    requires javafx.controls;
    requires javafx.swing;

    // D-Bus library for Linux StatusNotifierItem — only present on Linux builds.
    // 'static' makes it optional so the module compiles/runs on macOS and Windows
    // without dbus-java on the module path.
    requires static dbus.java.core;

    exports eu.hansolo.trayfx;
    exports eu.hansolo.trayfx.event;
    exports eu.hansolo.trayfx.menu;
    exports eu.hansolo.trayfx.example;
}
