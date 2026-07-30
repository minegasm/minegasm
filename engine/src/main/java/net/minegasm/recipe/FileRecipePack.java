package net.minegasm.recipe;

import net.minegasm.core.HapticLayer;
import net.minegasm.core.HapticPrimitive;
import net.minegasm.core.HapticScene;
import net.minegasm.pack.ScenePack;
import net.minegasm.util.HapticMath;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Renders a user-supplied {@link ScenePack} as a {@link RecipePack} (brief 0003 §2.4, Tier 1). The
 * pack's authored scene is materialized as-is, then scaled by the user's volume ({@code userGain} =
 * master intensity times the per-event multiplier) so the master slider still governs a file pack.
 * The mode preset's shaping is not applied: the author already chose the levels.
 *
 * <p>Scaling touches amplitude only. Grain, density, roughness, easing, and every duration are the
 * pack's feel and timing and are left untouched; multiplying them by a volume slider would corrupt
 * the effect. Levels are clamped after scaling because {@code userGain} can exceed 1.
 */
public final class FileRecipePack implements RecipePack {

    private final ScenePack pack;

    public FileRecipePack(ScenePack pack) {
        if (pack == null) {
            throw new IllegalArgumentException("pack required");
        }
        this.pack = pack;
    }

    @Override
    public String id() {
        return pack.packId();
    }

    @Override
    public Optional<HapticScene> resolve(RecipeContext ctx) {
        Optional<HapticScene> scene = pack.resolve(ctx.intent().kind(), ctx.nowNs());
        if (!scene.isPresent()) {
            return scene;
        }
        return Optional.of(scaleScene(scene.get(), ctx.userGain()));
    }

    private static HapticScene scaleScene(HapticScene s, float gain) {
        List<HapticLayer> layers = new ArrayList<>(s.layers().size());
        for (HapticLayer layer : s.layers()) {
            layers.add(scaleLayer(layer, gain));
        }
        return new HapticScene(s.sceneId(), s.kind(), s.priority(), layers, s.createdAtNs(),
                s.expiresAtNs(), s.continuousKey());
    }

    private static HapticLayer scaleLayer(HapticLayer l, float gain) {
        return new HapticLayer(l.layerId(), l.role(), scale(l.primitive(), gain), l.route(),
                l.coupling(), l.priority(), l.startOffsetNs(), l.expiresAfterNs(), l.coalesceKey());
    }

    /**
     * Scale a primitive's amplitude by {@code gain}, leaving character and timing untouched. The
     * trailing throw keeps this exhaustive so a new core primitive fails loudly rather than passing
     * through unscaled (mirrors PrimitiveEvaluator).
     */
    private static HapticPrimitive scale(HapticPrimitive p, float gain) {
        if (p instanceof HapticPrimitive.Impulse) {
            HapticPrimitive.Impulse i = (HapticPrimitive.Impulse) p;
            return new HapticPrimitive.Impulse(lvl(i.level(), gain), i.durationMs(), i.attackMs(),
                    i.releaseMs());
        } else if (p instanceof HapticPrimitive.Texture) {
            HapticPrimitive.Texture t = (HapticPrimitive.Texture) p;
            return new HapticPrimitive.Texture(lvl(t.level(), gain), t.durationMs(), t.grain(),
                    t.density(), t.irregularity());
        } else if (p instanceof HapticPrimitive.Rumble) {
            HapticPrimitive.Rumble r = (HapticPrimitive.Rumble) p;
            return new HapticPrimitive.Rumble(lvl(r.level(), gain), r.durationMs(), r.roughness(),
                    r.decay());
        } else if (p instanceof HapticPrimitive.Sweep) {
            HapticPrimitive.Sweep s = (HapticPrimitive.Sweep) p;
            return new HapticPrimitive.Sweep(lvl(s.from(), gain), lvl(s.to(), gain), s.durationMs(),
                    s.easing());
        } else if (p instanceof HapticPrimitive.BeatPattern) {
            HapticPrimitive.BeatPattern bp = (HapticPrimitive.BeatPattern) p;
            List<HapticPrimitive.Beat> beats = new ArrayList<>(bp.beats().size());
            for (HapticPrimitive.Beat b : bp.beats()) {
                beats.add(new HapticPrimitive.Beat(b.atMs(), lvl(b.level(), gain), b.durationMs()));
            }
            return new HapticPrimitive.BeatPattern(beats);
        } else if (p instanceof HapticPrimitive.Hold) {
            HapticPrimitive.Hold h = (HapticPrimitive.Hold) p;
            return new HapticPrimitive.Hold(lvl(h.level(), gain), h.durationMs(), h.fadeInMs(),
                    h.fadeOutMs());
        } else if (p instanceof HapticPrimitive.Oscillation) {
            HapticPrimitive.Oscillation o = (HapticPrimitive.Oscillation) p;
            return new HapticPrimitive.Oscillation(lvl(o.level(), gain), o.periodMs(), o.durationMs());
        }
        throw new IllegalStateException("Unknown HapticPrimitive: " + p);
    }

    private static float lvl(float level, float gain) {
        return HapticMath.clamp01(level * gain);
    }
}
