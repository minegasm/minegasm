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
    private final Set<Integer> includedDeviceIndexes;
    private final Set<FeatureRef> includedFeatures;
    private final Set<FeatureRef> excludedFeatures;
    private final DeliveryMode deliveryMode;

    public HapticRoute(
            Set<OutputKind> allowedOutputs,
            Set<Integer> includedDeviceIndexes,
            Set<FeatureRef> includedFeatures,
            Set<FeatureRef> excludedFeatures,
            DeliveryMode deliveryMode) {
        this.allowedOutputs = allowedOutputs == null || allowedOutputs.isEmpty()
                ? EnumSet.of(OutputKind.VIBRATE, OutputKind.OSCILLATE, OutputKind.ROTATE)
                : Collections.unmodifiableSet(EnumSet.copyOf(allowedOutputs));
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

    /** The logical output families this route can reach. */
    public Set<OutputClass> outputClasses() {
        return Collections.unmodifiableSet(OutputClass.ofKinds(allowedOutputs));
    }

    /**
     * Copy this route with only the output kinds in {@code outputClass}. Physical include and exclude
     * filters and delivery spread are retained.
     */
    public HapticRoute restrictedTo(OutputClass outputClass) {
        EnumSet<OutputKind> restricted = EnumSet.noneOf(OutputKind.class);
        for (OutputKind kind : allowedOutputs) {
            if (OutputClass.of(kind) == outputClass) {
                restricted.add(kind);
            }
        }
        if (restricted.isEmpty()) {
            throw new IllegalArgumentException("route does not include " + outputClass);
        }
        return new HapticRoute(restricted, includedDeviceIndexes, includedFeatures, excludedFeatures,
                deliveryMode);
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

    /**
     * The common case: a continuous "buzz" to all compatible features. Prefers vibration, but drives an
     * oscillator or rotator where that is what the device has. No experimental gating.
     */
    public static HapticRoute buzzAll() {
        return new HapticRoute(
                EnumSet.of(OutputKind.VIBRATE, OutputKind.OSCILLATE, OutputKind.ROTATE),
                Collections.emptySet(), Collections.emptySet(), Collections.emptySet(),
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
        return Objects.equals(allowedOutputs, other.allowedOutputs)
                && Objects.equals(includedDeviceIndexes, other.includedDeviceIndexes)
                && Objects.equals(includedFeatures, other.includedFeatures)
                && Objects.equals(excludedFeatures, other.excludedFeatures)
                && deliveryMode == other.deliveryMode;
    }

    @Override
    public int hashCode() {
        return Objects.hash(allowedOutputs, includedDeviceIndexes,
                includedFeatures, excludedFeatures, deliveryMode);
    }

    @Override
    public String toString() {
        return "HapticRoute[allowedOutputs=" + allowedOutputs
                + ", includedDeviceIndexes=" + includedDeviceIndexes
                + ", includedFeatures=" + includedFeatures + ", excludedFeatures=" + excludedFeatures
                + ", deliveryMode=" + deliveryMode + "]";
    }
}
