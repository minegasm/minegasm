package net.minegasm.observe;

import net.minegasm.core.MaterialFeel;

import java.util.Optional;

/**
 * A once-per-client-tick sample of continuous player state (brief §6.2 structure B). Device- and
 * Minecraft-independent: the observation adapter fills it from client-visible state, so the same
 * fields work in singleplayer and on an unmodified multiplayer server. Positions/block ids are
 * strings to keep this type free of Minecraft classes.
 */
public record ClientStateSnapshot(
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

    public ClientStateSnapshot {
        miningTarget = miningTarget == null ? Optional.empty() : miningTarget;
        miningBlock = miningBlock == null ? Optional.empty() : miningBlock;
        miningMaterial = miningMaterial == null ? MaterialFeel.UNKNOWN : miningMaterial;
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
}
