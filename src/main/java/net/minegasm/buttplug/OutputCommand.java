package net.minegasm.buttplug;

import net.minegasm.core.OutputKind;
import net.minegasm.device.FeatureRef;

/**
 * A wire-ready, feature-level output command (brief §2.5, §9.6). Values are already scaled into the
 * feature's advertised integer range. The captured {@code registryGeneration} lets the provider drop
 * the command if the device list has since changed (brief §5.3, §9.5).
 */
public record OutputCommand(
        int deviceIndex,
        int featureIndex,
        OutputKind kind,
        int value,
        Integer durationMs,
        long registryGeneration) {

    public static OutputCommand of(FeatureRef ref, OutputKind kind, int value, Integer durationMs) {
        return new OutputCommand(ref.deviceIndex(), ref.featureIndex(), kind, value, durationMs,
                ref.registryGeneration());
    }

    public boolean hasDuration() {
        return durationMs != null && kind.carriesDuration();
    }
}
