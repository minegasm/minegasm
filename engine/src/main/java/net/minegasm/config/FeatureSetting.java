package net.minegasm.config;

/** Per-feature user setting under a device: enablement and a multiplier. */
public final class FeatureSetting implements ConfigValue {

    private final boolean enabled;
    private final double multiplier;

    public FeatureSetting(boolean enabled, double multiplier) {
        this.enabled = enabled;
        if (multiplier < 0) {
            this.multiplier = 0;
        } else if (multiplier > 2.0) {
            this.multiplier = 2.0;
        } else {
            this.multiplier = multiplier;
        }
    }

    public boolean enabled() {
        return enabled;
    }

    public double multiplier() {
        return multiplier;
    }

    public static FeatureSetting defaultOn() {
        return new FeatureSetting(true, 1.0);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof FeatureSetting)) {
            return false;
        }
        FeatureSetting other = (FeatureSetting) o;
        return enabled == other.enabled && Double.compare(multiplier, other.multiplier) == 0;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(enabled, multiplier);
    }

    @Override
    public String toString() {
        return "FeatureSetting[enabled=" + enabled + ", multiplier=" + multiplier + "]";
    }
}
