package net.minegasm.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Per-device user setting keyed by a best-effort identity string (never a raw device index, brief
 * §13.3). Holds a device-level minimum (start-threshold) and cap, plus per-feature settings keyed by
 * feature identity.
 */
public final class DeviceSetting implements ConfigValue {

    /** Default start-threshold: a vibration-class output below this is lifted so it is felt on a motor
     *  with a dead zone. 0 disables the floor. */
    public static final double DEFAULT_MIN_LEVEL = 0.22;

    private final boolean enabled;
    private final double minLevel;
    private final double maxLevel;
    private final Map<String, FeatureSetting> features;

    public DeviceSetting(boolean enabled, double minLevel, double maxLevel,
                         Map<String, FeatureSetting> features) {
        this.enabled = enabled;
        this.minLevel = clamp01(minLevel);
        this.maxLevel = clamp01(maxLevel);
        this.features = features == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(features));
    }

    private static double clamp01(double v) {
        return v < 0 ? 0 : (v > 1.0 ? 1.0 : v);
    }

    public boolean enabled() {
        return enabled;
    }

    public double minLevel() {
        return minLevel;
    }

    public double maxLevel() {
        return maxLevel;
    }

    public Map<String, FeatureSetting> features() {
        return features;
    }

    public static DeviceSetting defaultOn() {
        return new DeviceSetting(true, DEFAULT_MIN_LEVEL, 1.0, Collections.emptyMap());
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
        return enabled == other.enabled && Double.compare(minLevel, other.minLevel) == 0
                && Double.compare(maxLevel, other.maxLevel) == 0
                && Objects.equals(features, other.features);
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, minLevel, maxLevel, features);
    }

    @Override
    public String toString() {
        return "DeviceSetting[enabled=" + enabled + ", minLevel=" + minLevel + ", maxLevel=" + maxLevel
                + ", features=" + features + "]";
    }
}
