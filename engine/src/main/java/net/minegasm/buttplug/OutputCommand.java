package net.minegasm.buttplug;

import net.minegasm.core.OutputKind;
import net.minegasm.device.FeatureRef;

import java.util.Objects;

/**
 * A wire-ready, feature-level output command (brief §2.5, §9.6). Values are already scaled into the
 * feature's advertised integer range. The captured {@code registryGeneration} lets the provider drop
 * the command if the device list has since changed (brief §5.3, §9.5).
 */
public final class OutputCommand {

    private final int deviceIndex;
    private final int featureIndex;
    private final OutputKind kind;
    private final int value;
    private final Integer durationMs;
    private final long registryGeneration;

    public OutputCommand(int deviceIndex, int featureIndex, OutputKind kind, int value,
                         Integer durationMs, long registryGeneration) {
        this.deviceIndex = deviceIndex;
        this.featureIndex = featureIndex;
        this.kind = kind;
        this.value = value;
        this.durationMs = durationMs;
        this.registryGeneration = registryGeneration;
    }

    public int deviceIndex() {
        return deviceIndex;
    }

    public int featureIndex() {
        return featureIndex;
    }

    public OutputKind kind() {
        return kind;
    }

    public int value() {
        return value;
    }

    public Integer durationMs() {
        return durationMs;
    }

    public long registryGeneration() {
        return registryGeneration;
    }

    public static OutputCommand of(FeatureRef ref, OutputKind kind, int value, Integer durationMs) {
        return new OutputCommand(ref.deviceIndex(), ref.featureIndex(), kind, value, durationMs,
                ref.registryGeneration());
    }

    public boolean hasDuration() {
        return durationMs != null && kind.carriesDuration();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof OutputCommand)) {
            return false;
        }
        OutputCommand other = (OutputCommand) o;
        return deviceIndex == other.deviceIndex
                && featureIndex == other.featureIndex
                && value == other.value
                && registryGeneration == other.registryGeneration
                && kind == other.kind
                && Objects.equals(durationMs, other.durationMs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(deviceIndex, featureIndex, kind, value, durationMs, registryGeneration);
    }

    @Override
    public String toString() {
        return "OutputCommand[deviceIndex=" + deviceIndex + ", featureIndex=" + featureIndex
                + ", kind=" + kind + ", value=" + value + ", durationMs=" + durationMs
                + ", registryGeneration=" + registryGeneration + "]";
    }
}
