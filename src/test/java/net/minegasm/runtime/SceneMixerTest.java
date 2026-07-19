package net.minegasm.runtime;

import net.minegasm.config.MinegasmMode;
import net.minegasm.config.RecipePackId;
import net.minegasm.config.RuntimeConfig;
import net.minegasm.core.CouplingMode;
import net.minegasm.core.GameEventKind;
import net.minegasm.core.HapticLayer;
import net.minegasm.core.HapticPrimitive;
import net.minegasm.core.HapticRole;
import net.minegasm.core.HapticRoute;
import net.minegasm.core.HapticScene;
import net.minegasm.core.Priorities;
import net.minegasm.device.DeviceRegistrySnapshot;
import net.minegasm.render.EndpointTarget;
import net.minegasm.testsupport.Configs;
import net.minegasm.testsupport.Devices;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SceneMixerTest {

    private static final long MS = 1_000_000L;
    private final RuntimeConfig cfg = Configs.enabled(MinegasmMode.HEDONIST, RecipePackId.BALANCED);
    private final FatigueGovernor governor = new FatigueGovernor();

    private HapticScene scene(String id, HapticLayer layer, long created, long expiry) {
        return new HapticScene(id, GameEventKind.ATTACK, layer.priority(), List.of(layer),
                created, created + expiry, null);
    }

    private HapticLayer vibeLayer(String id, float level, CouplingMode coupling, int priority) {
        var impulse = new HapticPrimitive.Impulse(level, 200, 8, 40);
        return new HapticLayer(id, HapticRole.IMPACT, impulse, HapticRoute.vibrateAll(),
                coupling, priority, 0, 250 * MS, null);
    }

    @Test
    void vibrationImpulseRoutesToFeature() {
        SceneMixer mixer = new SceneMixer();
        mixer.add(scene("a", vibeLayer("l", 0.8f, CouplingMode.MAX, Priorities.HURT), 0, 250 * MS));
        Map<String, EndpointTarget> targets =
                mixer.render(Devices.singleVibrate(), cfg, governor, false, 20 * MS);
        assertEquals(1, targets.size());
        EndpointTarget t = targets.values().iterator().next();
        assertEquals(0.8f, t.level(), 1e-3);
    }

    @Test
    void disabledOutputKindNotRendered() {
        SceneMixer mixer = new SceneMixer();
        mixer.add(scene("a", vibeLayer("l", 0.8f, CouplingMode.MAX, Priorities.HURT), 0, 250 * MS));
        // Device only has Oscillate, which is disabled by default -> nothing routed.
        DeviceRegistrySnapshot oscOnly = Devices.registryWith(Devices.oscillateOnly(0, "osc"));
        assertTrue(mixer.render(oscOnly, cfg, governor, false, 20 * MS).isEmpty());
    }

    @Test
    void exclusiveLayerDominatesHigherLevel() {
        SceneMixer mixer = new SceneMixer();
        // A louder non-exclusive layer and a quieter exclusive higher-priority layer collide.
        mixer.add(scene("loud", vibeLayer("loud", 0.9f, CouplingMode.MAX, Priorities.MINING_TEXTURE),
                0, 250 * MS));
        mixer.add(scene("excl", vibeLayer("excl", 0.3f, CouplingMode.EXCLUSIVE, Priorities.EXPLOSION),
                0, 250 * MS));
        Map<String, EndpointTarget> targets =
                mixer.render(Devices.singleVibrate(), cfg, governor, false, 20 * MS);
        EndpointTarget t = targets.values().iterator().next();
        assertEquals(0.3f, t.level(), 1e-3, "exclusive high-priority layer ducks the louder one");
    }

    @Test
    void expiredLayerProducesNoTarget() {
        SceneMixer mixer = new SceneMixer();
        mixer.add(scene("a", vibeLayer("l", 0.8f, CouplingMode.MAX, Priorities.HURT), 0, 250 * MS));
        mixer.update(300 * MS);
        assertTrue(mixer.render(Devices.singleVibrate(), cfg, governor, false, 300 * MS).isEmpty());
    }
}
