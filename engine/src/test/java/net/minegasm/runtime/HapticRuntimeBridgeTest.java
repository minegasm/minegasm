package net.minegasm.runtime;

import net.minegasm.bridge.BridgeEndpoint;
import net.minegasm.bridge.BridgeTransport;
import net.minegasm.buttplug.ButtplugProvider;
import net.minegasm.config.HapticConfig;
import net.minegasm.config.RuntimeConfig;
import net.minegasm.pack.PackRegistry;
import net.minegasm.testsupport.FakeButtplugServer;
import net.minegasm.time.FakeClock;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
