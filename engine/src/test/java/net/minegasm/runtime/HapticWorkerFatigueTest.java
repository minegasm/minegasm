package net.minegasm.runtime;

import net.minegasm.buttplug.ButtplugProvider;
import net.minegasm.config.HapticConfig;
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
import net.minegasm.testsupport.Configs;
import net.minegasm.testsupport.FakeButtplugServer;
import net.minegasm.testsupport.FakeButtplugServer.FakeDevice;
import net.minegasm.testsupport.FakeButtplugServer.FakeFeature;
import net.minegasm.time.FakeClock;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fatigue accrual is gated on a device being connected (ADR-018): an enabled mod with nothing attached
 * never fatigues, so its ambient does not start pre-attenuated the moment a device appears. Observed
 * through the governed scene the worker fans to the bridge forwarder.
 */
class HapticWorkerFatigueTest {

    private static final long MS = 1_000_000L;

    // Fatigue protection on, so attenuation is observable; the two tests then differ only in whether a
    // device is connected, isolating the accrual gate.
    private final RuntimeConfig cfg = withFatigueProtection(
            Configs.enabled(MinegasmMode.REACTION, RecipePackId.BALANCED));

    private static RuntimeConfig withFatigueProtection(RuntimeConfig base) {
        HapticConfig raw = base.raw();
        HapticConfig.Global g = raw.global();
        return RuntimeConfig.of(new HapticConfig(raw.schemaVersion(), raw.profile(),
                new HapticConfig.Global(g.enabled(), g.intensity(), g.variation(), true,
                        g.pauseBehavior(), g.stopOnWorldUnload(), g.panicKey(), g.testMaxPercent(),
                        g.testMaxDurationMs(), g.unsafeTestMaxPercent(), g.unsafeTestMaxDurationMs()),
                raw.buttplug(), raw.events(), raw.outputPolicy(), raw.devices(),
                raw.positionCalibrations(), raw.accumulation(), raw.customIntensity(), raw.bridge()));
    }

    private static FakeDevice vibe() {
        return new FakeDevice(0, "Vibe", null, 0,
                List.of(new FakeFeature("motor", Map.of("Vibrate", new int[]{0, 20}))));
    }

    private static HapticScene sustainedTexture(long createdNs) {
        HapticLayer layer = new HapticLayer("l", HapticRole.TEXTURE,
                new HapticPrimitive.Hold(1.0f, 600_000, 0, 0), HapticRoute.buzzAll(),
                CouplingMode.MAX, 0, 0, Long.MAX_VALUE / 4, "tex");
        return new HapticScene("tex", GameEventKind.AMBIENT, 0, Collections.singletonList(layer),
                createdNs, createdNs + 500 * MS, "tex");
    }

    /** Run a sustained TEXTURE scene for 25 s and return the last governed amplitude the bridge saw. */
    private static float lastForwardedAmplitudeOver25s(ButtplugProvider provider, RuntimeConfig cfg) {
        FakeClock clock = new FakeClock(1_000_000_000L);
        provider.connect(URI.create("ws://127.0.0.1:12345")).toCompletableFuture().join();
        SceneGovernor governor = new SceneGovernor();
        HapticWorker worker = new HapticWorker(governor, provider, clock, () -> cfg);
        List<HapticScene> forwarded = new ArrayList<>();
        worker.setBridgeForwarder(new GovernedSceneForwarder(forwarded::add));

        worker.offer(sustainedTexture(clock.nanoTime()));
        for (int i = 0; i < 25; i++) {
            worker.offer(sustainedTexture(clock.nanoTime())); // continuous scene is re-submitted each tick
            worker.cycle(clock.nanoTime());
            clock.advanceMillis(1_000);
        }
        return forwarded.get(forwarded.size() - 1).layers().get(0).primitive().level();
    }

    @Test
    void noDeviceMeansNoFatigueAccrual() {
        ButtplugProvider provider = new ButtplugProvider(new FakeButtplugServer(), "test");
        try {
            assertEquals(1.0f, lastForwardedAmplitudeOver25s(provider, cfg), 1e-6,
                    "with nothing connected, sustained ambient never fatigues");
        } finally {
            provider.close();
        }
    }

    @Test
    void aConnectedDeviceAccruesFatigue() {
        ButtplugProvider provider = new ButtplugProvider(new FakeButtplugServer().withDevices(vibe()), "test");
        try {
            assertTrue(lastForwardedAmplitudeOver25s(provider, cfg) < 0.9f,
                    "with a device connected, sustained ambient fatigues past its budget");
        } finally {
            provider.close();
        }
    }
}
