package eu.hansolo.trayfx;


/**
 * Identifies the host operating system at runtime.
 */
public enum Platform {
    MACOS,
    WINDOWS,
    LINUX,
    UNSUPPORTED;

    private static final Platform CURRENT = detect();

    private static Platform detect() {
        final String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("mac") || os.contains("darwin")) { return MACOS;   }
        if (os.contains("win"))                           { return WINDOWS; }
        if (os.contains("nux") || os.contains("nix"))    { return LINUX;   }
        return UNSUPPORTED;
    }

    /** Returns the platform the JVM is currently running on. */
    public static Platform current() { return CURRENT; }

    public boolean isMacOS()   { return this == MACOS;   }
    public boolean isWindows() { return this == WINDOWS; }
    public boolean isLinux()   { return this == LINUX;   }
}
