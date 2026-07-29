package net.minegasm.core;

import net.minegasm.device.FeatureRef;

import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Capability- and policy-based routing for a layer, never brand names (brief §5.4). Declares which
 * output kinds are acceptable, whether experimental opt-in is required, optional include/exclude
 * filters, and the delivery spread.
 */
public final class HapticRoute {

    private final Set<OutputKind> allowedOutputs;
    private final boolean requiresExperimentalOptIn;
    private final Set<Integer> includedDeviceIndexes;
    private final Set<FeatureRef> includedFeatures;
    private final Set<FeatureRef> excludedFeatures;
    private final DeliveryMode deliveryMode;

    public HapticRoute(
            Set<OutputKind> allowedOutputs,
            boolean requiresExperimentalOptIn,
            Set<Integer> includedDeviceIndexes,
            Set<FeatureRef> includedFeatures,
            Set<FeatureRef> excludedFeatures,
            DeliveryMode deliveryMode) {
        this.allowedOutputs = allowedOutputs == null || allowedOutputs.isEmpty()
                ? EnumSet.of(OutputKind.VIBRATE)
                : Collections.unmodifiableSet(EnumSet.copyOf(allowedOutputs));
        this.requiresExperimentalOptIn = requiresExperimentalOptIn;
        this.includedDeviceIndexes = includedDeviceIndexes == null
                ? Collections.emptySet()
                : Collections.unmodifiableSet(new LinkedHashSet<>(includedDeviceIndexes));
        this.includedFeatures = includedFeatures == null
                ? Collections.emptySet()
                : Collections.unmodifiableSet(new LinkedHashSet<>(includedFeatures));
        this.excludedFeatures = excludedFeatures == null
                ? Collections.emptySet()
                : Collections.unmodifiableSet(new LinkedHashSet<>(excludedFeatures));
        this.deliveryMode = deliveryMode == null ? DeliveryMode.ALL_COMPATIBLE : deliveryMode;
    }

    public Set<OutputKind> allowedOutputs() {
        return allowedOutputs;
    }

    public boolean requiresExperimentalOptIn() {
        return requiresExperimentalOptIn;
    }

    public Set<Integer> includedDeviceIndexes() {
        return includedDeviceIndexes;
    }

    public Set<FeatureRef> includedFeatures() {
        return includedFeatures;
    }

    public Set<FeatureRef> excludedFeatures() {
        return excludedFeatures;
    }

    public DeliveryMode deliveryMode() {
        return deliveryMode;
    }

    /** The common case: vibration to all compatible features, no experimental gating. */
    public static HapticRoute vibrateAll() {
        return new HapticRoute(EnumSet.of(OutputKind.VIBRATE), false,
                Collections.emptySet(), Collections.emptySet(), Collections.emptySet(),
                DeliveryMode.ALL_COMPATIBLE);
    }

    /** Vibration everywhere plus experimental motion as a supplemental layer. */
    public static HapticRoute vibrateAllPlusMotion() {
        return new HapticRoute(
                EnumSet.of(OutputKind.VIBRATE, OutputKind.HW_POSITION_WITH_DURATION,
                        OutputKind.POSITION),
                false, Collections.emptySet(), Collections.emptySet(), Collections.emptySet(),
                DeliveryMode.ALL_COMPATIBLE);
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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof HapticRoute)) {
            return false;
        }
        HapticRoute other = (HapticRoute) o;
        return requiresExperimentalOptIn == other.requiresExperimentalOptIn
                && Objects.equals(allowedOutputs, other.allowedOutputs)
                && Objects.equals(includedDeviceIndexes, other.includedDeviceIndexes)
                && Objects.equals(includedFeatures, other.includedFeatures)
                && Objects.equals(excludedFeatures, other.excludedFeatures)
                && deliveryMode == other.deliveryMode;
    }

    @Override
    public int hashCode() {
        return Objects.hash(allowedOutputs, requiresExperimentalOptIn, includedDeviceIndexes,
                includedFeatures, excludedFeatures, deliveryMode);
    }

    @Override
    public String toString() {
        return "HapticRoute[allowedOutputs=" + allowedOutputs + ", requiresExperimentalOptIn="
                + requiresExperimentalOptIn + ", includedDeviceIndexes=" + includedDeviceIndexes
                + ", includedFeatures=" + includedFeatures + ", excludedFeatures=" + excludedFeatures
                + ", deliveryMode=" + deliveryMode + "]";
    }
}
