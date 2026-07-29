package net.minegasm.observe;

import java.util.Objects;

/**
 * The change between two consecutive {@link ClientStateSnapshot}s (brief §6.2). Continuous effects
 * start/update/stop from these transitions rather than being re-enqueued every tick.
 */
public final class StateTransitions {

    private final float effectiveHealthDelta;
    private final int xpGained;
    private final boolean leveledUp;
    private final int levelsGained;
    private final boolean miningStarted;
    private final boolean miningStopped;
    private final boolean miningTargetChanged;
    private final boolean fishingBiteEdge;
    private final boolean vitalityFull;
    private final boolean vitalityCritical;
    private final boolean respawnOrInit;

    public StateTransitions(
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
        this.effectiveHealthDelta = effectiveHealthDelta;
        this.xpGained = xpGained;
        this.leveledUp = leveledUp;
        this.levelsGained = levelsGained;
        this.miningStarted = miningStarted;
        this.miningStopped = miningStopped;
        this.miningTargetChanged = miningTargetChanged;
        this.fishingBiteEdge = fishingBiteEdge;
        this.vitalityFull = vitalityFull;
        this.vitalityCritical = vitalityCritical;
        this.respawnOrInit = respawnOrInit;
    }

    public float effectiveHealthDelta() {
        return effectiveHealthDelta;
    }

    public int xpGained() {
        return xpGained;
    }

    public boolean leveledUp() {
        return leveledUp;
    }

    public int levelsGained() {
        return levelsGained;
    }

    public boolean miningStarted() {
        return miningStarted;
    }

    public boolean miningStopped() {
        return miningStopped;
    }

    public boolean miningTargetChanged() {
        return miningTargetChanged;
    }

    public boolean fishingBiteEdge() {
        return fishingBiteEdge;
    }

    public boolean vitalityFull() {
        return vitalityFull;
    }

    public boolean vitalityCritical() {
        return vitalityCritical;
    }

    public boolean respawnOrInit() {
        return respawnOrInit;
    }

    public boolean tookDamage() {
        return effectiveHealthDelta < 0 && !respawnOrInit;
    }

    public boolean gainedXp() {
        return xpGained > 0 && !respawnOrInit;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof StateTransitions)) {
            return false;
        }
        StateTransitions other = (StateTransitions) o;
        return Float.compare(effectiveHealthDelta, other.effectiveHealthDelta) == 0
                && xpGained == other.xpGained
                && leveledUp == other.leveledUp
                && levelsGained == other.levelsGained
                && miningStarted == other.miningStarted
                && miningStopped == other.miningStopped
                && miningTargetChanged == other.miningTargetChanged
                && fishingBiteEdge == other.fishingBiteEdge
                && vitalityFull == other.vitalityFull
                && vitalityCritical == other.vitalityCritical
                && respawnOrInit == other.respawnOrInit;
    }

    @Override
    public int hashCode() {
        return Objects.hash(effectiveHealthDelta, xpGained, leveledUp, levelsGained, miningStarted,
                miningStopped, miningTargetChanged, fishingBiteEdge, vitalityFull, vitalityCritical,
                respawnOrInit);
    }

    @Override
    public String toString() {
        return "StateTransitions[effectiveHealthDelta=" + effectiveHealthDelta + ", xpGained=" + xpGained
                + ", leveledUp=" + leveledUp + ", levelsGained=" + levelsGained
                + ", miningStarted=" + miningStarted + ", miningStopped=" + miningStopped
                + ", miningTargetChanged=" + miningTargetChanged + ", fishingBiteEdge=" + fishingBiteEdge
                + ", vitalityFull=" + vitalityFull + ", vitalityCritical=" + vitalityCritical
                + ", respawnOrInit=" + respawnOrInit + "]";
    }
}
