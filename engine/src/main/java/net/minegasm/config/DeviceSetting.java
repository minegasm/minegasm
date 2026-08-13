package net.minegasm.config;

import net.minegasm.core.BodyRegion;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Per-device user setting keyed by a best-effort identity string (never a raw device index, brief
 * §13.3). Holds a device-level minimum (start-threshold) and cap, the body region the device is worn on,
 * and per-feature settings keyed by feature identity.
 *
 * <p>The body region is per device, not per feature: a device sits in one place, and the extra precision
 * of tagging each motor separately is not worth the config and UI it would cost. The renderer routes an
 * effect to this device only when the effect's region overlaps this one, so region defaults to
 * {@link BodyRegion#WHOLE_BODY}, which overlaps everything and leaves routing unchanged until it is set.
 */
public final class DeviceSetting implements ConfigValue {

    /** Default start-threshold: a vibration-class output below this is lifted so it is felt on a motor
     *  with a dead zone. 0 disables the floor. */
    public static final double DEFAULT_MIN_LEVEL = 0.22;

    private final boolean enabled;
    private final double minLevel;
    private final double maxLevel;
    private final Map<String, FeatureSetting> features;
    // Null means the user has not assigned a region yet, which is distinct from an explicit whole-body
    // choice: both resolve to whole-body (so nothing is muted), but the UI shows "not set" for null so a
    // user can tell an untouched device from one they deliberately marked whole-body. Never coerced here,
    // or the distinction would be lost on the round-trip through config.
    private final BodyRegion bodyRegion;

    /** Convenience for a device with no region assigned yet (resolves to whole-body). */
    public DeviceSetting(boolean enabled, double minLevel, double maxLevel,
                         Map<String, FeatureSetting> features) {
        this(enabled, minLevel, maxLevel, features, null);
    }

    public DeviceSetting(boolean enabled, double minLevel, double maxLevel,
                         Map<String, FeatureSetting> features, BodyRegion bodyRegion) {
        this.enabled = enabled;
        this.minLevel = clamp01(minLevel);
        this.maxLevel = clamp01(maxLevel);
        this.features = features == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(features));
        this.bodyRegion = bodyRegion; // null kept as "not set"; bodyRegion() resolves it to whole-body
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

    /**
     * The body region this device is worn on, resolved for routing: {@link BodyRegion#WHOLE_BODY} when the
     * user has not assigned one, so an unassigned device reaches every effect rather than going silent.
     */
    public BodyRegion bodyRegion() {
        return bodyRegion == null ? BodyRegion.WHOLE_BODY : bodyRegion;
    }

    /**
     * Whether the user has explicitly assigned a region. False for an untouched device, which the editor
     * shows as "not set" even though it resolves to whole-body. Distinguishes an unset device from one a
     * user deliberately marked whole-body.
     */
    public boolean regionAssigned() {
        return bodyRegion != null;
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
                && Objects.equals(features, other.features)
                && bodyRegion == other.bodyRegion;
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, minLevel, maxLevel, features, bodyRegion);
    }

    @Override
    public String toString() {
        return "DeviceSetting[enabled=" + enabled + ", minLevel=" + minLevel + ", maxLevel=" + maxLevel
                + ", features=" + features + ", bodyRegion=" + bodyRegion + "]";
    }
}
