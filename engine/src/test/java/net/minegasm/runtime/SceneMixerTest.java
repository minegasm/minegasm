package net.minegasm.runtime;

import net.minegasm.config.MinegasmMode;
import net.minegasm.config.RecipePackId;
import net.minegasm.config.RuntimeConfig;
import net.minegasm.core.CouplingMode;
import net.minegasm.core.DeliveryMode;
import net.minegasm.core.GameEventKind;
import net.minegasm.core.HapticLayer;
import net.minegasm.core.HapticPrimitive;
import net.minegasm.core.HapticRole;
import net.minegasm.core.HapticRoute;
import net.minegasm.core.HapticScene;
import net.minegasm.config.DeviceSetting;
import net.minegasm.core.OutputKind;
import net.minegasm.render.SafetyCaps;
import net.minegasm.core.Priorities;
import net.minegasm.device.DeviceRegistrySnapshot;
import net.minegasm.render.EndpointTarget;
import net.minegasm.testsupport.Configs;
import net.minegasm.testsupport.Devices;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SceneMixerTest {

    private static final long MS = 1_000_000L;
    private final RuntimeConfig cfg = Configs.enabled(MinegasmMode.IMMERSION, RecipePackId.BALANCED);

    private HapticScene scene(String id, HapticLayer layer, long created, long expiry) {
        return new HapticScene(id, GameEventKind.ATTACK, layer.priority(), List.of(layer),
                created, created + expiry, null);
    }

    private HapticLayer vibeLayer(String id, float level, CouplingMode coupling, int priority) {
        var impulse = new HapticPrimitive.Impulse(level, 200, 8, 40);
        return new HapticLayer(id, HapticRole.IMPACT, impulse, HapticRoute.buzzAll(),
                coupling, priority, 0, 250 * MS, null);
    }

    @Test
    void hwPositionStrokeAlternatesEndpointsWithHalfPeriodDuration() {
        // A continuous stroke (period 800ms) on a linear stroker must produce alternating endpoint
        // waypoints with a half-period move duration, not a per-cycle crawl around center.
        HapticRoute motion = new HapticRoute(
                EnumSet.of(OutputKind.HW_POSITION_WITH_DURATION, OutputKind.POSITION),
                Collections.emptySet(), Collections.emptySet(), Collections.emptySet(),
                DeliveryMode.SUPPLEMENTAL);
        HapticPrimitive.Oscillation osc = new HapticPrimitive.Oscillation(1.0f, 800, Integer.MAX_VALUE);
        HapticLayer layer = new HapticLayer("stroke", HapticRole.TEXTURE, osc, motion,
                CouplingMode.MAX, Priorities.MINING_TEXTURE, 0, 3_600_000L * MS, "stroke");
        HapticScene strokeScene = new HapticScene("stroke", GameEventKind.AMBIENT,
                Priorities.MINING_TEXTURE, List.of(layer), 0, 3_600_000L * MS, "stroke");
        RuntimeConfig motionCfg = Configs.withMotion(MinegasmMode.IMMERSION, RecipePackId.BALANCED);
        DeviceRegistrySnapshot snap = Devices.registryWith(Devices.hwPosition(0, "stroker"));

        SceneMixer mixer = new SceneMixer();
        SceneStore store = new SceneStore();
        store.add(strokeScene);
        EndpointTarget atStart = only(mixer.render(store.snapshot(), snap, motionCfg, 0));
        store.clear();
        store.add(strokeScene);
        EndpointTarget atHalf = only(mixer.render(store.snapshot(), snap, motionCfg, 400 * MS));

        assertTrue(atStart.level() > 0.6f, "first half strokes toward the high bound");
        assertTrue(atHalf.level() < 0.4f, "second half strokes toward the low bound");
        assertEquals(400, atHalf.durationMs().intValue(),
                "move duration is the half-period, not the scene length");
    }

    private EndpointTarget only(Map<String, EndpointTarget> targets) {
        assertEquals(1, targets.size());
        return targets.values().iterator().next();
    }

    @Test
    void vibrationImpulseRoutesToFeature() {
        SceneMixer mixer = new SceneMixer();
        SceneStore store = new SceneStore();
        store.add(scene("a", vibeLayer("l", 0.8f, CouplingMode.MAX, Priorities.HURT), 0, 250 * MS));
        Map<String, EndpointTarget> targets =
                mixer.render(store.snapshot(), Devices.singleVibrate(), cfg, 20 * MS);
        assertEquals(1, targets.size());
        EndpointTarget t = targets.values().iterator().next();
        assertEquals(0.8f, t.level(), 1e-3);
    }

    @Test
    void weakVibrationLiftsToTheDeviceStartThreshold() {
        // 0.10 is below the default 0.22 start-threshold, so it is lifted to be felt on a motor with a
        // dead zone; the default device setting supplies the threshold.
        SceneMixer mixer = new SceneMixer();
        SceneStore store = new SceneStore();
        store.add(scene("weak", vibeLayer("l", 0.10f, CouplingMode.MAX, Priorities.HURT), 0, 250 * MS));
        EndpointTarget t = only(mixer.render(store.snapshot(), Devices.singleVibrate(), cfg, 20 * MS));
        assertEquals((float) DeviceSetting.DEFAULT_MIN_LEVEL, t.level(), 1e-3);
    }

    @Test
    void startThresholdAppliesToStrengthKindsNotPosition() {
        // The floor is a motor start-threshold. It must never touch position/stroker outputs, whose
        // level is a travel coordinate; flooring one would push the stroker off its neutral.
        assertTrue(SafetyCaps.isStrengthKind(OutputKind.VIBRATE));
        assertFalse(SafetyCaps.isStrengthKind(OutputKind.POSITION));
        assertFalse(SafetyCaps.isStrengthKind(OutputKind.HW_POSITION_WITH_DURATION));
    }

    @Test
    void disabledOutputKindNotRendered() {
        SceneMixer mixer = new SceneMixer();
        SceneStore store = new SceneStore();
        store.add(scene("a", vibeLayer("l", 0.8f, CouplingMode.MAX, Priorities.HURT), 0, 250 * MS));
        // Device only has Oscillate; with Oscillate forced off in policy, nothing routes.
        RuntimeConfig oscOff =
                Configs.withOutputOff(MinegasmMode.IMMERSION, RecipePackId.BALANCED, "Oscillate");
        DeviceRegistrySnapshot oscOnly = Devices.registryWith(Devices.oscillateOnly(0, "osc"));
        assertTrue(mixer.render(store.snapshot(), oscOnly, oscOff, 20 * MS).isEmpty());
    }

    @Test
    void exclusiveLayerDominatesHigherLevel() {
        SceneMixer mixer = new SceneMixer();
        SceneStore store = new SceneStore();
        // A louder non-exclusive layer and a quieter exclusive higher-priority layer collide.
        store.add(scene("loud", vibeLayer("loud", 0.9f, CouplingMode.MAX, Priorities.MINING_TEXTURE),
                0, 250 * MS));
        store.add(scene("excl", vibeLayer("excl", 0.3f, CouplingMode.EXCLUSIVE, Priorities.EXPLOSION),
                0, 250 * MS));
        Map<String, EndpointTarget> targets =
                mixer.render(store.snapshot(), Devices.singleVibrate(), cfg, 20 * MS);
        EndpointTarget t = targets.values().iterator().next();
        assertEquals(0.3f, t.level(), 1e-3, "exclusive high-priority layer ducks the louder one");
    }

    @Test
    void higherPriorityExclusiveDucksALouderExclusive() {
        SceneMixer mixer = new SceneMixer();
        SceneStore store = new SceneStore();
        // Two exclusive layers collide: the quieter one has the higher priority and must win, so a loud
        // low-priority exclusive can't override a high-priority warning-style duck (review P1-6).
        store.add(scene("loud", vibeLayer("loud", 0.9f, CouplingMode.EXCLUSIVE, Priorities.MINING_TEXTURE),
                0, 250 * MS));
        store.add(scene("warn", vibeLayer("warn", 0.3f, CouplingMode.EXCLUSIVE, Priorities.EXPLOSION),
                0, 250 * MS));
        Map<String, EndpointTarget> targets =
                mixer.render(store.snapshot(), Devices.singleVibrate(), cfg, 20 * MS);
        EndpointTarget t = targets.values().iterator().next();
        assertEquals(0.3f, t.level(), 1e-3, "the higher-priority exclusive wins regardless of level");
    }

    @Test
    void expiredLayerProducesNoTarget() {
        SceneMixer mixer = new SceneMixer();
        SceneStore store = new SceneStore();
        store.add(scene("a", vibeLayer("l", 0.8f, CouplingMode.MAX, Priorities.HURT), 0, 250 * MS));
        store.update(300 * MS);
        assertTrue(mixer.render(store.snapshot(), Devices.singleVibrate(), cfg, 300 * MS).isEmpty());
    }
}
