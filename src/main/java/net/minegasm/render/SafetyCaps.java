package net.minegasm.render;

import net.minegasm.core.OutputKind;

/**
 * Hard normalized-level caps per output kind (brief §12.1, matching the conservative scaffold
 * defaults). These are absolute ceilings applied after all user scaling — a bug upstream can never
 * push an output above these. Motion travel is additionally bounded by calibration.
 */
public final class SafetyCaps {

    private SafetyCaps() {}

    public static float cap(OutputKind kind) {
        return switch (kind) {
            case VIBRATE -> 1.00f;
            case OSCILLATE -> 0.50f;
            case ROTATE -> 0.35f;
            case CONSTRICT -> 0.30f;
            case POSITION, HW_POSITION_WITH_DURATION -> 0.20f; // travel fraction ceiling
            case TEMPERATURE, LED, UNKNOWN -> 0.00f; // not driven by gameplay in the MVP
        };
    }
}
