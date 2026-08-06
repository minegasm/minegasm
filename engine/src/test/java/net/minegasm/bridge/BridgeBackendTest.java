package net.minegasm.bridge;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minegasm.core.CouplingMode;
import net.minegasm.core.GameEventKind;
import net.minegasm.core.HapticLayer;
import net.minegasm.core.HapticPrimitive;
import net.minegasm.core.HapticRole;
import net.minegasm.core.HapticRoute;
import net.minegasm.core.HapticScene;
import net.minegasm.runtime.StopReason;
import net.minegasm.time.FakeClock;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Backend behavior: send when open and enabled, drop otherwise, and stop-all on every stop path. */
class BridgeBackendTest {

    private static final URI ENDPOINT = URI.create("ws://127.0.0.1:12345");

    private final FakeClock clock = new FakeClock(1_000_000_000L);
    private final FakeBridgeTransport transport = new FakeBridgeTransport();
    private final BridgeBackend backend = new BridgeBackend(() -> transport, ENDPOINT, clock);

    @Test
    void submitSendsAnEffectWhenOpenAndEnabled() {
        backend.start();
        backend.onGovernedScenes(java.util.Collections.singletonList(scene()), clock.nanoTime());
        assertEquals(1, transport.sent.size());
        assertEquals("effect", type(transport.sent.get(0)));
    }

    @Test
    void submitDropsWhenNoAdapterConnected() {
        backend.onGovernedScenes(java.util.Collections.singletonList(scene()), clock.nanoTime()); // never started, so transport not open
        assertTrue(transport.sent.isEmpty());
    }

    @Test
    void submitDropsWhenOutputDisabled() {
        backend.start();
        backend.setOutputEnabled(false);
        backend.onGovernedScenes(java.util.Collections.singletonList(scene()), clock.nanoTime());
        assertTrue(transport.sent.isEmpty(), "a panic-latched backend must not emit");
    }

    @Test
    void stopSendsStopAll() {
        backend.start();
        backend.stop(StopReason.PANIC);
        assertEquals(1, transport.sent.size());
        assertEquals("stop", type(transport.sent.get(0)));
    }

    @Test
    void pauseSendsStopAll() {
        backend.start();
        backend.pause();
        assertEquals("stop", type(transport.sent.get(0)));
    }

    @Test
    void closeClosesTheTransport() {
        backend.start();
        backend.close();
        assertTrue(!transport.open);
    }

    private static String type(String frame) {
        JsonObject o = JsonParser.parseString(frame).getAsJsonObject();
        return o.get("type").getAsString();
    }

    private static HapticScene scene() {
        HapticLayer layer = new HapticLayer("hit", HapticRole.IMPACT,
                new HapticPrimitive.Impulse(0.8f, 250, 10, 40), HapticRoute.buzzAll(),
                CouplingMode.MAX, 100, 0L, 300L * 1_000_000L, null);
        return new HapticScene("hurt:HURT", GameEventKind.HURT, 100,
                java.util.Collections.singletonList(layer), 1_000_000_000L,
                1_000_000_000L + 300L * 1_000_000L, null);
    }

    /** Records sent frames; sends are dropped while closed, matching the transport contract. */
    private static final class FakeBridgeTransport implements BridgeTransport {
        final List<String> sent = new ArrayList<>();
        boolean open;

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
}
