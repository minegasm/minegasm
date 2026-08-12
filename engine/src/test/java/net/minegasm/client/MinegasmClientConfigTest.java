package net.minegasm.client;

import net.minegasm.buttplug.ButtplugProvider;
import net.minegasm.config.ConfigStore;
import net.minegasm.config.HapticConfig;
import net.minegasm.runtime.StopReason;
import net.minegasm.testsupport.FakeButtplugServer;
import net.minegasm.time.FakeClock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A disable is a safety-reducing transition: it must take effect and stop hardware before the fallible
 * config write, and stay in effect even if that write throws. Otherwise a failed save could leave output
 * running against a config the user asked to turn off.
 */
class MinegasmClientConfigTest {

    private static HapticConfig enabledConfig() {
        HapticConfig d = HapticConfig.defaults();
        HapticConfig.Global g = d.global();
        HapticConfig.Global enabled = new HapticConfig.Global(true, g.intensity(), g.variation(),
                g.fatigueProtection(), g.pauseBehavior(), g.stopOnWorldUnload(), g.panicKey(),
                g.testMaxPercent(), g.testMaxDurationMs(),
                g.unsafeTestMaxPercent(), g.unsafeTestMaxDurationMs());
        return new HapticConfig(d.schemaVersion(), d.profile(), enabled, d.buttplug(), d.events(),
                d.outputPolicy(), d.devices(), d.positionCalibrations(), d.accumulation(),
                d.customIntensity(), d.bridges());
    }

    @Test
    void disableStaysAppliedWhenTheSaveFails(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("minegasm.json");
        new ConfigStore(file).save(enabledConfig()); // an existing, enabled config on disk

        MinegasmClient client = new MinegasmClient(file,
                new ButtplugProvider(new FakeButtplugServer(), "test"), new FakeClock(1_000_000_000L));
        assertTrue(client.config().enabled(), "starts enabled from the on-disk config");

        // Replace the config file with a non-empty directory so the next atomic save cannot complete.
        Files.delete(file);
        Files.createDirectory(file);
        Files.createFile(file.resolve("occupied"));

        assertThrows(RuntimeException.class, () -> client.setHapticsEnabled(false),
                "the failing save still surfaces as an error to the caller");

        assertFalse(client.config().enabled(),
                "the disable stays applied in memory even though the write failed");
        assertEquals(StopReason.CONFIG_RESET, client.runtime().worker().lastStopReason(),
                "hardware was stopped before the fallible save, not after it");
    }
}
