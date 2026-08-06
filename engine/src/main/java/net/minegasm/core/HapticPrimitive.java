package net.minegasm.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * The device-independent shape of an effect. Renderers translate each concrete kind into per-output
 * commands (brief §5.2, §8.2). All durations are milliseconds and are converted to monotonic
 * deadlines by the worker, never to tick counts (brief §6.1).
 */
public interface HapticPrimitive {

    /** Nominal level of the primitive in {@code [0, 1]} (peak for shaped primitives). */
    float level();

    /** Total nominal duration in milliseconds. */
    int durationMs();

    /**
     * Return a copy of this primitive with its amplitude multiplied by {@code factor} (clamped to
     * {@code [0, 1]}), leaving timing and shape untouched. Because {@link PrimitiveEvaluator} is linear
     * in a primitive's amplitude, scaling here is equivalent to scaling the rendered level, which lets
     * central governance bake attenuation (fatigue now, the body budget later) into a scene before it
     * fans out to any backend, including semantic ones that never render a level themselves.
     */
    HapticPrimitive scaled(float factor);

    /** Clamp an amplitude to the valid {@code [0, 1]} range. */
    static float clampLevel(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    /** Immediate contact/impact: attack, hurt, place, block break (brief §8.2). */
    final class Impulse implements HapticPrimitive {
        private final float level;
        private final int durationMs;
        private final int attackMs;
        private final int releaseMs;

        public Impulse(float level, int durationMs, int attackMs, int releaseMs) {
            this.level = level;
            this.durationMs = durationMs;
            this.attackMs = attackMs;
            this.releaseMs = releaseMs;
        }

        @Override
        public float level() {
            return level;
        }

        @Override
        public int durationMs() {
            return durationMs;
        }

        public int attackMs() {
            return attackMs;
        }

        public int releaseMs() {
            return releaseMs;
        }

        @Override
        public HapticPrimitive scaled(float factor) {
            return new Impulse(clampLevel(level * factor), durationMs, attackMs, releaseMs);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Impulse)) {
                return false;
            }
            Impulse other = (Impulse) o;
            return Float.compare(level, other.level) == 0 && durationMs == other.durationMs
                    && attackMs == other.attackMs && releaseMs == other.releaseMs;
        }

        @Override
        public int hashCode() {
            return Objects.hash(level, durationMs, attackMs, releaseMs);
        }

        @Override
        public String toString() {
            return "Impulse[level=" + level + ", durationMs=" + durationMs + ", attackMs=" + attackMs
                    + ", releaseMs=" + releaseMs + "]";
        }
    }

    /** Repeated material/contact detail for active mining. */
    final class Texture implements HapticPrimitive {
        private final float level;
        private final int durationMs;
        private final float grain;
        private final float density;
        private final float irregularity;

        public Texture(float level, int durationMs, float grain, float density, float irregularity) {
            this.level = level;
            this.durationMs = durationMs;
            this.grain = grain;
            this.density = density;
            this.irregularity = irregularity;
        }

        @Override
        public float level() {
            return level;
        }

        @Override
        public int durationMs() {
            return durationMs;
        }

        public float grain() {
            return grain;
        }

        public float density() {
            return density;
        }

        public float irregularity() {
            return irregularity;
        }

        @Override
        public HapticPrimitive scaled(float factor) {
            return new Texture(clampLevel(level * factor), durationMs, grain, density, irregularity);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Texture)) {
                return false;
            }
            Texture other = (Texture) o;
            return Float.compare(level, other.level) == 0 && durationMs == other.durationMs
                    && Float.compare(grain, other.grain) == 0
                    && Float.compare(density, other.density) == 0
                    && Float.compare(irregularity, other.irregularity) == 0;
        }

        @Override
        public int hashCode() {
            return Objects.hash(level, durationMs, grain, density, irregularity);
        }

        @Override
        public String toString() {
            return "Texture[level=" + level + ", durationMs=" + durationMs + ", grain=" + grain
                    + ", density=" + density + ", irregularity=" + irregularity + "]";
        }
    }

    /** Environmental low-frequency energy: explosion aftershock. */
    final class Rumble implements HapticPrimitive {
        private final float level;
        private final int durationMs;
        private final float roughness;
        private final boolean decay;

        public Rumble(float level, int durationMs, float roughness, boolean decay) {
            this.level = level;
            this.durationMs = durationMs;
            this.roughness = roughness;
            this.decay = decay;
        }

        @Override
        public float level() {
            return level;
        }

        @Override
        public int durationMs() {
            return durationMs;
        }

        public float roughness() {
            return roughness;
        }

        public boolean decay() {
            return decay;
        }

        @Override
        public HapticPrimitive scaled(float factor) {
            return new Rumble(clampLevel(level * factor), durationMs, roughness, decay);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Rumble)) {
                return false;
            }
            Rumble other = (Rumble) o;
            return Float.compare(level, other.level) == 0 && durationMs == other.durationMs
                    && Float.compare(roughness, other.roughness) == 0 && decay == other.decay;
        }

        @Override
        public int hashCode() {
            return Objects.hash(level, durationMs, roughness, decay);
        }

        @Override
        public String toString() {
            return "Rumble[level=" + level + ", durationMs=" + durationMs + ", roughness=" + roughness
                    + ", decay=" + decay + "]";
        }
    }

    /** Tension/buildup ramp: future bow draw, charge. */
    final class Sweep implements HapticPrimitive {
        private final float from;
        private final float to;
        private final int durationMs;
        private final Easing easing;

        public Sweep(float from, float to, int durationMs, Easing easing) {
            this.from = from;
            this.to = to;
            this.durationMs = durationMs;
            this.easing = easing;
        }

        public float from() {
            return from;
        }

        public float to() {
            return to;
        }

        @Override
        public int durationMs() {
            return durationMs;
        }

        public Easing easing() {
            return easing;
        }

        @Override
        public float level() {
            return Math.max(from, to);
        }

        @Override
        public HapticPrimitive scaled(float factor) {
            return new Sweep(clampLevel(from * factor), clampLevel(to * factor), durationMs, easing);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Sweep)) {
                return false;
            }
            Sweep other = (Sweep) o;
            return Float.compare(from, other.from) == 0 && Float.compare(to, other.to) == 0
                    && durationMs == other.durationMs && easing == other.easing;
        }

        @Override
        public int hashCode() {
            return Objects.hash(from, to, durationMs, easing);
        }

        @Override
        public String toString() {
            return "Sweep[from=" + from + ", to=" + to + ", durationMs=" + durationMs
                    + ", easing=" + easing + "]";
        }
    }

    /** Recognisable notification/reward rhythm: XP, advancement, fishing bite, vitality. */
    final class BeatPattern implements HapticPrimitive {
        private final List<Beat> beats;

        public BeatPattern(List<Beat> beats) {
            this.beats = beats == null
                    ? Collections.emptyList()
                    : Collections.unmodifiableList(new ArrayList<>(beats));
        }

        public List<Beat> beats() {
            return beats;
        }

        @Override
        public float level() {
            return (float) beats.stream().mapToDouble(Beat::level).max().orElse(0.0);
        }

        @Override
        public int durationMs() {
            return beats.stream().mapToInt(b -> b.atMs() + b.durationMs()).max().orElse(0);
        }

        @Override
        public HapticPrimitive scaled(float factor) {
            List<Beat> scaled = new ArrayList<>(beats.size());
            for (Beat b : beats) {
                scaled.add(new Beat(b.atMs(), clampLevel(b.level() * factor), b.durationMs()));
            }
            return new BeatPattern(scaled);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof BeatPattern)) {
                return false;
            }
            BeatPattern other = (BeatPattern) o;
            return Objects.equals(beats, other.beats);
        }

        @Override
        public int hashCode() {
            return Objects.hash(beats);
        }

        @Override
        public String toString() {
            return "BeatPattern[beats=" + beats + "]";
        }
    }

    /** Sustained state with fade; carefully limited for ambient/state effects. */
    final class Hold implements HapticPrimitive {
        private final float level;
        private final int durationMs;
        private final int fadeInMs;
        private final int fadeOutMs;

        public Hold(float level, int durationMs, int fadeInMs, int fadeOutMs) {
            this.level = level;
            this.durationMs = durationMs;
            this.fadeInMs = fadeInMs;
            this.fadeOutMs = fadeOutMs;
        }

        @Override
        public float level() {
            return level;
        }

        @Override
        public int durationMs() {
            return durationMs;
        }

        public int fadeInMs() {
            return fadeInMs;
        }

        public int fadeOutMs() {
            return fadeOutMs;
        }

        @Override
        public HapticPrimitive scaled(float factor) {
            return new Hold(clampLevel(level * factor), durationMs, fadeInMs, fadeOutMs);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Hold)) {
                return false;
            }
            Hold other = (Hold) o;
            return Float.compare(level, other.level) == 0 && durationMs == other.durationMs
                    && fadeInMs == other.fadeInMs && fadeOutMs == other.fadeOutMs;
        }

        @Override
        public int hashCode() {
            return Objects.hash(level, durationMs, fadeInMs, fadeOutMs);
        }

        @Override
        public String toString() {
            return "Hold[level=" + level + ", durationMs=" + durationMs + ", fadeInMs=" + fadeInMs
                    + ", fadeOutMs=" + fadeOutMs + "]";
        }
    }

    /**
     * A continuous stroke for a position/motion output: a full out-and-back cycle every
     * {@code periodMs}, repeating until {@code durationMs}. {@code level} is the stroke depth (0..1).
     * Used only on the motion route; {@code SceneMixer.buildTarget} turns depth + period + elapsed time
     * into position waypoints (endpoint alternation for {@code HwPositionWithDuration}, a sampled sweep
     * for {@code Position}). Its instantaneous {@link #level()} is constant so the scene stays active;
     * the position shaping comes from the period, not the sampled level.
     */
    final class Oscillation implements HapticPrimitive {
        private final float level;
        private final int periodMs;
        private final int durationMs;

        public Oscillation(float level, int periodMs, int durationMs) {
            this.level = level;
            this.periodMs = periodMs;
            this.durationMs = durationMs;
        }

        @Override
        public float level() {
            return level;
        }

        @Override
        public int durationMs() {
            return durationMs;
        }

        public int periodMs() {
            return periodMs;
        }

        @Override
        public HapticPrimitive scaled(float factor) {
            return new Oscillation(clampLevel(level * factor), periodMs, durationMs);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Oscillation)) {
                return false;
            }
            Oscillation other = (Oscillation) o;
            return Float.compare(level, other.level) == 0 && periodMs == other.periodMs
                    && durationMs == other.durationMs;
        }

        @Override
        public int hashCode() {
            return Objects.hash(level, periodMs, durationMs);
        }

        @Override
        public String toString() {
            return "Oscillation[level=" + level + ", periodMs=" + periodMs
                    + ", durationMs=" + durationMs + "]";
        }
    }

    /** One beat within a {@link BeatPattern}: onset offset, level, and duration in ms. */
    final class Beat {
        private final int atMs;
        private final float level;
        private final int durationMs;

        public Beat(int atMs, float level, int durationMs) {
            this.atMs = atMs;
            this.level = level;
            this.durationMs = durationMs;
        }

        public int atMs() {
            return atMs;
        }

        public float level() {
            return level;
        }

        public int durationMs() {
            return durationMs;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Beat)) {
                return false;
            }
            Beat other = (Beat) o;
            return atMs == other.atMs && Float.compare(level, other.level) == 0
                    && durationMs == other.durationMs;
        }

        @Override
        public int hashCode() {
            return Objects.hash(atMs, level, durationMs);
        }

        @Override
        public String toString() {
            return "Beat[atMs=" + atMs + ", level=" + level + ", durationMs=" + durationMs + "]";
        }
    }

    enum Easing {
        LINEAR,
        EASE_IN,
        EASE_OUT,
        EASE_IN_OUT
    }
}
