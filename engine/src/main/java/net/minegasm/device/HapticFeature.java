package net.minegasm.device;

import net.minegasm.core.InputKind;
import net.minegasm.core.OutputKind;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * One feature of a device, exposing zero or more output capabilities (a feature may advertise
 * several output contexts, e.g. a stroker with both {@code Position} and
 * {@code HwPositionWithDuration}) and zero or more inputs (brief §5.3, §C rule 7).
 */
public final class HapticFeature {

    private final int featureIndex;
    private final String description;
    private final Map<OutputKind, OutputCapability> outputs;
    private final Map<InputKind, InputCapability> inputs;

    public HapticFeature(
            int featureIndex,
            String description,
            Map<OutputKind, OutputCapability> outputs,
            Map<InputKind, InputCapability> inputs) {
        if (featureIndex < 0) {
            throw new IllegalArgumentException("featureIndex must be >= 0: " + featureIndex);
        }
        this.featureIndex = featureIndex;
        this.description = description == null ? "" : description;
        this.outputs = outputs == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(outputs));
        this.inputs = inputs == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(inputs));
    }

    public int featureIndex() {
        return featureIndex;
    }

    public String description() {
        return description;
    }

    public Map<OutputKind, OutputCapability> outputs() {
        return outputs;
    }

    public Map<InputKind, InputCapability> inputs() {
        return inputs;
    }

    public boolean supports(OutputKind kind) {
        return outputs.containsKey(kind);
    }

    public Optional<OutputCapability> output(OutputKind kind) {
        return Optional.ofNullable(outputs.get(kind));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof HapticFeature)) {
            return false;
        }
        HapticFeature other = (HapticFeature) o;
        return featureIndex == other.featureIndex
                && Objects.equals(description, other.description)
                && Objects.equals(outputs, other.outputs)
                && Objects.equals(inputs, other.inputs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(featureIndex, description, outputs, inputs);
    }

    @Override
    public String toString() {
        return "HapticFeature[featureIndex=" + featureIndex + ", description=" + description
                + ", outputs=" + outputs + ", inputs=" + inputs + "]";
    }
}
