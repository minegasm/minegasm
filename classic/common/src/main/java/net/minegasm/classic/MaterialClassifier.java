package net.minegasm.classic;

import net.minegasm.core.MaterialFeel;

import java.util.Locale;

/**
 * Maps a block's identifier (its registry name or translation key) to a {@link MaterialFeel}. This is
 * the name-based fallback the modern sampler also uses, lifted out so all three Classic versions share
 * one classifier instead of each carrying its own copy. It holds no Minecraft types; each version
 * passes in whatever string its API exposes for the block.
 */
public final class MaterialClassifier {

    private MaterialClassifier() {
    }

    public static MaterialFeel classify(String blockId) {
        if (blockId == null) {
            return MaterialFeel.UNKNOWN;
        }
        String id = blockId.toLowerCase(Locale.ROOT);
        if (id.contains("ore") || id.contains("stone") || id.contains("deepslate")) {
            return MaterialFeel.STONE_ORE;
        }
        if (id.contains("log") || id.contains("wood") || id.contains("plank")) {
            return MaterialFeel.WOOD;
        }
        if (id.contains("sand") || id.contains("gravel")) {
            return MaterialFeel.SAND_GRAVEL;
        }
        if (id.contains("dirt") || id.contains("clay") || id.contains("mud")) {
            return MaterialFeel.SOIL_CLAY;
        }
        if (id.contains("iron") || id.contains("copper") || id.contains("metal") || id.contains("gold")) {
            return MaterialFeel.METAL;
        }
        if (id.contains("glass") || id.contains("amethyst") || id.contains("crystal")) {
            return MaterialFeel.GLASS_CRYSTAL;
        }
        if (id.contains("wool") || id.contains("wave")) {
            return MaterialFeel.WOOL_SOFT;
        }
        if (id.contains("crop") || id.contains("wheat") || id.contains("leaves") || id.contains("plant")) {
            return MaterialFeel.PLANTS_CROPS;
        }
        return MaterialFeel.UNKNOWN;
    }

    public static boolean isOre(String blockId) {
        return blockId != null && blockId.toLowerCase(Locale.ROOT).contains("ore");
    }

    /** Normalize a raw block hardness to 0..1 the way the modern sampler does (~obsidian saturates). */
    public static float normalizedHardness(float rawHardness) {
        if (rawHardness < 0f) {
            return 0.4f;
        }
        return Math.max(0f, Math.min(1f, rawHardness / 5.0f));
    }
}
