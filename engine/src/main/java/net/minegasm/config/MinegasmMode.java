package net.minegasm.config;

import java.util.Locale;

/**
 * Compatibility mode presets. The mode selects a data-driven {@code Preset} (per-event base intensity +
 * enablement); it is never hard-coded into event classes (brief §3.3, ADR-009).
 *
 * <p>These names replaced the original Minegasm set. {@link #fromString} maps the old names (NORMAL,
 * MASOCHIST, HEDONIST, ACCUMULATION) onto the current ones, so a stored config or an imported legacy
 * {@code minegasm-client.toml} written before the rename keeps working.
 */
public enum MinegasmMode {
    /** Triggered by actions the player performs (was NORMAL; the legacy default). */
    ACTION,
    /** Triggered by things that happen to the player (was MASOCHIST). */
    REACTION,
    /** Uses as many triggers as possible for broad coverage (was HEDONIST). */
    IMMERSION,
    /** Gameplay events add to a charge that accumulates and decays (was ACCUMULATION). */
    MOMENTUM,
    /** Per-event user values and routing. */
    CUSTOM;

    public boolean isMomentum() {
        return this == MOMENTUM;
    }

    public static MinegasmMode fromString(String s, MinegasmMode fallback) {
        if (s == null) {
            return fallback;
        }
        String key = s.trim().toUpperCase(Locale.ROOT);
        switch (key) {
            // Legacy names, kept working across the rename.
            case "NORMAL":
                return ACTION;
            case "MASOCHIST":
                return REACTION;
            case "HEDONIST":
                return IMMERSION;
            case "ACCUMULATION":
                return MOMENTUM;
            default:
                break;
        }
        try {
            return valueOf(key);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
