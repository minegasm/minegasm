package net.minegasm.runtime;

import net.minegasm.buttplug.ButtplugProvider;
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
import net.minegasm.time.FakeClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * The worker fans the governed set to the bridge forwarder each cycle, and a stop forgets that
 * forwarding state so nothing can be re-sent after the bridge's stop frame (ADR-018).
 */
class HapticWorkerBridgeTest {

    private static final long MS = 1_000_000L;

    private FakeClock clock;
    private ButtplugProvider provider;
    private SceneGovernor governor;
    private HapticWorker worker;
    private final List<HapticScene> forwarded = new ArrayList<>();
    private final RuntimeConfig cfg = Configs.enabled(MinegasmMode.REACTION, RecipePackId.BALANCED);

    @BeforeEach
    void setUp() {
        clock = new FakeClock(1_000_000_000L);
        provider = new ButtplugProvider(new FakeButtplugServer(), "test");
        provider.connect(URI.create("ws://127.0.0.1:12345")).toCompletableFuture().join();
        governor = new SceneGovernor();
        worker = new HapticWorker(governor, provider, clock, () -> cfg);
        worker.setBridgeForwarder(new GovernedSceneForwarder(forwarded::add));
    }

    @AfterEach
    void tearDown() {
        provider.close();
    }

    private static HapticScene continuousScene(long createdNs) {
        HapticLayer layer = new HapticLayer("l", HapticRole.TEXTURE,
                new HapticPrimitive.Hold(0.5f, 600_000, 0, 0), HapticRoute.buzzAll(),
                CouplingMode.MAX, 0, 0, Long.MAX_VALUE / 4, "accumulation");
        return new HapticScene("accumulation", GameEventKind.AMBIENT, 0,
                Collections.singletonList(layer), createdNs, createdNs + 500 * MS, "accumulation");
    }

    @Test
    void cycleForwardsTheGovernedSetToTheBridge() {
        worker.offer(continuousScene(clock.nanoTime()));
        worker.cycle(clock.nanoTime());
        assertEquals(1, forwarded.size(), "the held scene reaches the bridge forwarder");
    }

    @Test
    void stopStopsForwardingAndForgetsState() {
        worker.offer(continuousScene(clock.nanoTime()));
        worker.cycle(clock.nanoTime());
        assertEquals(1, forwarded.size());

        worker.requestStop(StopReason.PANIC);
        clock.advanceMillis(15);
        worker.cycle(clock.nanoTime());
        assertEquals(1, forwarded.size(), "after a stop the empty governed set forwards nothing");
    }

    @Test
    void suppressedOutputDoesNotForward() {
        RuntimeConfig disabled = Configs.disabled();
        HapticWorker off = new HapticWorker(governor, provider, clock, () -> disabled);
        List<HapticScene> none = new ArrayList<>();
        off.setBridgeForwarder(new GovernedSceneForwarder(none::add));

        off.offer(continuousScene(clock.nanoTime()));
        off.cycle(clock.nanoTime());

        assertFalse(none.size() > 0, "with the master switch off nothing is forwarded to the bridge");
    }
}
