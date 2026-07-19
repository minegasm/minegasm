package net.minegasm.observe;

/**
 * The change between two consecutive {@link ClientStateSnapshot}s (brief §6.2). Continuous effects
 * start/update/stop from these transitions rather than being re-enqueued every tick.
 */
public record StateTransitions(
        float effectiveHealthDelta,
        int xpGained,
        boolean leveledUp,
        int levelsGained,
        boolean miningStarted,
        boolean miningStopped,
        boolean miningTargetChanged,
        boolean fishingBiteEdge,
        boolean vitalityFull,
        boolean vitalityCritical,
        boolean respawnOrInit) {

    public boolean tookDamage() {
        return effectiveHealthDelta < 0 && !respawnOrInit;
    }

    public boolean gainedXp() {
        return xpGained > 0 && !respawnOrInit;
    }
}
