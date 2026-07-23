package net.minegasm.recipe;

import net.minegasm.config.AccumulationParams;
import net.minegasm.core.GameEventKind;
import net.minegasm.util.HapticMath;

/**
 * Accumulation-mode charge processor (brief §11.5). Gameplay events add charge; charge decays over
 * <em>real</em> time (never tick counts) and the output level is a curve of {@code charge/capacity}.
 * The accumulator is bounded (charge never exceeds capacity) and thread-confined to the worker.
 *
 * <p>Legacy Minegasm used a hold-then-step decay; this uses the brief's continuous real-time decay,
 * with the same contribution weights, which is smoother and cannot grow unbounded.
 */
public final class AccumulationProcessor {

    private double charge;
    private long lastUpdateNs;
    private boolean initialised;

    /** Decay charge to {@code nowNs} using the configured decay rate. */
    public void update(AccumulationParams params, long nowNs) {
        if (!initialised) {
            lastUpdateNs = nowNs;
            initialised = true;
            return;
        }
        long deltaNs = nowNs - lastUpdateNs;
        if (deltaNs <= 0) {
            return;
        }
        lastUpdateNs = nowNs;
        double seconds = deltaNs / 1_000_000_000.0;
        charge = Math.max(0.0, charge - params.decayPerSecond() * seconds);
    }

    /** Add an event's contribution, clamped to capacity. */
    public void contribute(AccumulationParams params, GameEventKind kind, boolean ore, float strength) {
        double delta = switch (kind) {
            case ATTACK -> params.contribution("attack");
            case HURT, EXPLOSION -> params.contribution("hurt");
            case BLOCK_BROKEN -> ore ? params.contribution("oreBreak") : params.contribution("blockBreak");
            case PLACE -> params.contribution("place");
            case XP_GAIN -> params.contribution("xpPerFive") * (0.5 + 4.5 * HapticMath.clamp01(strength));
            case ADVANCEMENT -> params.contribution("advancement");
            case HARVEST -> params.contribution("harvest");
            case FISHING_BITE -> params.contribution("fishing");
            case MINING_ACTIVE, VITALITY, AMBIENT -> 0.0;
        };
        charge = Math.min(params.capacity(), charge + delta);
    }

    /** Current output level in {@code [0, 1]} after applying the configured curve. */
    public float level(AccumulationParams params) {
        float ratio = (float) HapticMath.clamp(charge / params.capacity(), 0.0, 1.0);
        return switch (params.outputCurve()) {
            case "smoothstep" -> HapticMath.smoothstep(ratio);
            case "linear" -> ratio;
            case "square" -> ratio * ratio;
            default -> HapticMath.smoothstep(ratio);
        };
    }

    public double charge() {
        return charge;
    }

    public void reset() {
        charge = 0.0;
        initialised = false;
    }
}
