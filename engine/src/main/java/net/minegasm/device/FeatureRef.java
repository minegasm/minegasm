package net.minegasm.device;

import java.util.Objects;

/**
 * A stable-within-a-generation reference to a specific feature of a specific device. Pairs the
 * ephemeral device/feature indexes with the registry generation they were resolved against, so a
 * scheduled command can be discarded if the generation no longer matches (brief §5.3, §9.5).
 */
public final class FeatureRef {

    private final int deviceIndex;
    private final int featureIndex;
    private final long registryGeneration;

    public FeatureRef(int deviceIndex, int featureIndex, long registryGeneration) {
        if (deviceIndex < 0 || featureIndex < 0) {
            throw new IllegalArgumentException(
                    "indexes must be >= 0: device=" + deviceIndex + " feature=" + featureIndex);
        }
        this.deviceIndex = deviceIndex;
        this.featureIndex = featureIndex;
        this.registryGeneration = registryGeneration;
    }

    public int deviceIndex() {
        return deviceIndex;
    }

    public int featureIndex() {
        return featureIndex;
    }

    public long registryGeneration() {
        return registryGeneration;
    }

    /** Identity ignoring generation, useful for user-facing enablement keys. */
    public String stableKey() {
        return deviceIndex + ":" + featureIndex;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof FeatureRef)) {
            return false;
        }
        FeatureRef other = (FeatureRef) o;
        return deviceIndex == other.deviceIndex
                && featureIndex == other.featureIndex
                && registryGeneration == other.registryGeneration;
    }

    @Override
    public int hashCode() {
        return Objects.hash(deviceIndex, featureIndex, registryGeneration);
    }

    @Override
    public String toString() {
        return "FeatureRef[deviceIndex=" + deviceIndex + ", featureIndex=" + featureIndex
                + ", registryGeneration=" + registryGeneration + "]";
    }
}
