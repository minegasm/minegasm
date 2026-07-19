package net.minegasm.config;

/**
 * Per-event user setting: whether the event is enabled and its multiplier applied on top of the
 * preset base strength. Used by CUSTOM mode and as a global per-event gate in every mode.
 */
public record EventSetting(boolean enabled, double multiplier) {

    public EventSetting {
        multiplier = clamp(multiplier);
    }

    public static EventSetting of(boolean enabled, double multiplier) {
        return new EventSetting(enabled, multiplier);
    }

    public static EventSetting enabled(double multiplier) {
        return new EventSetting(true, multiplier);
    }

    private static double clamp(double v) {
        if (v < 0) {
            return 0;
        }
        return v > 4.0 ? 4.0 : v; // generous head-room but bounded
    }
}
