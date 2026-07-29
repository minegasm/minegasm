package net.minegasm.device;

import java.util.Objects;

/**
 * An inclusive integer range as advertised by a Buttplug feature (value or duration). Buttplug
 * ranges are inclusive on both ends (brief §C rule 4). May be signed (e.g. rotate direction).
 */
public final class IntRange {

    private final int min;
    private final int max;

    public IntRange(int min, int max) {
        if (max < min) {
            throw new IllegalArgumentException("range max < min: [" + min + ", " + max + "]");
        }
        this.min = min;
        this.max = max;
    }

    public int min() {
        return min;
    }

    public int max() {
        return max;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IntRange)) {
            return false;
        }
        IntRange other = (IntRange) o;
        return min == other.min && max == other.max;
    }

    @Override
    public int hashCode() {
        return Objects.hash(min, max);
    }

    @Override
    public String toString() {
        return "IntRange[min=" + min + ", max=" + max + "]";
    }
}
