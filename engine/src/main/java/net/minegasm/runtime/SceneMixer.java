package net.minegasm.runtime;

import net.minegasm.config.DeviceSetting;
import net.minegasm.config.FeatureSetting;
import net.minegasm.config.PositionCalibration;
import net.minegasm.config.RuntimeConfig;
import net.minegasm.core.CouplingMode;
import net.minegasm.core.HapticLayer;
import net.minegasm.core.HapticPrimitive;
import net.minegasm.core.HapticScene;
import net.minegasm.core.OutputKind;
import net.minegasm.device.DeviceRegistrySnapshot;
import net.minegasm.device.FeatureRef;
import net.minegasm.device.HapticDevice;
import net.minegasm.device.HapticFeature;
import net.minegasm.render.EndpointTarget;
import net.minegasm.render.PrimitiveEvaluator;
import net.minegasm.render.SafetyCaps;
import net.minegasm.util.HapticMath;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders a snapshot of active scenes to per-feature endpoint targets at a monotonic time (brief
 * §10.1). Discrete and continuous scenes combine per endpoint by coupling/priority (MAX, plus
 * EXCLUSIVE ducking). Damage merging happens earlier in the aggregator, so the mixer stays free of
 * event-name logic. Scene-level bookkeeping (holding, coalescing, expiry) lives in {@link SceneStore};
 * this class is a pure function of the scene list handed to {@link #render}.
 *
 * <p>Confined to the haptic worker thread; not synchronised.
 */
public final class SceneMixer {

    /** Preference order when a route allows several output kinds on one feature. */
    private static final OutputKind[] KIND_PREFERENCE = {
            OutputKind.VIBRATE, OutputKind.HW_POSITION_WITH_DURATION, OutputKind.POSITION,
            OutputKind.OSCILLATE, OutputKind.ROTATE, OutputKind.CONSTRICT
    };

    /** Shortest full out-and-back stroke period; also the move-duration floor. Anti-jackhammer. */
    private static final long MIN_STROKE_PERIOD_MS = 700;

    /**
     * Compute the desired output for every enabled compatible feature at {@code nowNs} from the given
     * scene snapshot. Applies routing, device/feature enablement and caps, calibration for motion, and
     * per-endpoint coupling. Fatigue attenuation is already baked into the scenes by
     * {@link SceneGovernor#govern}, so this stage only reads the primitive levels it is handed.
     */
    public Map<String, EndpointTarget> render(List<HapticScene> scenes, DeviceRegistrySnapshot snapshot,
                                              RuntimeConfig config, long nowNs) {
        Map<String, EndpointTarget> targets = new LinkedHashMap<>();
        for (HapticScene scene : scenes) {
            if (scene.isExpired(nowNs)) {
                continue;
            }
            for (HapticLayer layer : scene.layers()) {
                long layerStart = scene.createdAtNs() + layer.startOffsetNs();
                long layerEnd = layerStart + layer.expiresAfterNs();
                if (nowNs < layerStart || nowNs >= layerEnd) {
                    continue;
                }
                float level = PrimitiveEvaluator.levelAt(layer.primitive(), nowNs - layerStart);
                if (level <= 0f) {
                    continue;
                }
                routeLayer(layer, level, nowNs - layerStart, snapshot, config, targets);
            }
        }
        return targets;
    }

    private void routeLayer(HapticLayer layer, float level, long elapsedNs,
                            DeviceRegistrySnapshot snapshot,
                            RuntimeConfig config, Map<String, EndpointTarget> targets) {
        for (HapticDevice device : snapshot.all()) {
            DeviceSetting deviceSetting = config.deviceSetting(device.identityKey());
            if (!deviceSetting.enabled()) {
                continue;
            }
            // Region gate: a device worn on one part of the body only receives effects whose region reaches
            // it. Both default to whole-body, which overlaps everything, so this is a no-op until the user
            // tags a device and an effect carries a specific region. This is where a region-scoped exclusive
            // gets its per-device effect: it reaches the device it overlaps, and a whole-body effect ducked
            // there by the mixer keeps playing on devices in other regions.
            if (!deviceSetting.bodyRegion().overlaps(layer.bodyRegion())) {
                continue;
            }
            for (HapticFeature feature : device.features().values()) {
                FeatureRef ref = new FeatureRef(device.deviceIndex(), feature.featureIndex(),
                        snapshot.generation());
                if (!layer.route().includes(ref)) {
                    continue;
                }
                OutputKind kind = chooseKind(layer, feature, config);
                if (kind == null) {
                    continue;
                }
                String featureKey = featureKey(kind, feature);
                FeatureSetting featureSetting = deviceSetting.feature(featureKey);
                if (!featureSetting.enabled()) {
                    continue;
                }
                float capped = HapticMath.clamp01(level * (float) featureSetting.multiplier());
                // Lift a vibration-class output to the device's start-threshold so it registers on a motor
                // with a dead zone. This runs after fatigue attenuation, so a fatigued ambient holds at the
                // threshold rather than fading to silence (matching the "always felt" intent); set a
                // device's minimum to 0 to let fatigue duck it away. Position/stroker outputs are travel
                // coordinates, not strengths, so they are never floored (that would shove the stroker off
                // its neutral).
                if (capped > 0f && SafetyCaps.isStrengthKind(kind)) {
                    capped = Math.max(capped, (float) deviceSetting.minLevel());
                }
                capped = Math.min(capped, (float) deviceSetting.maxLevel());
                capped = Math.min(capped, SafetyCaps.cap(kind));
                if (capped <= 0f) {
                    continue;
                }
                EndpointTarget candidate = buildTarget(ref, kind, capped, elapsedNs, layer, device, config);
                if (candidate == null) {
                    continue;
                }
                targets.merge(candidate.endpointKey(), candidate, SceneMixer::dominant);
            }
        }
    }

    /** Choose the best allowed, enabled output kind this feature supports; null if none. */
    private OutputKind chooseKind(HapticLayer layer, HapticFeature feature, RuntimeConfig config) {
        for (OutputKind kind : KIND_PREFERENCE) {
            if (!layer.route().allows(kind) || !feature.supports(kind)) {
                continue;
            }
            if (!config.outputEnabled(kind)) {
                continue; // experimental/disabled kinds are gated here
            }
            return kind;
        }
        return null;
    }

    private EndpointTarget buildTarget(FeatureRef ref, OutputKind kind, float level, long elapsedNs,
                                       HapticLayer layer, HapticDevice device, RuntimeConfig config) {
        boolean exclusive = layer.coupling() == CouplingMode.EXCLUSIVE;
        if (kind == OutputKind.POSITION || kind == OutputKind.HW_POSITION_WITH_DURATION) {
            // Motion works out of the box with a conservative safe default; an explicit, enabled
            // calibration overrides it. Physical travel is bounded by gameplayTravelFraction (<= 0.20)
            // and the [minimum, maximum] clamp regardless of the incoming level.
            PositionCalibration calib = config.calibration(device.identityKey())
                    .filter(PositionCalibration::enabled)
                    .orElseGet(PositionCalibration::safeDefault);
            float direction = calib.invert() ? -1f : 1f;
            float reach = (float) (level * calib.gameplayTravelFraction()); // bounded stroke depth
            if (layer.primitive() instanceof HapticPrimitive.Oscillation) {
                return strokeTarget(ref, kind, (HapticPrimitive.Oscillation) layer.primitive(),
                        elapsedNs, calib, direction, reach, exclusive, layer);
            }
            // One-shot move for non-oscillation motion primitives.
            float position = (float) HapticMath.clamp(calib.neutral() + direction * reach,
                    calib.minimum(), calib.maximum());
            Integer duration = kind.carriesDuration() ? layer.primitive().durationMs() : null;
            return new EndpointTarget(ref, kind, position, duration, layer.priority(), exclusive,
                    layer.role());
        }
        return new EndpointTarget(ref, kind, level, null, layer.priority(), exclusive, layer.role());
    }

    /**
     * Rhythmic stroke waypoints from an {@link HapticPrimitive.Oscillation}. For
     * {@code HwPositionWithDuration} it alternates between the two travel-window endpoints with a move
     * duration of the remaining half-period, so the device interpolates a smooth out-and-back; for plain
     * {@code Position} it emits the sampled sine position. The period is floored at
     * {@link #MIN_STROKE_PERIOD_MS} so a device can never be driven faster than that.
     */
    private EndpointTarget strokeTarget(FeatureRef ref, OutputKind kind,
                                        HapticPrimitive.Oscillation osc, long elapsedNs,
                                        PositionCalibration calib, float direction, float reach,
                                        boolean exclusive, HapticLayer layer) {
        long period = Math.max(MIN_STROKE_PERIOD_MS, osc.periodMs());
        double elapsedMs = elapsedNs / 1_000_000.0;
        float high = (float) HapticMath.clamp(calib.neutral() + direction * reach,
                calib.minimum(), calib.maximum());
        float low = (float) HapticMath.clamp(calib.neutral() - direction * reach,
                calib.minimum(), calib.maximum());
        if (kind == OutputKind.HW_POSITION_WITH_DURATION) {
            long half = Math.max(1, period / 2);
            double phase = elapsedMs % period;
            float target = phase < half ? high : low;
            int duration = (int) Math.max(1, half - (phase % half));
            return new EndpointTarget(ref, kind, target, duration, layer.priority(), exclusive,
                    layer.role());
        }
        double sine = Math.sin(2.0 * Math.PI * (elapsedMs % period) / period);
        float target = (float) HapticMath.clamp(calib.neutral() + direction * reach * (float) sine,
                calib.minimum(), calib.maximum());
        return new EndpointTarget(ref, kind, target, null, layer.priority(), exclusive, layer.role());
    }

    /**
     * Resolve two targets on the same endpoint. One exclusive vs one not: the exclusive wins unless the
     * other outranks it. Two exclusive candidates duck by priority first (a quieter high-priority warning
     * must beat a louder low-priority exclusive), level only breaking a tie. Two non-exclusive: loudest
     * wins, priority breaks a tie (review P1-6).
     */
    private static EndpointTarget dominant(EndpointTarget a, EndpointTarget b) {
        if (a.exclusive() != b.exclusive()) {
            EndpointTarget ex = a.exclusive() ? a : b;
            EndpointTarget other = a.exclusive() ? b : a;
            return ex.priority() >= other.priority() ? ex : other;
        }
        if (a.exclusive()) {
            if (a.priority() != b.priority()) {
                return a.priority() > b.priority() ? a : b;
            }
            return a.level() >= b.level() ? a : b;
        }
        if (a.level() != b.level()) {
            return a.level() >= b.level() ? a : b;
        }
        return a.priority() >= b.priority() ? a : b;
    }

    private static String featureKey(OutputKind kind, HapticFeature feature) {
        return kind.wireName() + "|" + feature.featureIndex() + "|" + feature.description();
    }
}
