package net.minegasm.recipe;

import net.minegasm.config.RuntimeConfig;
import net.minegasm.core.HapticIntent;
import net.minegasm.util.HapticMath;

/**
 * Everything a {@link RecipePack} needs to turn one intent into a scene.
 *
 * <ul>
 *   <li>{@code modeBase}: the preset's per-event base intensity (0..1), "how much this mode wants
 *       this event felt".</li>
 *   <li>{@code userGain}: user scaling, event multiplier × global intensity.</li>
 * </ul>
 *
 * A pack computes its amplitude as {@code modeBase × shape(intent) × userGain}, where {@code shape}
 * is the pack's magnitude shaping (flat for Classic, catalog curves for Balanced).
 */
public record RecipeContext(
        HapticIntent intent,
        float modeBase,
        float userGain,
        RuntimeConfig config,
        long nowNs) {

    /** Amplitude for a given magnitude shape in {@code [0, 1]}. */
    public float amplitude(float shape) {
        return HapticMath.clamp01(modeBase * HapticMath.clamp01(shape) * userGain);
    }

    /** Stable seed for deterministic variation from this event's context (brief §8.4). */
    public long variationSeed() {
        return HapticMath.variationSeed(intent.eventKey(), intent.gameTick(),
                intent.tags().hashCode());
    }

    /** Configured variation fraction (0 disables variation). */
    public float variation() {
        return config.variation();
    }
}
