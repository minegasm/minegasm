package net.minegasm.core;

/**
 * Broad material classes for mining/placement texture, derived from block tags/properties rather
 * than a hard-coded block list (brief Â§7.4). Each class carries default texture-shaping parameters
 * (grain/density/roughness in 0..1) that recipes may reference.
 */
public enum MaterialFeel {
    STONE_ORE(0.55f, 0.75f, 0.45f),
    WOOD(0.35f, 0.55f, 0.30f),
    SOIL_CLAY(0.20f, 0.40f, 0.25f),
    SAND_GRAVEL(0.70f, 0.30f, 0.60f),
    METAL(0.80f, 0.85f, 0.20f),
    GLASS_CRYSTAL(0.90f, 0.60f, 0.15f),
    PLANTS_CROPS(0.15f, 0.25f, 0.35f),
    WOOL_SOFT(0.10f, 0.20f, 0.10f),
    LIQUID_OTHER(0.05f, 0.15f, 0.50f),
    UNKNOWN(0.40f, 0.50f, 0.30f);

    private final float grain;
    private final float density;
    private final float roughness;

    MaterialFeel(float grain, float density, float roughness) {
        this.grain = grain;
        this.density = density;
        this.roughness = roughness;
    }

    /** Coarseness of the texture (0 = smooth, 1 = coarse). */
    public float grain() {
        return grain;
    }

    /** How closely packed the texture pulses are (0 = sparse, 1 = dense). */
    public float density() {
        return density;
    }

    /** Irregularity/roughness used by rumble-like renderers. */
    public float roughness() {
        return roughness;
    }
}

