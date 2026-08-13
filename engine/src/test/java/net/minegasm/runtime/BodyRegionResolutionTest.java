package net.minegasm.runtime;

import net.minegasm.config.DeviceSetting;
import net.minegasm.config.MinegasmMode;
import net.minegasm.config.RecipePackId;
import net.minegasm.config.RuntimeConfig;
import net.minegasm.core.BodyRegion;
import net.minegasm.core.CouplingMode;
import net.minegasm.core.GameEventKind;
import net.minegasm.core.HapticLayer;
import net.minegasm.core.HapticPrimitive;
import net.minegasm.core.HapticRole;
import net.minegasm.core.HapticRoute;
import net.minegasm.core.HapticScene;
import net.minegasm.device.DeviceRegistrySnapshot;
import net.minegasm.device.HapticDevice;
import net.minegasm.render.EndpointTarget;
import net.minegasm.testsupport.Configs;
import net.minegasm.testsupport.Devices;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Phase 2 gate: two same-role effects in non-overlapping body regions must (a) not suppress each other
 * in the governor and (b) route to different devices by region. If this passes, region-scoped competition
 * is validated without hardware. See [[BodyRegion]] and {@link SceneGovernor}.
 */
class BodyRegionResolutionTest {

    private static final long MS = 1_000_000L;

    private static HapticScene regionScene(String key, BodyRegion region, int priority,
                                           CouplingMode coupling, float level) {
        HapticLayer layer = new HapticLayer(key, HapticRole.IMPACT,
                new HapticPrimitive.Hold(level, 250, 0, 0), HapticRoute.buzzAll(),
                coupling, priority, 0, 250 * MS, key, region);
        return new HapticScene(key, GameEventKind.AMBIENT, priority, List.of(layer), 0, 250 * MS, key);
    }

    private static boolean has(List<HapticScene> governed, String key) {
        return governed.stream().anyMatch(s -> s.sceneId().equals(key));
    }

    @Test
    void sameRoleDifferentRegionsNeitherSuppressesNorCrossRoutes() {
        // A higher-priority exclusive on the genital region and a lower-priority effect on the nipple region.
        SceneGovernor gov = new SceneGovernor();
        gov.submit(regionScene("gen", BodyRegion.GENITAL, 100, CouplingMode.EXCLUSIVE, 0.8f), 0);
        gov.submit(regionScene("nip", BodyRegion.NIPPLE, 50, CouplingMode.MAX, 0.5f), 0);

        // (a) The governor keeps both: the genital exclusive does not contain the nipple region.
        List<HapticScene> resolved = gov.govern(20 * MS, false, false);
        assertTrue(has(resolved, "gen"), "the exclusive survives");
        assertTrue(has(resolved, "nip"),
                "a same-role effect in a non-overlapping region is not suppressed by the exclusive");

        // (b) Two devices, one worn on each region. The renderer must route each effect only to its region's
        // device: without the region gate the genital exclusive would dominate the nipple device too.
        HapticDevice genitalDev = Devices.vibrate(0, "GenitalToy", 0, 0, 20);
        HapticDevice nippleDev = Devices.vibrate(1, "NippleToy", 0, 0, 20);
        DeviceRegistrySnapshot snap = Devices.registryWith(genitalDev, nippleDev);
        Map<String, DeviceSetting> devices = new HashMap<>();
        devices.put(genitalDev.identityKey(),
                new DeviceSetting(true, DeviceSetting.DEFAULT_MIN_LEVEL, 1.0, Map.of(), BodyRegion.GENITAL));
        devices.put(nippleDev.identityKey(),
                new DeviceSetting(true, DeviceSetting.DEFAULT_MIN_LEVEL, 1.0, Map.of(), BodyRegion.NIPPLE));
        RuntimeConfig cfg = Configs.withDeviceSettings(MinegasmMode.IMMERSION, RecipePackId.BALANCED, devices);

        Map<String, EndpointTarget> targets = new SceneMixer().render(resolved, snap, cfg, 20 * MS);

        assertEquals(2, targets.size(), "each device is driven by the effect for its own region");
        Map<Integer, Float> byDevice = new HashMap<>();
        for (EndpointTarget t : targets.values()) {
            byDevice.put(t.ref().deviceIndex(), t.level());
        }
        assertEquals(0.8f, byDevice.get(0), 1e-3f, "the genital device gets the genital exclusive");
        assertEquals(0.5f, byDevice.get(1), 1e-3f,
                "the nipple device keeps its own effect, not the genital exclusive that never reached it");
    }

    @Test
    void wholeBodyExclusiveStillOwnsTheWholeRole() {
        // A whole-body exclusive contains every region, so it suppresses a lower same-role layer anywhere,
        // exactly as before region existed (the default behavior is unchanged).
        SceneGovernor gov = new SceneGovernor();
        gov.submit(regionScene("all", BodyRegion.WHOLE_BODY, 100, CouplingMode.EXCLUSIVE, 0.8f), 0);
        gov.submit(regionScene("nip", BodyRegion.NIPPLE, 50, CouplingMode.MAX, 0.5f), 0);

        List<HapticScene> resolved = gov.govern(20 * MS, false, false);
        assertTrue(has(resolved, "all"));
        assertFalse(has(resolved, "nip"), "a whole-body exclusive contains the nipple region and suppresses it");
    }
}
