package net.minegasm.util;

import net.minegasm.device.IntRange;

/**
 * Pure, side-effect-free math for the haptic engine: clamping, response curves, range scaling, and
 * deterministic seeded variation. Every normalization is clamped at an explicit boundary (guideline
 * Â§H "no unchecked normalization values").
 */
public final class HapticMath {

    private HapticMath() {}

    /** Clamp a normalized value into {@code [0, 1]}. */
    public static float clamp01(float value) {
        if (value < 0f) {
            return 0f;
        }
        return value > 1f ? 1f : value;
    }

    public static float clamp(float value, float min, float max) {
        if (value < min) {
            return min;
        }
        return value > max ? max : value;
    }

    public static double clamp(double value, double min, double max) {
        if (value < min) {
            return min;
        }
        return value > max ? max : value;
    }

    /**
     * Classic Hermite smoothstep on {@code [0, 1]}. Used for hurt strength shaping so small damage
     * stays gentle and large damage saturates smoothly (brief Â§7.3).
     */
    public static float smoothstep(float x) {
        float t = clamp01(x);
        return t * t * (3f - 2f * t);
    }

    /** Linear interpolation. */
    public static float lerp(float from, float to, float t) {
        return from + (to - from) * clamp01(t);
    }

    /**
     * Scale a normalized {@code [0, 1]} value into a device's inclusive integer output range.
     * The single conversion point between the engine's float world and the wire (brief Â§9.7).
     */
    public static int scaleNormalized(float normalized, IntRange range) {
        float value = clamp01(normalized);
        int span = range.max() - range.min();
        return range.min() + Math.round(value * span);
    }

    /**
     * Scale a signed normalized {@code [-1, 1]} value into an inclusive integer range. Required for
     * signed outputs such as {@code Rotate}; a generic 0..1 intensity must never be fed into a
     * signed range without an explicit direction decision (brief Â§9.7).
     */
    public static int scaleSigned(float signedNormalized, IntRange range) {
        float value = clamp(signedNormalized, -1f, 1f);
        // Map [-1, 1] -> [min, max].
        float t = (value + 1f) * 0.5f;
        int span = range.max() - range.min();
        return range.min() + Math.round(t * span);
    }

    /**
     * Deterministic, stable hash for seeded variation. Same inputs always yield the same seed, so a
     * given event context produces reproducible variation (brief Â§8.4). Uses a 64-bit mix.
     */
    public static long variationSeed(Object... parts) {
        long h = 0xcbf29ce484222325L; // FNV offset basis
        for (Object part : parts) {
            long v = part == null ? 0L : part.hashCode() & 0xffffffffL;
            h ^= v;
            h *= 0x100000001b3L; // FNV prime
            h ^= (h >>> 32);
        }
        // Final avalanche (splitmix64 finalizer) for good bit distribution.
        h ^= (h >>> 30);
        h *= 0xbf58476d1ce4e5b9L;
        h ^= (h >>> 27);
        h *= 0x94d049bb133111ebL;
        h ^= (h >>> 31);
        return h;
    }

    /**
     * Deterministic bounded jitter in {@code [-magnitude, +magnitude]} derived from a seed and a
     * salt. Returns 0 exactly when {@code magnitude <= 0}, so variation can be disabled cleanly.
     */
    public static float seededJitter(long seed, int salt, float magnitude) {
        if (magnitude <= 0f) {
            return 0f;
        }
        long mixed = variationSeed(seed, salt);
        // Convert top 24 bits to a float in [0, 1) then to [-1, 1).
        float unit = ((mixed >>> 40) / (float) (1L << 24));
        float centered = unit * 2f - 1f;
        return centered * magnitude;
    }

    /**
     * Apply bounded multiplicative variation to a level: {@code level * (1 + jitter)}, clamped to
     * {@code [0, 1]}. Safety caps and priorities are never varied (brief Â§8.4).
     */
    public static float varyLevel(float level, long seed, float fraction) {
        float jitter = seededJitter(seed, 1, fraction);
        return clamp01(level * (1f + jitter));
    }
}

