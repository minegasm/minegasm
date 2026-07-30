package net.minegasm.render;

import net.minegasm.core.OutputKind;

/**
 * Hard normalized-level caps per output kind (brief §12.1, matching the conservative scaffold
 * defaults). These are absolute ceilings applied after all user scaling; a bug upstream can never
 * push an output above these. Motion travel is additionally bounded by calibration.
 */
public final class SafetyCaps {

    private SafetyCaps() {}

    public static float cap(OutputKind kind) {
        switch (kind) {
            case VIBRATE:
                return 1.00f;
            case OSCILLATE:
                return 0.50f;
            case ROTATE:
                return 0.35f;
            case CONSTRICT:
                return 0.30f;
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
