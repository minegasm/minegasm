package net.minegasm.config;

import net.minegasm.core.GameEventKind;
import net.minegasm.util.HapticMath;

/**
 * Per-event base intensities for CUSTOM mode and the target of a legacy Minegasm config import.
 * Values are normalized {@code [0, 1]} (legacy stored 0..100). Field names mirror the legacy
 * {@code *Intensity} settings for a transparent migration mapping (brief §3.5).
 */
public record CustomIntensities(
        double attack,
        double hurt,
        double mine,
        double place,
        double xpChange,
        double fishing,
        double harvest,
        double vitality,
        double advancement) {

    public CustomIntensities {
        attack = c(attack);
        hurt = c(hurt);
        mine = c(mine);
        place = c(place);
        xpChange = c(xpChange);
        fishing = c(fishing);
        harvest = c(harvest);
        vitality = c(vitality);
        advancement = c(advancement);
    }

    private static double c(double v) {
        return HapticMath.clamp(v, 0.0, 1.0);
    }

    /** Legacy Minegasm CUSTOM-mode defaults (0..100 → normalized): attack 60, mine 80, place 20,
     * xp 100, fishing 50, advancement 100; hurt/harvest/vitality 0 (legacy ClientConfig defaults). */
    public static CustomIntensities legacyDefaults() {
        return new CustomIntensities(0.60, 0.0, 0.80, 0.20, 1.00, 0.50, 0.0, 0.0, 1.00);
    }

    /** Look up the base intensity for an event kind (0 for events without a custom slot). */
    public double forEvent(GameEventKind kind) {
        return switch (kind) {
            case ATTACK -> attack;
            case HURT -> hurt;
            case MINING_ACTIVE, BLOCK_BROKEN -> mine;
            case PLACE -> place;
            case XP_GAIN -> xpChange;
            case FISHING_BITE -> fishing;
            case HARVEST -> harvest;
            case VITALITY -> vitality;
            case ADVANCEMENT -> advancement;
            case EXPLOSION -> hurt; // explosion borrows the damage channel in custom mode
            case AMBIENT -> 0.0;
        };
    }
}
