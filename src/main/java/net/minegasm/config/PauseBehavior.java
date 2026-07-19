package net.minegasm.config;

import java.util.Locale;

/** What haptic output should do while Minecraft is paused. */
public enum PauseBehavior {
    STOP,
    PAUSE,
    CONTINUE;

    public static PauseBehavior fromString(String value, PauseBehavior fallback) {
        if (value == null) return fallback;
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}
