package net.minegasm.config;

/** Per-feature user setting under a device: enablement and a multiplier. */
public record FeatureSetting(boolean enabled, double multiplier) {

    public FeatureSetting {
        if (multiplier < 0) {
            multiplier = 0;
        } else if (multiplier > 2.0) {
            multiplier = 2.0;
        }
    }

    public static FeatureSetting defaultOn() {
        return new FeatureSetting(true, 1.0);
    }
}
