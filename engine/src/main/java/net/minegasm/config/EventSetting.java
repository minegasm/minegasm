package net.minegasm.config;

/**
 * Per-event user setting: whether the event is enabled and its multiplier applied on top of the
 * preset base strength. Used by CUSTOM mode and as a global per-event gate in every mode.
 */
public final class EventSetting implements ConfigValue {

    private final boolean enabled;
    private final double multiplier;

    public EventSetting(boolean enabled, double multiplier) {
        this.enabled = enabled;
        this.multiplier = clamp(multiplier);
    }

    public boolean enabled() {
        return enabled;
    }

    public double multiplier() {
        return multiplier;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof EventSetting)) {
            return false;
        }
        EventSetting other = (EventSetting) o;
        return enabled == other.enabled && Double.compare(multiplier, other.multiplier) == 0;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(enabled, multiplier);
    }

    @Override
    public String toString() {
        return "EventSetting[enabled=" + enabled + ", multiplier=" + multiplier + "]";
    }
}
