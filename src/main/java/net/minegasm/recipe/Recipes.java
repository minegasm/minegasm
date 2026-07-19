package net.minegasm.recipe;

import net.minegasm.core.CouplingMode;
import net.minegasm.core.GameEventKind;
import net.minegasm.core.HapticLayer;
import net.minegasm.core.HapticPrimitive;
import net.minegasm.core.HapticRole;
import net.minegasm.core.HapticRoute;
import net.minegasm.core.HapticScene;

import java.util.List;

/** Small builders shared by the recipe packs to keep them declarative. */
final class Recipes {

    private Recipes() {}

    static long ms(long millis) {
        return millis * 1_000_000L;
    }

    static HapticLayer layer(String id, HapticRole role, HapticPrimitive primitive,
                             HapticRoute route, CouplingMode coupling, int priority,
                             long startOffsetNs, long expiresAfterNs, String coalesceKey) {
        return new HapticLayer(id, role, primitive, route, coupling, priority,
                startOffsetNs, expiresAfterNs, coalesceKey);
    }

    /**
     * Build a scene for the given context. Scene id is derived from the event key and game tick so
     * repeated events on the same tick coalesce naturally. Expiry uses the timing table unless an
     * explicit override is provided (Classic reproduces longer legacy durations).
     */
    static HapticScene scene(RecipeContext ctx, String suffix, List<HapticLayer> layers,
                             String continuousKey, long expiryNsOverride) {
        GameEventKind kind = ctx.intent().kind();
        RecipeTiming timing = RecipeTiming.forKind(kind);
        long created = ctx.nowNs();
        long expiry = expiryNsOverride > 0 ? expiryNsOverride : timing.expiryNs();
        String id = kind.key() + "@" + ctx.intent().gameTick() + (suffix.isEmpty() ? "" : ":" + suffix);
        return new HapticScene(id, kind, timing.priority(), layers, created, created + expiry,
                continuousKey);
    }

    static HapticScene scene(RecipeContext ctx, List<HapticLayer> layers) {
        return scene(ctx, "", layers, null, 0);
    }
}
