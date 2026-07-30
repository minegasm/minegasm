package net.minegasm.recipe;

import net.minegasm.util.HapticMath;

/**
 * Rhythmic-stroke drive for position/motion devices (Balanced pack). Gameplay events raise an activity
 * charge (0..1) that decays over <em>real</em> time; while it is above a small threshold the recipe emits
 * a continuous stroke scene whose depth and speed scale with the charge, so a stroker strokes during
 * activity and fades to rest when things go quiet. Bounded and thread-confined like
 * {@link AccumulationProcessor}.
 *
 * <p>The scene is re-offered every client tick (latest-wins), but a stroke has a slow phase that must
 * advance across those refreshes, so the driver holds a stable {@link #strokeOriginNs(long)} to use as
 * the scene's creation time. Resetting it only when activity lapses gives each new run a fresh phase.
 */
final class StrokeProcessor {

    private static final double DECAY_PER_SECOND = 0.6;   // fades ~1.6 s after activity stops
    private static final double CONTRIBUTION = 0.5;       // per event, scaled by strength
    private static final double ACTIVE_THRESHOLD = 0.02;

    private static final float MIN_DEPTH = 0.45f;         // as a fraction of the gameplay travel window
    private static final float MAX_DEPTH = 1.00f;
    private static final int SLOW_PERIOD_MS = 1400;
    private static final int FAST_PERIOD_MS = 700;

    private double charge;
    private long lastUpdateNs;
    private boolean initialised;
    private long strokeOriginNs;
    private boolean stroking;

    /** Decay the charge to {@code nowNs}; ends the current stroke run once it falls idle. */
    void update(long nowNs) {
        if (!initialised) {
            lastUpdateNs = nowNs;
            initialised = true;
            return;
        }
        long deltaNs = nowNs - lastUpdateNs;
        if (deltaNs > 0) {
            lastUpdateNs = nowNs;
            charge = Math.max(0.0, charge - DECAY_PER_SECOND * (deltaNs / 1_000_000_000.0));
        }
        if (charge <= ACTIVE_THRESHOLD) {
            stroking = false;
        }
    }

    /** Add an event's contribution (strength-scaled), clamped to 1. */
    void contribute(float strength) {
        charge = Math.min(1.0, charge + CONTRIBUTION * HapticMath.clamp01(strength));
    }

    boolean active() {
        return charge > ACTIVE_THRESHOLD;
    }

    /** Stable phase origin for the current stroke run; (re)starts at {@code nowNs} when activity resumes. */
    long strokeOriginNs(long nowNs) {
        if (!stroking) {
            strokeOriginNs = nowNs;
            stroking = true;
        }
        return strokeOriginNs;
    }

    /** Stroke depth as a fraction of the gameplay travel window (0..1). */
    float depth() {
        return MIN_DEPTH + (MAX_DEPTH - MIN_DEPTH) * HapticMath.clamp01((float) charge);
    }

    /** Full out-and-back period in ms; shorter (faster) with more activity. */
    int periodMs() {
        float c = HapticMath.clamp01((float) charge);
        return Math.round(SLOW_PERIOD_MS + (FAST_PERIOD_MS - SLOW_PERIOD_MS) * c);
    }

    double charge() {
        return charge;
    }

    void reset() {
        charge = 0.0;
        initialised = false;
        stroking = false;
    }
}
