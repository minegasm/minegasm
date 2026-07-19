package net.minegasm.config;

/**
 * Compatibility mode presets, preserving the legacy Minegasm mode names and semantics (brief §3.3).
 * The mode selects a data-driven {@code Preset} (per-event base intensity + enablement); it is never
 * hard-coded into event classes (brief §3.3, ADR-009).
 */
public enum MinegasmMode {
    /** Reward/action oriented; hurt and vitality off (legacy default). */
    NORMAL,
    /** Damage/critical oriented; suppress most reward events. */
    MASOCHIST,
    /** Broad event coverage. */
    HEDONIST,
    /** Gameplay events add to a decaying charge state. */
    ACCUMULATION,
    /** Per-event user values and routing. */
    CUSTOM;

    public boolean isAccumulation() {
        return this == ACCUMULATION;
    }

    public static MinegasmMode fromString(String s, MinegasmMode fallback) {
        if (s == null) {
            return fallback;
        }
        try {
            return valueOf(s.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
