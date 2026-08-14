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
import net.minegasm.core.BodyRegion;
import net.minegasm.core.LogicalDestination;
import net.minegasm.core.OutputClass;
import net.minegasm.device.DeviceRegistrySnapshot;
import net.minegasm.render.EndpointTarget;
import net.minegasm.testsupport.Configs;
import net.minegasm.testsupport.Devices;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the P1-3 invariant that makes the central resolver worth having: the bridge and the Buttplug mixer
 * render one already-resolved set, so a layer the governor suppresses reaches neither backend. If a future
 * change re-adds a per-backend resolver, or the governor stops resolving, this fails rather than letting
 * the two backends drive hardware differently. Runs entirely in JUnit; the hardware feel pass is separate.
 */
class CrossBackendResolutionTest {

    private static final long MS = 1_000_000L;

    private static HapticScene sameRoleScene(String key, CouplingMode coupling, int priority, float level) {
        HapticLayer layer = new HapticLayer(key, HapticRole.IMPACT,
                new HapticPrimitive.Hold(level, 250, 0, 0), HapticRoute.buzzAll(),
                coupling, priority, 0, 250 * MS, key);
        return new HapticScene(key, GameEventKind.AMBIENT, priority,
                Collections.singletonList(layer), 0, 250 * MS, key);
    }

    @Test
    void aLayerTheGovernorSuppressesReachesNeitherBackend() {
        // A quieter, higher-priority exclusive collides with a louder, lower-priority layer on one role.
        SceneGovernor gov = new SceneGovernor();
        gov.submit(sameRoleScene("excl", CouplingMode.EXCLUSIVE, 100, 0.3f), 0);
        gov.submit(sameRoleScene("loud", CouplingMode.MAX, 10, 0.9f), 0);

        GovernedOutput output = gov.resolve(20 * MS, false, false);
        List<HapticScene> resolved = output.scenes();

        // The bridge renders the resolved set to per-role levels.
        assertEquals(0.3f, output.destinations().levels().values().iterator().next(), 1e-6f,
                "the bridge sees only the surviving exclusive level, never the suppressed 0.9");

        // The Buttplug mixer renders the same resolved set to per-feature targets.
        RuntimeConfig cfg = Configs.enabled(MinegasmMode.IMMERSION, RecipePackId.BALANCED);
        DeviceRegistrySnapshot devices = Devices.singleVibrate();
        Map<String, EndpointTarget> targets = new SceneMixer().render(resolved, devices, cfg, 20 * MS);
        assertEquals(1, targets.size());
        float rendered = targets.values().iterator().next().level();
        assertTrue(rendered < 0.9f,
                "the mixer never sees the suppressed louder layer either; both backends agree");
        assertEquals(0.3f, rendered, 1e-3f, "and it renders the surviving exclusive level");
    }

    @Test
    void bridgeSnapshotUsesTheSameCurrentTimingAsTheRenderer() {
        HapticLayer delayed = new HapticLayer("delayed", HapticRole.IMPACT,
                new HapticPrimitive.Hold(0.7f, 250, 0, 0), HapticRoute.buzzAll(),
                CouplingMode.MAX, 10, 100 * MS, 250 * MS, null);
        HapticScene scene = new HapticScene("delayed", GameEventKind.AMBIENT, 10,
                Collections.singletonList(delayed), 0, 500 * MS, null);
        SceneGovernor gov = new SceneGovernor();
        gov.submit(scene, 0);

        GovernedOutput before = gov.resolve(50 * MS, false, false);
        assertTrue(before.destinations().isAllZero(), "the bridge cannot start a delayed layer early");
        assertTrue(before.scenes().isEmpty(), "the renderer sees the same inactive set");

        GovernedOutput active = gov.resolve(150 * MS, false, false);
        LogicalDestination destination = new LogicalDestination(HapticRole.IMPACT,
                BodyRegion.WHOLE_BODY, OutputClass.STRENGTH);
        assertEquals(0.7f, active.destinations().level(destination), 1e-6f);

        RuntimeConfig cfg = Configs.enabled(MinegasmMode.IMMERSION, RecipePackId.BALANCED);
        Map<String, EndpointTarget> targets = new SceneMixer().render(active.scenes(),
                Devices.singleVibrate(), cfg, 150 * MS);
        assertEquals(0.7f, targets.values().iterator().next().level(), 1e-3f,
                "bridge and physical renderer sample the same active level");
    }
}
