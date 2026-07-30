package net.minegasm.runtime;

import net.minegasm.backend.HapticBackend;
import net.minegasm.bridge.BridgeTransport;
import net.minegasm.buttplug.ButtplugProvider;
import net.minegasm.config.HapticConfig;
import net.minegasm.config.RuntimeConfig;
import net.minegasm.pack.PackRegistry;
import net.minegasm.testsupport.FakeButtplugServer;
import net.minegasm.time.FakeClock;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The coordinator gains a bridge backend only when a transport is injected (brief 0003 §3.4). */
class HapticRuntimeBridgeTest {

    private final FakeClock clock = new FakeClock(1_000_000_000L);
    private final ButtplugProvider provider = new ButtplugProvider(new FakeButtplugServer(), "test");

    @Test
    void bridgeBackendIsAddedWhenATransportIsInjected() {
        HapticRuntime rt = new HapticRuntime(provider, clock, () -> RuntimeConfig.defaults(),
                new PackRegistry(), new NoopTransport());

        var backends = rt.coordinator().backends();
        assertEquals(2, backends.size());
        assertTrue(backends.stream().anyMatch(b -> b.id().equals("bridge")));
        assertTrue(backends.stream().anyMatch(b -> b.id().equals("buttplug")));
    }

    @Test
    void noBridgeBackendWithoutATransport() {
        HapticRuntime rt = new HapticRuntime(provider, clock, () -> RuntimeConfig.defaults(),
                new PackRegistry(), null);

        var backends = rt.coordinator().backends();
        assertEquals(1, backends.size());
        assertEquals("buttplug", backends.get(0).id());
    }

    @Test
    void bridgeConfigDefaultsDisabledAndLoopback() {
        HapticConfig.Bridge bridge = HapticConfig.defaults().bridge();
        assertTrue(!bridge.enabled(), "bridge is off by default so existing configs are unaffected");
        assertTrue(!bridge.allowRemote());
        assertTrue(bridge.url().startsWith("ws://127.0.0.1"), "default endpoint is loopback");
    }

    private static final class NoopTransport implements BridgeTransport {
        @Override
        public CompletionStage<Void> connect(URI uri, Consumer<String> onMessage,
                                              Consumer<Throwable> onClose) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void send(String frame) {
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
