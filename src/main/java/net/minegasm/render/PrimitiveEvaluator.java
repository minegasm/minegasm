package net.minegasm.render;

import net.minegasm.core.HapticPrimitive;
import net.minegasm.util.HapticMath;

/**
 * Evaluates the instantaneous level (0..1) of a {@link HapticPrimitive} at a given elapsed time.
 * All shaping uses monotonic elapsed nanoseconds so behaviour is identical regardless of Minecraft
 * tick rate or client stalls (brief §6.1). Past its duration a primitive evaluates to 0, which is
 * how the worker knows to send the endpoint's zero/neutral transition.
 */
public final class PrimitiveEvaluator {

    private PrimitiveEvaluator() {}

    public static float levelAt(HapticPrimitive primitive, long elapsedNs) {
        if (elapsedNs < 0) {
            return 0f;
        }
        double ms = elapsedNs / 1_000_000.0;
        // instanceof chain rather than a switch over the sealed type: switch type patterns are a
        // Java 21 feature, and this loader-agnostic core also compiles under Java 17 for the 1.20.1
        // variants. The trailing throw keeps the exhaustiveness the switch gave: a new HapticPrimitive
        // subtype fails loudly here instead of silently returning 0.
        if (primitive instanceof HapticPrimitive.Impulse i) {
            return impulse(i, ms);
        } else if (primitive instanceof HapticPrimitive.Texture t) {
            return texture(t, ms);
        } else if (primitive instanceof HapticPrimitive.Rumble r) {
            return rumble(r, ms);
        } else if (primitive instanceof HapticPrimitive.Sweep s) {
            return sweep(s, ms);
        } else if (primitive instanceof HapticPrimitive.BeatPattern b) {
            return beat(b, ms);
        } else if (primitive instanceof HapticPrimitive.Hold h) {
            return hold(h, ms);
        }
        throw new IllegalStateException("Unknown HapticPrimitive: " + primitive);
    }

    private static float impulse(HapticPrimitive.Impulse i, double ms) {
        double dur = i.durationMs();
        if (ms >= dur) {
            return 0f;
        }
        double attack = Math.min(i.attackMs(), dur);
        double release = Math.min(i.releaseMs(), dur - attack);
        double sustainEnd = dur - release;
        if (ms < attack) {
            return scale(i.level(), attack <= 0 ? 1.0 : ms / attack);
        }
        if (ms < sustainEnd) {
            return i.level();
        }
        double t = release <= 0 ? 1.0 : (ms - sustainEnd) / release;
        return scale(i.level(), 1.0 - t);
    }

    private static float texture(HapticPrimitive.Texture t, double ms) {
        if (ms >= t.durationMs()) {
            return 0f;
        }
        double freqHz = 4.0 + 6.0 * HapticMath.clamp01(t.density());
        double phase = Math.sin(2.0 * Math.PI * freqHz * (ms / 1000.0));
        double mod = 0.75 + 0.25 * phase; // subtle grain, stays within [0.5, 1]
        return scale(t.level(), mod);
    }

    private static float rumble(HapticPrimitive.Rumble r, double ms) {
        double dur = r.durationMs();
        if (ms >= dur) {
            return 0f;
        }
        double envelope = r.decay() ? (1.0 - ms / dur) : 1.0;
        double rough = 1.0 - 0.15 * r.roughness() * (0.5 + 0.5 * Math.sin(ms * 0.09));
        return scale(r.level(), envelope * rough);
    }

    private static float sweep(HapticPrimitive.Sweep s, double ms) {
        double dur = s.durationMs();
        double t = dur <= 0 ? 1.0 : HapticMath.clamp(ms / dur, 0.0, 1.0);
        double eased = switch (s.easing()) {
            case LINEAR -> t;
            case EASE_IN -> t * t;
            case EASE_OUT -> 1.0 - (1.0 - t) * (1.0 - t);
            case EASE_IN_OUT -> t * t * (3.0 - 2.0 * t);
        };
        return HapticMath.clamp01((float) (s.from() + (s.to() - s.from()) * eased));
    }

    private static float beat(HapticPrimitive.BeatPattern pattern, double ms) {
        float best = 0f;
        for (HapticPrimitive.Beat b : pattern.beats()) {
            double start = b.atMs();
            double end = start + b.durationMs();
            if (ms >= start && ms < end) {
                // Small in-beat attack/release for a clean feel.
                double local = (ms - start) / Math.max(1.0, b.durationMs());
                double shape = local < 0.2 ? local / 0.2 : (local > 0.8 ? (1.0 - local) / 0.2 : 1.0);
                best = Math.max(best, scale(b.level(), shape));
            }
        }
        return best;
    }

    private static float hold(HapticPrimitive.Hold h, double ms) {
        double dur = h.durationMs();
        if (ms >= dur) {
            return 0f;
        }
        if (ms < h.fadeInMs()) {
            return scale(h.level(), h.fadeInMs() <= 0 ? 1.0 : ms / h.fadeInMs());
        }
        double fadeOutStart = dur - h.fadeOutMs();
        if (ms >= fadeOutStart && h.fadeOutMs() > 0) {
            return scale(h.level(), (dur - ms) / h.fadeOutMs());
        }
        return h.level();
    }

    private static float scale(float level, double factor) {
        return HapticMath.clamp01((float) (level * HapticMath.clamp(factor, 0.0, 1.0)));
    }
}
