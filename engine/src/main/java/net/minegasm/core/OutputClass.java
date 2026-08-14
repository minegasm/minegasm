package net.minegasm.core;

import java.util.EnumSet;
import java.util.Set;

/**
 * Device-neutral output families. Outputs in different families do not compete for one logical
 * destination even when their role and body region match.
 */
public enum OutputClass {
    STRENGTH,
    MOTION,
    CONSTRICTION,
    THERMAL,
    LIGHT,
    UNKNOWN;

    public static OutputClass of(OutputKind kind) {
        switch (kind) {
            case VIBRATE:
            case OSCILLATE:
            case ROTATE:
                return STRENGTH;
            case POSITION:
            case HW_POSITION_WITH_DURATION:
                return MOTION;
            case CONSTRICT:
                return CONSTRICTION;
            case TEMPERATURE:
                return THERMAL;
            case LED:
                return LIGHT;
            case UNKNOWN:
            default:
                return UNKNOWN;
        }
    }

    public static EnumSet<OutputClass> ofKinds(Set<OutputKind> kinds) {
        EnumSet<OutputClass> classes = EnumSet.noneOf(OutputClass.class);
        for (OutputKind kind : kinds) {
            classes.add(of(kind));
        }
        return classes;
    }
}
