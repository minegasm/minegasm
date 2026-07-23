package net.minegasm.device;

/**
 * A stable-within-a-generation reference to a specific feature of a specific device. Pairs the
 * ephemeral device/feature indexes with the registry generation they were resolved against, so a
 * scheduled command can be discarded if the generation no longer matches (brief §5.3, §9.5).
 */
public record FeatureRef(int deviceIndex, int featureIndex, long registryGeneration) {

    public FeatureRef {
        if (deviceIndex < 0 || featureIndex < 0) {
            throw new IllegalArgumentException(
                    "indexes must be >= 0: device=" + deviceIndex + " feature=" + featureIndex);
        }
    }

    /** Identity ignoring generation, useful for user-facing enablement keys. */
    public String stableKey() {
        return deviceIndex + ":" + featureIndex;
    }
}

