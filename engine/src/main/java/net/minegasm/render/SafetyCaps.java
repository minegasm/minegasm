package net.minegasm.render;

import net.minegasm.core.OutputKind;

/**
 * Hard normalized-level caps per output kind (brief §12.1). Absolute ceilings applied after all user
 * scaling, so a bug upstream can never push an output above these. The values are stronger than the
 * original conservative scaffold defaults (the scaffold was weak enough that gameplay barely registered)
 * but still ordered by risk: constriction (physical squeezing pressure) stays the most cautious, then
 * rotation, then oscillation, with vibration free to the device's own max. Motion travel is additionally
 * bounded by calibration.
 */
public final class SafetyCaps {

    private SafetyCaps() {}

    /**
     * Whether {@code kind} is a vibration-class strength (a motor level), as opposed to a position or
     * travel coordinate. The per-device minimum-strength floor applies only to these; flooring a
     * position would push a stroker off its neutral.
     */
    public static boolean isStrengthKind(OutputKind kind) {
        switch (kind) {
            case VIBRATE:
            case OSCILLATE:
            case ROTATE:
            case CONSTRICT:
                return true;
            default:
                return false;
        }
    }

    public static float cap(OutputKind kind) {
        switch (kind) {
            case VIBRATE:
                return 1.00f;
            case OSCILLATE:
                return 0.90f;
            case ROTATE:
                return 0.75f;
            case CONSTRICT:
                return 0.60f;
            case POSITION:
            case HW_POSITION_WITH_DURATION:
                // Level passes through here; physical travel is bounded in SceneMixer.buildTarget by the
                // calibration's gameplayTravelFraction (<= 0.20) and the [minimum, maximum] clamp, so this
                // must not also act as a travel fraction or the two would compound to a ~4% ceiling.
                return 1.00f;
            case TEMPERATURE:
            case LED:
            case UNKNOWN: // not driven by gameplay in the MVP
                return 0.00f;
            default:
                throw new IllegalStateException("Unhandled OutputKind: " + kind);
        }
    }
}
