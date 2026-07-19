package net.minegasm.core;

import net.minegasm.device.FeatureRef;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Capability- and policy-based routing for a layer â€” never brand names (brief Â§5.4). Declares which
 * output kinds are acceptable, whether experimental opt-in is required, optional include/exclude
 * filters, and the delivery spread.
 */
public record HapticRoute(
        Set<OutputKind> allowedOutputs,
        boolean requiresExperimentalOptIn,
        Set<Integer> includedDeviceIndexes,
        Set<FeatureRef> includedFeatures,
        Set<FeatureRef> excludedFeatures,
        DeliveryMode deliveryMode) {

    public HapticRoute {
        allowedOutputs = allowedOutputs == null || allowedOutputs.isEmpty()
                ? EnumSet.of(OutputKind.VIBRATE)
                : Collections.unmodifiableSet(EnumSet.copyOf(allowedOutputs));
        includedDeviceIndexes = includedDeviceIndexes == null
                ? Set.of() : Set.copyOf(includedDeviceIndexes);
        includedFeatures = includedFeatures == null ? Set.of() : Set.copyOf(includedFeatures);
        excludedFeatures = excludedFeatures == null ? Set.of() : Set.copyOf(excludedFeatures);
        deliveryMode = deliveryMode == null ? DeliveryMode.ALL_COMPATIBLE : deliveryMode;
    }

    /** The common case: vibration to all compatible features, no experimental gating. */
    public static HapticRoute vibrateAll() {
        return new HapticRoute(EnumSet.of(OutputKind.VIBRATE), false,
                Set.of(), Set.of(), Set.of(), DeliveryMode.ALL_COMPATIBLE);
    }

    /** Vibration everywhere plus experimental motion as a supplemental layer. */
    public static HapticRoute vibrateAllPlusMotion() {
        return new HapticRoute(
                EnumSet.of(OutputKind.VIBRATE, OutputKind.HW_POSITION_WITH_DURATION,
                        OutputKind.POSITION),
                false, Set.of(), Set.of(), Set.of(), DeliveryMode.ALL_COMPATIBLE);
    }

    public boolean allows(OutputKind kind) {
        return allowedOutputs.contains(kind);
    }

    public boolean includes(FeatureRef ref) {
        if (excludedFeatures.contains(ref)) {
            return false;
        }
        boolean deviceOk = includedDeviceIndexes.isEmpty()
                || includedDeviceIndexes.contains(ref.deviceIndex());
        boolean featureOk = includedFeatures.isEmpty() || includedFeatures.contains(ref);
        return deviceOk && featureOk;
    }
}

