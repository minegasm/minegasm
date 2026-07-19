package net.minegasm.core;

import java.util.List;

/**
 * The device-independent shape of an effect. A sealed hierarchy so renderers can exhaustively
 * translate meaning into per-output commands (brief Â§5.2, Â§8.2). All durations are milliseconds and
 * are converted to monotonic deadlines by the worker â€” never to tick counts (brief Â§6.1).
 */
public sealed interface HapticPrimitive
        permits HapticPrimitive.Impulse,
                HapticPrimitive.Texture,
                HapticPrimitive.Rumble,
                HapticPrimitive.Sweep,
                HapticPrimitive.BeatPattern,
                HapticPrimitive.Hold {

    /** Nominal level of the primitive in {@code [0, 1]} (peak for shaped primitives). */
    float level();

    /** Total nominal duration in milliseconds. */
    int durationMs();

    /** Immediate contact/impact: attack, hurt, place, block break (brief Â§8.2). */
    record Impulse(float level, int durationMs, int attackMs, int releaseMs)
            implements HapticPrimitive {}

    /** Repeated material/contact detail for active mining. */
    record Texture(float level, int durationMs, float grain, float density, float irregularity)
            implements HapticPrimitive {}

    /** Environmental low-frequency energy: explosion aftershock. */
    record Rumble(float level, int durationMs, float roughness, boolean decay)
            implements HapticPrimitive {}

    /** Tension/buildup ramp: future bow draw, charge. */
    record Sweep(float from, float to, int durationMs, Easing easing) implements HapticPrimitive {
        @Override
        public float level() {
            return Math.max(from, to);
        }
    }

    /** Recognisable notification/reward rhythm: XP, advancement, fishing bite, vitality. */
    record BeatPattern(List<Beat> beats) implements HapticPrimitive {
        public BeatPattern {
            beats = beats == null ? List.of() : List.copyOf(beats);
        }

        @Override
        public float level() {
            return (float) beats.stream().mapToDouble(Beat::level).max().orElse(0.0);
        }

        @Override
        public int durationMs() {
            return beats.stream().mapToInt(b -> b.atMs() + b.durationMs()).max().orElse(0);
        }
    }

    /** Sustained state with fade; carefully limited for ambient/state effects. */
    record Hold(float level, int durationMs, int fadeInMs, int fadeOutMs)
            implements HapticPrimitive {}

    /** One beat within a {@link BeatPattern}: onset offset, level, and duration in ms. */
    record Beat(int atMs, float level, int durationMs) {}

    enum Easing {
        LINEAR,
        EASE_IN,
        EASE_OUT,
        EASE_IN_OUT
    }
}

