package net.minegasm.config;

import net.minegasm.core.GameEventKind;
import net.minegasm.util.HapticMath;

import java.util.Objects;

/**
 * Per-event base intensities for CUSTOM mode and the target of a legacy Minegasm config import.
 * Values are normalized {@code [0, 1]} (legacy stored 0..100). Field names mirror the legacy
 * {@code *Intensity} settings for a transparent migration mapping (brief §3.5).
 */
public final class CustomIntensities implements ConfigValue {

    private final double attack;
    private final double hurt;
    private final double mine;
    private final double place;
    private final double xpChange;
    private final double fishing;
    private final double harvest;
    private final double vitality;
    private final double advancement;

    public CustomIntensities(
            double attack,
            double hurt,
            double mine,
            double place,
            double xpChange,
            double fishing,
            double harvest,
            double vitality,
            double advancement) {
        this.attack = c(attack);
        this.hurt = c(hurt);
        this.mine = c(mine);
        this.place = c(place);
        this.xpChange = c(xpChange);
        this.fishing = c(fishing);
        this.harvest = c(harvest);
        this.vitality = c(vitality);
        this.advancement = c(advancement);
    }

    public double attack() {
        return attack;
    }

    public double hurt() {
        return hurt;
    }

    public double mine() {
        return mine;
    }

    public double place() {
        return place;
    }

    public double xpChange() {
        return xpChange;
    }

    public double fishing() {
        return fishing;
    }

    public double harvest() {
        return harvest;
    }

    public double vitality() {
        return vitality;
    }

    public double advancement() {
        return advancement;
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
        switch (kind) {
            case ATTACK:
                return attack;
            case HURT:
                return hurt;
            case MINING_ACTIVE:
            case BLOCK_BROKEN:
                return mine;
            case PLACE:
                return place;
            case XP_GAIN:
                return xpChange;
            case FISHING_BITE:
                return fishing;
            case HARVEST:
                return harvest;
            case VITALITY:
                return vitality;
            case ADVANCEMENT:
                return advancement;
            case EXPLOSION:
                return hurt; // explosion borrows the damage channel in custom mode
            case AMBIENT:
                return 0.0;
            default:
                throw new IllegalStateException("Unhandled GameEventKind: " + kind);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CustomIntensities)) {
            return false;
        }
        CustomIntensities other = (CustomIntensities) o;
        return Double.compare(attack, other.attack) == 0
                && Double.compare(hurt, other.hurt) == 0
                && Double.compare(mine, other.mine) == 0
                && Double.compare(place, other.place) == 0
                && Double.compare(xpChange, other.xpChange) == 0
                && Double.compare(fishing, other.fishing) == 0
                && Double.compare(harvest, other.harvest) == 0
                && Double.compare(vitality, other.vitality) == 0
                && Double.compare(advancement, other.advancement) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(attack, hurt, mine, place, xpChange, fishing, harvest, vitality,
                advancement);
    }

    @Override
    public String toString() {
        return "CustomIntensities[attack=" + attack + ", hurt=" + hurt + ", mine=" + mine
                + ", place=" + place + ", xpChange=" + xpChange + ", fishing=" + fishing
                + ", harvest=" + harvest + ", vitality=" + vitality + ", advancement=" + advancement
                + "]";
    }
}
