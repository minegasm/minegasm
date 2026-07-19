package net.minegasm.config;

import java.util.Collections;
import java.util.Map;

/**
 * Per-device user setting keyed by a best-effort identity string (never a raw device index — brief
 * §13.3). Holds a device-level cap and per-feature settings keyed by feature identity.
 */
public record DeviceSetting(boolean enabled, double maxLevel, Map<String, FeatureSetting> features) {

    public DeviceSetting {
        if (maxLevel < 0) {
            maxLevel = 0;
        } else if (maxLevel > 1.0) {
            maxLevel = 1.0;
        }
        features = features == null ? Map.of() : Collections.unmodifiableMap(Map.copyOf(features));
    }

    public static DeviceSetting defaultOn() {
        return new DeviceSetting(true, 1.0, Map.of());
    }

    public FeatureSetting feature(String featureKey) {
        return features.getOrDefault(featureKey, FeatureSetting.defaultOn());
    }
}
