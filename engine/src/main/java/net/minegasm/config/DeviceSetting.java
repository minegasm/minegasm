package net.minegasm.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Per-device user setting keyed by a best-effort identity string (never a raw device index, brief
 * §13.3). Holds a device-level cap and per-feature settings keyed by feature identity.
 */
public final class DeviceSetting implements ConfigValue {

    private final boolean enabled;
    private final double maxLevel;
    private final Map<String, FeatureSetting> features;

    public DeviceSetting(boolean enabled, double maxLevel, Map<String, FeatureSetting> features) {
        this.enabled = enabled;
        if (maxLevel < 0) {
            this.maxLevel = 0;
        } else if (maxLevel > 1.0) {
            this.maxLevel = 1.0;
        } else {
            this.maxLevel = maxLevel;
        }
        this.features = features == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(features));
    }

    public boolean enabled() {
        return enabled;
    }

    public double maxLevel() {
        return maxLevel;
    }

    public Map<String, FeatureSetting> features() {
        return features;
    }

    public static DeviceSetting defaultOn() {
        return new DeviceSetting(true, 1.0, Collections.emptyMap());
    }

    public FeatureSetting feature(String featureKey) {
        return features.getOrDefault(featureKey, FeatureSetting.defaultOn());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DeviceSetting)) {
            return false;
        }
        DeviceSetting other = (DeviceSetting) o;
        return enabled == other.enabled && Double.compare(maxLevel, other.maxLevel) == 0
                && Objects.equals(features, other.features);
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, maxLevel, features);
    }

    @Override
    public String toString() {
        return "DeviceSetting[enabled=" + enabled + ", maxLevel=" + maxLevel
                + ", features=" + features + "]";
    }
}
