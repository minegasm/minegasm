package gg.meza.feelcraft.core;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;

public enum MaterialFeel {
    STONE(0.65f, 0.55f, 0.10f), WOOD(0.45f, 0.35f, 0.12f), DIRT(0.35f, 0.45f, 0.20f),
    SAND(0.25f, 0.70f, 0.25f), GLASS(0.75f, 0.25f, 0.08f), METAL(0.85f, 0.40f, 0.06f),
    SOFT(0.20f, 0.20f, 0.20f), GENERIC(0.50f, 0.50f, 0.15f);

    public final float grain;
    public final float density;
    public final float irregularity;

    MaterialFeel(float grain, float density, float irregularity) {
        this.grain = grain;
        this.density = density;
        this.irregularity = irregularity;
    }

    public static MaterialFeel fromBlockState(BlockState state) {
        if (state == null) return GENERIC;
        if (state.is(BlockTags.LOGS) || state.is(BlockTags.PLANKS)) return WOOD;
        if (state.is(BlockTags.SAND)) return SAND;
        if (state.is(BlockTags.WOOL)) return SOFT;
        String key = state.getBlock().builtInRegistryHolder().key().location().getPath();
        if (key.contains("glass")) return GLASS;
        if (key.contains("iron") || key.contains("copper") || key.contains("gold") || key.contains("metal")) return METAL;
        if (key.contains("dirt") || key.contains("mud") || key.contains("gravel")) return DIRT;
        if (key.contains("stone") || key.contains("deepslate") || key.contains("ore")) return STONE;
        return GENERIC;
    }
}
