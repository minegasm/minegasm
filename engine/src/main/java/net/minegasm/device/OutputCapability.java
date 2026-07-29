package net.minegasm.device;

import net.minegasm.core.OutputKind;

import java.util.Objects;
import java.util.Optional;

/**
 * A single advertised output capability of a feature: its kind, value range, and optional duration
 * range (present for {@code HwPositionWithDuration}). Normalized directly from the Buttplug
 * {@code DeviceList} (brief §5.3).
 */
public final class OutputCapability {

    private final OutputKind kind;
    private final IntRange valueRange;
    private final Optional<IntRange> durationMs;

    public OutputCapability(OutputKind kind, IntRange valueRange, Optional<IntRange> durationMs) {
        if (kind == null) {
            throw new IllegalArgumentException("output kind required");
        }
        if (valueRange == null) {
            throw new IllegalArgumentException("value range required");
        }
        this.kind = kind;
        this.valueRange = valueRange;
        this.durationMs = durationMs == null ? Optional.empty() : durationMs;
    }

    public OutputKind kind() {
        return kind;
    }

    public IntRange valueRange() {
        return valueRange;
    }

    public Optional<IntRange> durationMs() {
        return durationMs;
    }

    public static OutputCapability level(OutputKind kind, int min, int max) {
        return new OutputCapability(kind, new IntRange(min, max), Optional.empty());
    }

    public static OutputCapability withDuration(OutputKind kind, IntRange value, IntRange duration) {
        return new OutputCapability(kind, value, Optional.of(duration));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof OutputCapability)) {
            return false;
        }
        OutputCapability other = (OutputCapability) o;
        return kind == other.kind
                && Objects.equals(valueRange, other.valueRange)
                && Objects.equals(durationMs, other.durationMs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, valueRange, durationMs);
    }

    @Override
    public String toString() {
        return "OutputCapability[kind=" + kind + ", valueRange=" + valueRange
                + ", durationMs=" + durationMs + "]";
    }
}
