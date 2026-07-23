package net.minegasm.core;

/**
 * The device-independent semantic categories of gameplay observations. This is the vocabulary the
 * Minecraft observation layer emits and the recipe resolver keys on. It intentionally covers the
 * full Minegasm parity surface (brief §3.2) plus a few modern enhancements (mining texture,
 * block-break completion, explosion) kept separate so parity stays clean (ADR-009).
 */
public enum GameEventKind {
    /** Local player initiated a valid attack against an entity. */
    ATTACK,
    /** Local player's effective health decreased. */
    HURT,
    /** Continuous state: local player is actively damaging a block. */
    MINING_ACTIVE,
    /** Discrete completion: a targeted block became air/replacement, attributable to the player. */
    BLOCK_BROKEN,
    /** Local player successfully placed a block. */
    PLACE,
    /** Local player harvested a mature crop / harvestable block. */
    HARVEST,
    /** Local player's fishing bobber registered a bite. */
    FISHING_BITE,
    /** Local player's experience increased. */
    XP_GAIN,
    /** Local player earned an advancement. */
    ADVANCEMENT,
    /** Derived status condition (full vitality or critical health, mode-dependent). */
    VITALITY,
    /** A nearby explosion shock (modern enhancement, not strict legacy parity). */
    EXPLOSION,
    /** Reserved ambient/environment channel (footsteps etc.), disabled by default. */
    AMBIENT;

    /**
     * Stable snake_case key used in recipes, coalescing keys, and logs.
     */
    public String key() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * camelCase key used in the user-facing config file's {@code events} map (matches
     * {@code config.example.yaml} and the legacy Minegasm setting names where they overlap).
     */
    public String configKey() {
        return switch (this) {
            case ATTACK -> "attack";
            case HURT -> "hurt";
            case MINING_ACTIVE -> "mining";
            case BLOCK_BROKEN -> "blockBreak";
            case PLACE -> "place";
            case HARVEST -> "harvest";
            case FISHING_BITE -> "fishingBite";
            case XP_GAIN -> "xp";
            case ADVANCEMENT -> "advancement";
            case VITALITY -> "vitality";
            case EXPLOSION -> "explosion";
            case AMBIENT -> "ambient";
        };
    }
}

