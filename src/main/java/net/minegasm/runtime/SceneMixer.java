package net.minegasm.runtime;

import net.minegasm.config.PositionCalibration;
import net.minegasm.config.RuntimeConfig;
import net.minegasm.core.CouplingMode;
import net.minegasm.core.HapticLayer;
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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Holds the currently active scenes and renders them to per-feature endpoint targets at a monotonic
 * time (brief §10.1). Continuous scenes are latest-wins by their key; discrete scenes coexist and
 * combine per endpoint by coupling/priority (MAX, plus EXCLUSIVE ducking). Damage merging happens
 * earlier in the aggregator, so the mixer stays free of event-name logic.
 *
 * <p>Confined to the haptic worker thread; not synchronised.
 */
public final class SceneMixer {

    /** Preference order when a route allows several output kinds on one feature. */
    private static final OutputKind[] KIND_PREFERENCE = {
            OutputKind.VIBRATE, OutputKind.HW_POSITION_WITH_DURATION, OutputKind.POSITION,
            OutputKind.OSCILLATE, OutputKind.ROTATE, OutputKind.CONSTRICT
    };

    private final List<HapticScene> discrete = new ArrayList<>();
    private final Map<String, HapticScene> continuous = new LinkedHashMap<>();

    public void add(HapticScene scene) {
        if (scene == null) {
            return;
        }
        if (scene.isContinuous()) {
            continuous.put(scene.continuousKey(), scene); // latest-wins
        } else {
            discrete.add(scene);
        }
    }

    /** Drop expired scenes. */
    public void update(long nowNs) {
        discrete.removeIf(s -> s.isExpired(nowNs));
        continuous.values().removeIf(s -> s.isExpired(nowNs));
    }

    public void clear() {
        discrete.clear();
        continuous.clear();
    }

    /** Shift every preserved scene forward after a real-time pause. */
    public void shiftTime(long deltaNs) {
        if (deltaNs <= 0) return;
        for (int i = 0; i < discrete.size(); i++) {
            discrete.set(i, shifted(discrete.get(i), deltaNs));
        }
        continuous.replaceAll((key, scene) -> shifted(scene, deltaNs));
    }

    private static HapticScene shifted(HapticScene scene, long deltaNs) {
        return new HapticScene(scene.sceneId(), scene.kind(), scene.priority(), scene.layers(),
                scene.createdAtNs() + deltaNs, scene.expiresAtNs() + deltaNs,
                scene.continuousKey());
    }

    public boolean isEmpty() {
        return discrete.isEmpty() && continuous.isEmpty();
    }

    public int activeSceneCount() {
        return discrete.size() + continuous.size();
    }

    /**
     * Compute the desired output for every enabled compatible feature at {@code nowNs}. Applies
     * routing, device/feature enablement and caps, fatigue attenuation, calibration for motion, and
     * per-endpoint coupling.
     */
    public Map<String, EndpointTarget> render(DeviceRegistrySnapshot snapshot, RuntimeConfig config,
                                              FatigueGovernor governor, boolean fatigueOn, long nowNs) {
        Map<String, EndpointTarget> targets = new LinkedHashMap<>();
        for (HapticScene scene : allScenes()) {
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
                if (fatigueOn) {
                    level *= governor.factor(layer.role());
                }
                if (level <= 0f) {
                    continue;
                }
                routeLayer(layer, level, snapshot, config, targets);
            }
        }
        return targets;
    }

    private List<HapticScene> allScenes() {
        List<HapticScene> all = new ArrayList<>(discrete.size() + continuous.size());
        all.addAll(discrete);
        all.addAll(continuous.values());
        return all;
    }

    private void routeLayer(HapticLayer layer, float level, DeviceRegistrySnapshot snapshot,
                            RuntimeConfig config, Map<String, EndpointTarget> targets) {
        for (HapticDevice device : snapshot.all()) {
            var deviceSetting = config.deviceSetting(device.identityKey());
            if (!deviceSetting.enabled()) {
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
                var featureSetting = deviceSetting.feature(featureKey);
                if (!featureSetting.enabled()) {
                    continue;
                }
                float capped = HapticMath.clamp01(level * (float) featureSetting.multiplier());
                capped = Math.min(capped, (float) deviceSetting.maxLevel());
                capped = Math.min(capped, SafetyCaps.cap(kind));
                if (capped <= 0f) {
                    continue;
                }
                EndpointTarget candidate = buildTarget(ref, kind, capped, layer, device, config);
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

    private EndpointTarget buildTarget(FeatureRef ref, OutputKind kind, float level, HapticLayer layer,
                                       HapticDevice device, RuntimeConfig config) {
        boolean exclusive = layer.coupling() == CouplingMode.EXCLUSIVE;
        if (kind == OutputKind.POSITION || kind == OutputKind.HW_POSITION_WITH_DURATION) {
            PositionCalibration calib = config.calibration(device.identityKey()).orElse(null);
            if (calib == null || !calib.enabled()) {
                return null; // motion never moves before calibration + opt-in (brief §11.2)
            }
            float travel = (float) (level); // already a bounded travel fraction
            float direction = calib.invert() ? -1f : 1f;
            float position = (float) HapticMath.clamp(
                    calib.neutral() + direction * travel * calib.gameplayTravelFraction(),
                    calib.minimum(), calib.maximum());
            Integer duration = kind.carriesDuration() ? layer.primitive().durationMs() : null;
            return new EndpointTarget(ref, kind, position, duration, layer.priority(), exclusive,
                    layer.role());
        }
        return new EndpointTarget(ref, kind, level, null, layer.priority(), exclusive, layer.role());
    }

    /** Resolve two targets on the same endpoint by exclusivity, then level, then priority. */
    private static EndpointTarget dominant(EndpointTarget a, EndpointTarget b) {
        if (a.exclusive() != b.exclusive()) {
            EndpointTarget ex = a.exclusive() ? a : b;
            EndpointTarget other = a.exclusive() ? b : a;
            return ex.priority() >= other.priority() ? ex : other;
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
