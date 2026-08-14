package net.minegasm.runtime;

import net.minegasm.core.BodyRegion;
import net.minegasm.core.HapticRole;

import java.util.EnumMap;
import java.util.Map;

/**
 * Rolling fatigue budgets by role and body region (brief §10.6). Accumulates level-seconds of output
 * with exponential decay (~15&nbsp;s half-life). When a continuous destination exceeds its budget,
 * texture or ambient output is scaled down before important warning clarity is reduced.
 * Impact and warning roles are never attenuated.
 *
 * <p>Confined to the worker thread; not synchronised.
 */
public final class FatigueGovernor {

    private static final double HALF_LIFE_SECONDS = 15.0;
    private static final double DECAY_LAMBDA = Math.log(2) / HALF_LIFE_SECONDS;

    /** Budget in level-seconds before attenuation begins, independently applied to each region. */
    private static final Map<HapticRole, Double> BUDGET = budgetTable();

    private static Map<HapticRole, Double> budgetTable() {
        Map<HapticRole, Double> budget = new java.util.EnumMap<>(HapticRole.class);
        budget.put(HapticRole.TEXTURE, 6.0);
        budget.put(HapticRole.AMBIENT, 4.0);
        return java.util.Collections.unmodifiableMap(budget);
    }

    private final EnumMap<HapticRole, EnumMap<BodyRegion, Double>> load =
            new EnumMap<>(HapticRole.class);
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
        for (EnumMap<BodyRegion, Double> byRegion : load.values()) {
            byRegion.replaceAll((region, value) -> value * factor);
        }
    }

    /** Attenuation factor (0..1) to apply to a role's level. 1.0 for non-continuous roles. */
    public float factor(HapticRole role) {
        return factor(role, BodyRegion.WHOLE_BODY);
    }

    /** Attenuation for one independently budgeted body destination. */
    public float factor(HapticRole role, BodyRegion region) {
        Double budget = BUDGET.get(role);
        if (budget == null) {
            return 1f;
        }
        EnumMap<BodyRegion, Double> byRegion = load.get(role);
        double current = byRegion == null ? 0.0 : byRegion.getOrDefault(region, 0.0);
        if (current <= budget) {
            return 1f;
        }
        return (float) (budget / current);
    }

    /** Record achieved output for a role over {@code dtNs} (level-seconds). */
    public void record(HapticRole role, float level, long dtNs) {
        record(role, BodyRegion.WHOLE_BODY, level, dtNs);
    }

    /** Record achieved output for one role and region over {@code dtNs} (level-seconds). */
    public void record(HapticRole role, BodyRegion region, float level, long dtNs) {
        if (level <= 0f || dtNs <= 0 || !BUDGET.containsKey(role)) {
            return;
        }
        double seconds = dtNs / 1_000_000_000.0;
        EnumMap<BodyRegion, Double> byRegion = load.get(role);
        if (byRegion == null) {
            byRegion = new EnumMap<>(BodyRegion.class);
            load.put(role, byRegion);
        }
        byRegion.merge(region, level * seconds, Double::sum);
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
        return loadFor(role, BodyRegion.WHOLE_BODY);
    }

    public double loadFor(HapticRole role, BodyRegion region) {
        EnumMap<BodyRegion, Double> byRegion = load.get(role);
        return byRegion == null ? 0.0 : byRegion.getOrDefault(region, 0.0);
    }
}
