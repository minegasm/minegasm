package net.minegasm.device;

import net.minegasm.core.OutputKind;

import java.util.Optional;

/**
 * A single advertised output capability of a feature: its kind, value range, and optional duration
 * range (present for {@code HwPositionWithDuration}). Normalized directly from the Buttplug
 * {@code DeviceList} (brief Â§5.3).
 */
public record OutputCapability(
        OutputKind kind,
        IntRange valueRange,
        Optional<IntRange> durationMs) {

    public OutputCapability {
        if (kind == null) {
            throw new IllegalArgumentException("output kind required");
        }
        if (valueRange == null) {
            throw new IllegalArgumentException("value range required");
        }
        durationMs = durationMs == null ? Optional.empty() : durationMs;
    }

    public static OutputCapability level(OutputKind kind, int min, int max) {
        return new OutputCapability(kind, new IntRange(min, max), Optional.empty());
    }

    public static OutputCapability withDuration(OutputKind kind, IntRange value, IntRange duration) {
        return new OutputCapability(kind, value, Optional.of(duration));
    }
}

