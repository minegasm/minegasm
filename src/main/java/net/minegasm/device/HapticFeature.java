package net.minegasm.device;

import net.minegasm.core.InputKind;
import net.minegasm.core.OutputKind;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

/**
 * One feature of a device, exposing zero or more output capabilities (a feature may advertise
 * several output contexts, e.g. a stroker with both {@code Position} and
 * {@code HwPositionWithDuration}) and zero or more inputs (brief Â§5.3, Â§C rule 7).
 */
public record HapticFeature(
        int featureIndex,
        String description,
        Map<OutputKind, OutputCapability> outputs,
        Map<InputKind, InputCapability> inputs) {

    public HapticFeature {
        if (featureIndex < 0) {
            throw new IllegalArgumentException("featureIndex must be >= 0: " + featureIndex);
        }
        description = description == null ? "" : description;
        outputs = outputs == null ? Map.of() : Collections.unmodifiableMap(Map.copyOf(outputs));
        inputs = inputs == null ? Map.of() : Collections.unmodifiableMap(Map.copyOf(inputs));
    }

    public boolean supports(OutputKind kind) {
        return outputs.containsKey(kind);
    }

    public Optional<OutputCapability> output(OutputKind kind) {
        return Optional.ofNullable(outputs.get(kind));
    }
}

