package net.minegasm.observe;

import net.minegasm.core.MaterialFeel;

import java.util.Objects;
import java.util.Optional;

/**
 * A once-per-client-tick sample of continuous player state (brief §6.2 structure B). Device- and
 * Minecraft-independent: the observation adapter fills it from client-visible state, so the same
 * fields work in singleplayer and on an unmodified multiplayer server. Positions/block ids are
 * strings to keep this type free of Minecraft classes.
 */
public final class ClientStateSnapshot {

    private final float health;
    private final float absorption;
    private final int food;
    private final int experienceLevel;
    private final float experienceProgress;
    private final int totalExperience;
    private final boolean mining;
    private final Optional<String> miningTarget;
    private final float miningProgress;
    private final Optional<String> miningBlock;
    private final MaterialFeel miningMaterial;
    private final float miningHardness;
    private final boolean onFire;
    private final boolean underwater;
    private final boolean fishingActive;
    private final boolean fishingBite;
    private final boolean paused;
    private final boolean worldReady;
    private final long gameTick;

    public ClientStateSnapshot(
            float health,
            float absorption,
            int food,
            int experienceLevel,
            float experienceProgress,
            int totalExperience,
            boolean mining,
            Optional<String> miningTarget,
            float miningProgress,
            Optional<String> miningBlock,
            MaterialFeel miningMaterial,
            float miningHardness,
            boolean onFire,
            boolean underwater,
            boolean fishingActive,
            boolean fishingBite,
            boolean paused,
            boolean worldReady,
            long gameTick) {
        this.health = health;
        this.absorption = absorption;
        this.food = food;
        this.experienceLevel = experienceLevel;
        this.experienceProgress = experienceProgress;
        this.totalExperience = totalExperience;
        this.mining = mining;
        this.miningTarget = miningTarget == null ? Optional.empty() : miningTarget;
        this.miningProgress = miningProgress;
        this.miningBlock = miningBlock == null ? Optional.empty() : miningBlock;
        this.miningMaterial = miningMaterial == null ? MaterialFeel.UNKNOWN : miningMaterial;
        this.miningHardness = miningHardness;
        this.onFire = onFire;
        this.underwater = underwater;
        this.fishingActive = fishingActive;
        this.fishingBite = fishingBite;
        this.paused = paused;
        this.worldReady = worldReady;
        this.gameTick = gameTick;
    }

    public float health() {
        return health;
    }

    public float absorption() {
        return absorption;
    }

    public int food() {
        return food;
    }

    public int experienceLevel() {
        return experienceLevel;
    }

    public float experienceProgress() {
        return experienceProgress;
    }

    public int totalExperience() {
        return totalExperience;
    }

    public boolean mining() {
        return mining;
    }

    public Optional<String> miningTarget() {
        return miningTarget;
    }

    public float miningProgress() {
        return miningProgress;
    }

    public Optional<String> miningBlock() {
        return miningBlock;
    }

    public MaterialFeel miningMaterial() {
        return miningMaterial;
    }

    public float miningHardness() {
        return miningHardness;
    }

    public boolean onFire() {
        return onFire;
    }

    public boolean underwater() {
        return underwater;
    }

    public boolean fishingActive() {
        return fishingActive;
    }

    public boolean fishingBite() {
        return fishingBite;
    }

    public boolean paused() {
        return paused;
    }

    public boolean worldReady() {
        return worldReady;
    }

    public long gameTick() {
        return gameTick;
    }

    /** Effective health = health + absorption; the quantity hurt detection tracks (brief §7.3). */
    public float effectiveHealth() {
        return health + absorption;
    }

    /** A neutral "not in world" snapshot used before a world is ready. */
    public static ClientStateSnapshot empty(long gameTick) {
        return new ClientStateSnapshot(20f, 0f, 20, 0, 0f, 0, false, Optional.empty(), 0f,
                Optional.empty(), MaterialFeel.UNKNOWN, 0f, false, false, false, false, false,
                false, gameTick);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ClientStateSnapshot)) {
            return false;
        }
        ClientStateSnapshot other = (ClientStateSnapshot) o;
        return Float.compare(health, other.health) == 0
                && Float.compare(absorption, other.absorption) == 0
                && food == other.food
                && experienceLevel == other.experienceLevel
                && Float.compare(experienceProgress, other.experienceProgress) == 0
                && totalExperience == other.totalExperience
                && mining == other.mining
                && Float.compare(miningProgress, other.miningProgress) == 0
                && Float.compare(miningHardness, other.miningHardness) == 0
                && onFire == other.onFire
                && underwater == other.underwater
                && fishingActive == other.fishingActive
                && fishingBite == other.fishingBite
                && paused == other.paused
                && worldReady == other.worldReady
                && gameTick == other.gameTick
                && Objects.equals(miningTarget, other.miningTarget)
                && Objects.equals(miningBlock, other.miningBlock)
                && miningMaterial == other.miningMaterial;
    }

    @Override
    public int hashCode() {
        return Objects.hash(health, absorption, food, experienceLevel, experienceProgress,
                totalExperience, mining, miningTarget, miningProgress, miningBlock, miningMaterial,
                miningHardness, onFire, underwater, fishingActive, fishingBite, paused, worldReady,
                gameTick);
    }

    @Override
    public String toString() {
        return "ClientStateSnapshot[health=" + health + ", absorption=" + absorption + ", food=" + food
                + ", experienceLevel=" + experienceLevel + ", experienceProgress=" + experienceProgress
                + ", totalExperience=" + totalExperience + ", mining=" + mining
                + ", miningTarget=" + miningTarget + ", miningProgress=" + miningProgress
                + ", miningBlock=" + miningBlock + ", miningMaterial=" + miningMaterial
                + ", miningHardness=" + miningHardness + ", onFire=" + onFire
                + ", underwater=" + underwater + ", fishingActive=" + fishingActive
                + ", fishingBite=" + fishingBite + ", paused=" + paused + ", worldReady=" + worldReady
                + ", gameTick=" + gameTick + "]";
    }
}
