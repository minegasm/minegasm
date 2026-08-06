package net.minegasm.config;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A non-default config section must survive an in-memory rebuild. The editor screens and toggles
 * reconstruct the whole config and preserve every section they do not touch; this guards that the
 * bridge list is one of them (it is new, so a careless rebuild would reset it to defaults).
 */
class BridgePreservationTest {

    @Test
    void enabledBridgeSurvivesARebuildThatDoesNotTouchIt() {
        HapticConfig base = HapticConfig.defaults();
        HapticConfig.Bridge enabled =
                new HapticConfig.Bridge("xtoys", true, "tcp://127.0.0.1:5000", "tcp", true);
        List<HapticConfig.Bridge> bridges = Collections.singletonList(enabled);
        HapticConfig withBridge = new HapticConfig(base.schemaVersion(), base.profile(), base.global(),
                base.buttplug(), base.events(), base.outputPolicy(), base.devices(),
                base.positionCalibrations(), base.accumulation(), base.customIntensity(), bridges);

        // Rebuild the way an editor screen does: change one unrelated section, preserve the rest.
        HapticConfig.Global disabledOutput = new HapticConfig.Global(false, 0.5, 0.0, true, "PAUSE",
                true, "KEY", 50, 2_000, 100, 10_000);
        HapticConfig rebuilt = new HapticConfig(withBridge.schemaVersion(), withBridge.profile(),
                disabledOutput, withBridge.buttplug(), withBridge.events(), withBridge.outputPolicy(),
                withBridge.devices(), withBridge.positionCalibrations(), withBridge.accumulation(),
                withBridge.customIntensity(), withBridge.bridges());

        assertEquals(1, rebuilt.bridges().size());
        HapticConfig.Bridge kept = rebuilt.bridges().get(0);
        assertTrue(kept.enabled(), "bridge must not be reset by an unrelated edit");
        assertTrue(kept.allowRemote());
        assertEquals("tcp://127.0.0.1:5000", kept.url());
        assertEquals(enabled, kept);
    }
}
