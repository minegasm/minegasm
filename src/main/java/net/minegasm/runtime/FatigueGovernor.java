package net.minegasm.runtime;

import net.minegasm.core.HapticRole;

import java.util.EnumMap;
import java.util.Map;

/**
 * Rolling fatigue budgets by role (brief §10.6). Accumulates level-seconds of output per role with
 * exponential decay (~15&nbsp;s half-life); when a continuous role exceeds its budget, low-priority
 * texture/ambient output is scaled down first, before important warning clarity is ever reduced.
 * Impact and warning roles are never attenuated.
 *
 * <p>Confined to the worker thread; not synchronised.
 */
public final class FatigueGovernor {

    private static final double HALF_LIFE_SECONDS = 15.0;
    private static final double DECAY_LAMBDA = Math.log(2) / HALF_LIFE_SECONDS;

    /** Budget in level-seconds before attenuation begins, per continuous role. */
    private static final Map<HapticRole, Double> BUDGET = Map.of(
            HapticRole.TEXTURE, 6.0,
            HapticRole.AMBIENT, 4.0);

    private final EnumMap<HapticRole, Double> load = new EnumMap<>(HapticRole.class);
    private long lastNs;
    private boolean initialised;

    /** Decay accumulated load up to {@code nowNs}. */
    public void update(long nowNs) {
        if (!initialised) {
            lastNs = nowNs;
            initialised = true;
            return;
        }
        double seconds = Math.max(0, nowNs - lastNs) / 1_000_000_000.0;
        lastNs = nowNs;
        if (seconds <= 0) {
            return;
        }
        double factor = Math.exp(-DECAY_LAMBDA * seconds);
        load.replaceAll((role, value) -> value * factor);
    }

    /** Attenuation factor (0..1) to apply to a role's level. 1.0 for non-continuous roles. */
    public float factor(HapticRole role) {
        Double budget = BUDGET.get(role);
        if (budget == null) {
            return 1f;
        }
        double current = load.getOrDefault(role, 0.0);
        if (current <= budget) {
            return 1f;
        }
        return (float) (budget / current);
    }

    /** Record achieved output for a role over {@code dtNs} (level-seconds). */
    public void record(HapticRole role, float level, long dtNs) {
        if (level <= 0f || dtNs <= 0 || !BUDGET.containsKey(role)) {
            return;
        }
        double seconds = dtNs / 1_000_000_000.0;
        load.merge(role, level * seconds, Double::sum);
    }

    public void reset() {
        load.clear();
        initialised = false;
    }

    public void shiftTime(long deltaNs) {
        if (initialised && deltaNs > 0) {
            lastNs += deltaNs;
        }
    }

    public double loadFor(HapticRole role) {
        return load.getOrDefault(role, 0.0);
    }
}
