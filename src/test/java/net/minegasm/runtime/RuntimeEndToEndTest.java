package net.minegasm.runtime;

import net.minegasm.buttplug.ButtplugProvider;
import net.minegasm.buttplug.OutputCommand;
import net.minegasm.config.MinegasmMode;
import net.minegasm.config.HapticConfig;
import net.minegasm.config.RecipePackId;
import net.minegasm.config.RuntimeConfig;
import net.minegasm.config.PauseBehavior;
import net.minegasm.core.MaterialFeel;
import net.minegasm.observe.ClientStateSnapshot;
import net.minegasm.testsupport.Configs;
import net.minegasm.testsupport.FakeButtplugServer;
import net.minegasm.testsupport.FakeButtplugServer.FakeDevice;
import net.minegasm.testsupport.FakeButtplugServer.FakeFeature;
import net.minegasm.time.FakeClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end pipeline: observation → intent → scene → mixer → scheduler → provider → fake server.
 * Exercises the acceptance scenarios from the test matrix (brief §14): hurt reaches every device,
 * held output is explicitly stopped, and pause stops immediately with no reassertion.
 */
class RuntimeEndToEndTest {

    private FakeClock clock;
    private FakeButtplugServer server;
    private ButtplugProvider provider;
    private HapticRuntime rt;
    private RuntimeConfig cfg;

    @BeforeEach
    void setUp() {
        clock = new FakeClock(1_000_000_000L);
        server = new FakeButtplugServer().withDevices(vibe(0), vibe(1));
        provider = new ButtplugProvider(server, "test");
        provider.connect(URI.create("ws://127.0.0.1:12345")).toCompletableFuture().join();
        cfg = Configs.enabled(MinegasmMode.MASOCHIST, RecipePackId.BALANCED);
        rt = new HapticRuntime(provider, clock, () -> cfg);
    }

    @AfterEach
    void tearDown() {
        provider.close();
    }

    private static FakeDevice vibe(int index) {
        return new FakeDevice(index, "Vibe" + index, null, 0,
                List.of(new FakeFeature("motor", Map.of("Vibrate", new int[]{0, 20}))));
    }

    private static ClientStateSnapshot snapshot(long tick, float health, boolean paused) {
        return snapshot(tick, health, paused, true);
    }

    private static ClientStateSnapshot snapshot(long tick, float health, boolean paused,
                                                boolean worldReady) {
        return new ClientStateSnapshot(health, 0f, 20, 0, 0f, 0, false, Optional.empty(), 0f,
                Optional.empty(), MaterialFeel.UNKNOWN, 0f, false, false, false, false,
                paused, worldReady, tick);
    }

    private void setStopOptions(boolean pause, boolean worldUnload) {
        var raw = cfg.raw();
        var global = raw.global();
        cfg = RuntimeConfig.of(new HapticConfig(raw.schemaVersion(), raw.identity(),
                new HapticConfig.Global(global.enabled(), global.intensity(), global.variation(),
                        global.fatigueProtection(),
                        pause ? PauseBehavior.STOP.name() : PauseBehavior.CONTINUE.name(),
                        worldUnload, global.panicKey(), global.testMaxPercent(),
                        global.testMaxDurationMs(), global.unsafeTestMaxPercent(),
                        global.unsafeTestMaxDurationMs()),
                raw.buttplug(), raw.events(), raw.outputPolicy(), raw.devices(),
                raw.positionCalibrations(), raw.accumulation(), raw.customIntensity()));
    }

    private void setPauseBehavior(PauseBehavior behavior) {
        var raw = cfg.raw();
        var global = raw.global();
        cfg = RuntimeConfig.of(new HapticConfig(raw.schemaVersion(), raw.identity(),
                new HapticConfig.Global(global.enabled(), global.intensity(), global.variation(),
                        global.fatigueProtection(), behavior.name(),
                        global.stopOnWorldUnload(), global.panicKey(),
                        global.testMaxPercent(), global.testMaxDurationMs(),
                        global.unsafeTestMaxPercent(), global.unsafeTestMaxDurationMs()),
                raw.buttplug(), raw.events(), raw.outputPolicy(), raw.devices(),
                raw.positionCalibrations(), raw.accumulation(), raw.customIntensity()));
    }

    private List<OutputCommand> triggerHurtAndPump() {
        rt.onClientTickEnd(snapshot(1, 20f, false)); // baseline
        clock.advanceMillis(50);
        rt.onClientTickEnd(snapshot(2, 14f, false)); // -6 HP => hurt scene offered
        clock.advanceMillis(20);
        return rt.pump(clock.nanoTime());
    }

    @Test
    void hurtReachesEveryDevice() {
        List<OutputCommand> commands = triggerHurtAndPump();
        assertEquals(2, commands.size(), "both devices should feel the hurt, not just the first");
        assertTrue(commands.stream().allMatch(c -> c.value() > 0));
        assertTrue(commands.stream().anyMatch(c -> c.deviceIndex() == 0));
        assertTrue(commands.stream().anyMatch(c -> c.deviceIndex() == 1));
    }

    @Test
    void heldOutputIsExplicitlyStoppedAfterExpiry() {
        triggerHurtAndPump();
        clock.advanceMillis(400); // past the 250 ms hurt expiry
        List<OutputCommand> stop = rt.pump(clock.nanoTime());
        assertEquals(2, stop.size());
        assertTrue(stop.stream().allMatch(c -> c.value() == 0), "endpoints must be driven to zero");
        // A further pump has nothing left to stop.
        assertTrue(rt.pump(clock.nanoTime()).isEmpty());
    }

    @Test
    void pauseStopsImmediatelyWithNoReassertion() {
        triggerHurtAndPump();
        int stopsBefore = server.stopAllCount;

        rt.onClientTickEnd(snapshot(3, 14f, true)); // paused
        assertEquals(stopsBefore + 1, server.stopAllCount, "pause sends StopCmd");

        clock.advanceMillis(20);
        assertTrue(rt.pump(clock.nanoTime()).isEmpty(), "no output after pause");
        clock.advanceMillis(500);
        assertTrue(rt.pump(clock.nanoTime()).isEmpty(), "still no reassertion later");
    }

    @Test
    void pauseDoesNotStopWhenOptionIsDisabled() {
        setStopOptions(false, true);
        triggerHurtAndPump();
        int stopsBefore = server.stopAllCount;

        rt.onClientTickEnd(snapshot(3, 14f, true));

        assertEquals(stopsBefore, server.stopAllCount,
                "disabled stop-on-pause must not send StopCmd");
    }

    @Test
    void worldUnloadStopsWhenOptionIsEnabled() {
        setStopOptions(false, true);
        triggerHurtAndPump();
        int stopsBefore = server.stopAllCount;

        rt.onClientTickEnd(snapshot(3, 14f, false, false));

        assertEquals(stopsBefore + 1, server.stopAllCount,
                "enabled stop-on-world-exit must send StopCmd independently of pause setting");
    }

    @Test
    void worldUnloadDoesNotStopWhenOptionIsDisabled() {
        setStopOptions(true, false);
        triggerHurtAndPump();
        int stopsBefore = server.stopAllCount;

        rt.onClientTickEnd(snapshot(3, 14f, false, false));

        assertEquals(stopsBefore, server.stopAllCount,
                "disabled stop-on-world-exit must not send StopCmd independently of pause setting");
    }

    @Test
    void worldUnloadStillStopsAfterNonStoppingPause() {
        setStopOptions(false, true);
        triggerHurtAndPump();
        int stopsBefore = server.stopAllCount;

        rt.onClientTickEnd(snapshot(3, 14f, true, true));
        assertEquals(stopsBefore, server.stopAllCount, "pause setting is disabled");

        rt.onClientTickEnd(snapshot(4, 14f, false, false));
        assertEquals(stopsBefore + 1, server.stopAllCount,
                "world unload must remain observable after pause made the game inactive");
    }

    @Test
    void pauseModeStopsHardwareFreezesSceneAndResumesRemainingTime() {
        setPauseBehavior(PauseBehavior.PAUSE);
        triggerHurtAndPump();
        int stopsBefore = server.stopAllCount;
        int scenesBefore = rt.worker().activeSceneCount();

        rt.onClientTickEnd(snapshot(3, 14f, true));
        assertEquals(stopsBefore + 1, server.stopAllCount, "pause stops hardware immediately");
        assertEquals(scenesBefore, rt.worker().activeSceneCount(), "scene is preserved");

        clock.advanceMillis(1_000); // much longer than the scene's unpaused remaining lifetime
        assertTrue(rt.pump(clock.nanoTime()).isEmpty(), "paused worker emits nothing");

        rt.onClientTickEnd(snapshot(4, 14f, false));
        assertFalse(rt.pump(clock.nanoTime()).isEmpty(), "preserved scene resumes after unpause");
    }

    @Test
    void disabledMasterSwitchProducesNoOutput() {
        cfg = Configs.disabled();
        rt.onClientTickEnd(snapshot(1, 20f, false));
        clock.advanceMillis(50);
        rt.onClientTickEnd(snapshot(2, 10f, false));
        clock.advanceMillis(20);
        assertTrue(rt.pump(clock.nanoTime()).isEmpty());
        assertFalse(server.recorded.stream().anyMatch(r -> "OutputCmd".equals(r.type())));
    }
}
