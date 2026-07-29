package net.minegasm.device;

import net.minegasm.core.InputKind;

import java.util.Objects;

/** An advertised input/sensor capability. Represented but unused for output in the MVP. */
public final class InputCapability {

    private final InputKind kind;
    private final IntRange valueRange;

    public InputCapability(InputKind kind, IntRange valueRange) {
        if (kind == null || valueRange == null) {
            throw new IllegalArgumentException("input kind and range required");
        }
        this.kind = kind;
        this.valueRange = valueRange;
    }

    public InputKind kind() {
        return kind;
    }

    public IntRange valueRange() {
        return valueRange;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof InputCapability)) {
            return false;
        }
        InputCapability other = (InputCapability) o;
        return kind == other.kind && Objects.equals(valueRange, other.valueRange);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, valueRange);
    }

    @Override
    public String toString() {
        return "InputCapability[kind=" + kind + ", valueRange=" + valueRange + "]";
    }
}
