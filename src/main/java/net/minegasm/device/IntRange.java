package net.minegasm.device;

/**
 * An inclusive integer range as advertised by a Buttplug feature (value or duration). Buttplug
 * ranges are inclusive on both ends (brief §C rule 4). May be signed (e.g. rotate direction).
 */
public record IntRange(int min, int max) {

    public IntRange {
        if (max < min) {
            throw new IllegalArgumentException("range max < min: [" + min + ", " + max + "]");
        }
    }

    public int span() {
        return max - min;
    }

    public boolean isSigned() {
        return min < 0;
    }

    public boolean contains(int value) {
        return value >= min && value <= max;
    }

    /** Clamp a raw integer into this inclusive range. */
    public int clamp(int value) {
        if (value < min) {
            return min;
        }
        return value > max ? max : value;
    }

    public static IntRange of(int min, int max) {
        return new IntRange(min, max);
    }
}

