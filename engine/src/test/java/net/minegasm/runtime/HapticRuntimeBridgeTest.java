package net.minegasm.runtime;

import net.minegasm.backend.HapticBackend;
import net.minegasm.bridge.BridgeEndpoint;
import net.minegasm.bridge.BridgeTransport;
import net.minegasm.buttplug.ButtplugProvider;
import net.minegasm.config.HapticConfig;
import net.minegasm.config.RuntimeConfig;
import net.minegasm.core.CouplingMode;
import net.minegasm.core.GameEventKind;
import net.minegasm.core.HapticLayer;
import net.minegasm.core.HapticPrimitive;
import net.minegasm.core.HapticRole;
import net.minegasm.core.HapticRoute;
import net.minegasm.core.HapticScene;
import net.minegasm.pack.PackRegistry;
import net.minegasm.testsupport.FakeButtplugServer;
import net.minegasm.time.FakeClock;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Each injected bridge endpoint becomes its own backend, and several run at once (multi-endpoint). */
class HapticRuntimeBridgeTest {

    private final FakeClock clock = new FakeClock(1_000_000_000L);
    private final ButtplugProvider provider = new ButtplugProvider(new FakeButtplugServer(), "test");

    private static BridgeEndpoint endpoint(String id) {
        return new BridgeEndpoint(id, URI.create("tcp://127.0.0.1:12347"), NoopTransport::new);
    }

    @Test
    void bridgeBackendIsAddedWhenAnEndpointIsInjected() {
        HapticRuntime rt = new HapticRuntime(provider, clock, () -> RuntimeConfig.defaults(),
                new PackRegistry(), Collections.singletonList(endpoint("bridge")));

        var backends = rt.coordinator().backends();
        assertEquals(2, backends.size());
        assertTrue(backends.stream().anyMatch(b -> b.id().equals("bridge")));
        assertTrue(backends.stream().anyMatch(b -> b.id().equals("buttplug")));
    }

    @Test
    void everyEndpointBecomesItsOwnBackend() {
        HapticRuntime rt = new HapticRuntime(provider, clock, () -> RuntimeConfig.defaults(),
                new PackRegistry(), Arrays.asList(endpoint("xtoys"), endpoint("diy")));

        var backends = rt.coordinator().backends();
        assertEquals(3, backends.size(), "buttplug plus one backend per bridge endpoint");
        assertTrue(backends.stream().anyMatch(b -> b.id().equals("xtoys")));
        assertTrue(backends.stream().anyMatch(b -> b.id().equals("diy")));
    }

    @Test
    void noBridgeBackendWithoutAnEndpoint() {
        HapticRuntime rt = new HapticRuntime(provider, clock, () -> RuntimeConfig.defaults(),
                new PackRegistry(), null);

        var backends = rt.coordinator().backends();
        assertEquals(1, backends.size());
        assertEquals("buttplug", backends.get(0).id());
    }

    @Test
    void bridgeConfigDefaultsDisabledAndLoopback() {
        HapticConfig.Bridge bridge = HapticConfig.defaults().bridges().get(0);
        assertTrue(!bridge.enabled(), "bridge is off by default so existing configs are unaffected");
        assertTrue(!bridge.allowRemote());
        assertTrue(bridge.url().contains("127.0.0.1"), "default endpoint is loopback");
        assertEquals("tcp", bridge.transport(), "tcp is the shared default transport");
    }

    @Test
    void bridgeAddedWhilePanicLatchedStartsDisabled() {
        // A bridge added or re-pointed while master output is latched off must inherit that latch, or it
        // would forward scenes despite the visible global stop (its default is output-enabled).
        RecordingTransport transport = new RecordingTransport();
        HapticRuntime rt = new HapticRuntime(provider, clock, () -> RuntimeConfig.defaults(),
                new PackRegistry(), null);

        rt.worker().setOutputEnabled(false); // panic / master off
        rt.reconcileBridges(Collections.singletonList(
                new BridgeEndpoint("late", URI.create("tcp://127.0.0.1:12347"), () -> transport)));

        HapticBackend bridge = bridgeBackend(rt, "late");
        bridge.start(); // connect the adapter link so a drop here can only be the latch, not the socket
        bridge.onGovernedScenes(Collections.singletonList(scene("a", clock.nanoTime())), clock.nanoTime());
        assertFalse(transport.effects() > 0, "a bridge added while latched off must not forward scenes");

        rt.worker().setOutputEnabled(true); // panic cleared / resumed
        bridge.onGovernedScenes(Collections.singletonList(scene("b", clock.nanoTime())), clock.nanoTime());
        assertTrue(transport.effects() > 0, "once output resumes the bridge forwards normally");

        rt.shutdown(); // stop the bridge's reconnect supervisor
    }

    private static HapticBackend bridgeBackend(HapticRuntime rt, String id) {
        return rt.coordinator().backends().stream()
                .filter(b -> b.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no bridge backend " + id));
    }

    private static HapticScene scene(String id, long now) {
        HapticLayer layer = new HapticLayer("l", HapticRole.IMPACT,
                new HapticPrimitive.Impulse(0.8f, 250, 10, 40), HapticRoute.buzzAll(),
                CouplingMode.MAX, 100, 0L, 300L * 1_000_000L, null);
        return new HapticScene(id, GameEventKind.HURT, 100, Collections.singletonList(layer),
                now, now + 300L * 1_000_000L, null);
    }

    /** An open transport that counts the effect frames it is asked to send. */
    private static final class RecordingTransport implements BridgeTransport {
        private final List<String> sent = new ArrayList<>();
        private boolean open;

        int effects() {
            int n = 0;
            for (String f : sent) {
                if (f.contains("\"type\":\"effect\"")) {
                    n++;
                }
            }
            return n;
        }

        @Override
        public CompletionStage<Void> connect(URI uri, Consumer<String> onMessage,
                                             Consumer<Throwable> onClose) {
            open = true;
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> send(String frame) {
            if (open) {
                sent.add(frame);
            }
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public boolean isOpen() {
            return open;
        }

        @Override
        public void close() {
            open = false;
        }
    }

    private static final class NoopTransport implements BridgeTransport {
        @Override
        public CompletionStage<Void> connect(URI uri, Consumer<String> onMessage,
                                              Consumer<Throwable> onClose) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> send(String frame) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public boolean isOpen() {
            return false;
        }

        @Override
        public void close() {
        }
    }
}
