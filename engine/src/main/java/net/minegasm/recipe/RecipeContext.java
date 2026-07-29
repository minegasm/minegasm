package net.minegasm.recipe;

import net.minegasm.config.RuntimeConfig;
import net.minegasm.core.HapticIntent;
import net.minegasm.util.HapticMath;

import java.util.Objects;

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
public final class RecipeContext {

    private final HapticIntent intent;
    private final float modeBase;
    private final float userGain;
    private final RuntimeConfig config;
    private final long nowNs;

    public RecipeContext(HapticIntent intent, float modeBase, float userGain, RuntimeConfig config,
                         long nowNs) {
        this.intent = intent;
        this.modeBase = modeBase;
        this.userGain = userGain;
        this.config = config;
        this.nowNs = nowNs;
    }

    public HapticIntent intent() {
        return intent;
    }

    public float modeBase() {
        return modeBase;
    }

    public float userGain() {
        return userGain;
    }

    public RuntimeConfig config() {
        return config;
    }

    public long nowNs() {
        return nowNs;
    }

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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RecipeContext)) {
            return false;
        }
        RecipeContext other = (RecipeContext) o;
        return Float.compare(modeBase, other.modeBase) == 0
                && Float.compare(userGain, other.userGain) == 0
                && nowNs == other.nowNs
                && Objects.equals(intent, other.intent)
                && Objects.equals(config, other.config);
    }

    @Override
    public int hashCode() {
        return Objects.hash(intent, modeBase, userGain, config, nowNs);
    }

    @Override
    public String toString() {
        return "RecipeContext[intent=" + intent + ", modeBase=" + modeBase + ", userGain=" + userGain
                + ", config=" + config + ", nowNs=" + nowNs + "]";
    }
}
