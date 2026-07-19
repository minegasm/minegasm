package net.minegasm.observe;

/**
 * Derives {@link StateTransitions} by comparing each snapshot to the previous one (brief §6.2, §7).
 * Handles the awkward edges: a respawn/dimension change is detected as a large positive health jump
 * or a world-ready edge and suppressed so it cannot masquerade as damage; mining target changes end
 * the old continuous key. Confined to the client thread.
 */
public final class StateTracker {

    private static final float CRITICAL_HEALTH = 6.0f;   // <= 3 hearts
    private static final float RESPAWN_JUMP = 8.0f;      // sudden large heal => respawn/init

    private ClientStateSnapshot previous;

    public StateTransitions update(ClientStateSnapshot current) {
        ClientStateSnapshot prev = previous;
        previous = current;

        if (prev == null || !prev.worldReady() || !current.worldReady()) {
            // First sample or world (re)load: establish a baseline, emit no damage/xp.
            return baseline(current);
        }

        float healthDelta = current.effectiveHealth() - prev.effectiveHealth();
        boolean respawn = healthDelta > RESPAWN_JUMP
                || (current.experienceLevel() == 0 && prev.experienceLevel() > 0
                    && current.totalExperience() == 0);

        int xpGained = Math.max(0, current.totalExperience() - prev.totalExperience());
        int levelsGained = Math.max(0, current.experienceLevel() - prev.experienceLevel());
        boolean leveledUp = levelsGained > 0;

        boolean miningStarted = current.mining() && !prev.mining();
        boolean miningStopped = !current.mining() && prev.mining();
        boolean targetChanged = current.mining() && prev.mining()
                && !current.miningTarget().equals(prev.miningTarget());

        boolean biteEdge = current.fishingBite() && !prev.fishingBite();

        boolean full = current.health() >= 20f && current.food() >= 20;
        boolean critical = current.health() > 0f && current.health() <= CRITICAL_HEALTH;

        return new StateTransitions(respawn ? 0f : healthDelta,
                respawn ? 0 : xpGained, leveledUp, levelsGained,
                miningStarted, miningStopped, targetChanged, biteEdge, full, critical, respawn);
    }

    public void reset() {
        previous = null;
    }

    private StateTransitions baseline(ClientStateSnapshot current) {
        boolean full = current.worldReady() && current.health() >= 20f && current.food() >= 20;
        boolean critical = current.worldReady() && current.health() > 0f
                && current.health() <= CRITICAL_HEALTH;
        return new StateTransitions(0f, 0, false, 0, false, false, false, false, full, critical, true);
    }
}
