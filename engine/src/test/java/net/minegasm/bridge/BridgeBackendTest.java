package net.minegasm.bridge;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minegasm.core.CouplingMode;
import net.minegasm.backend.BackendOperation;
import net.minegasm.backend.BackendOutcomeState;
import net.minegasm.core.GameEventKind;
import net.minegasm.core.HapticLayer;
import net.minegasm.core.HapticPrimitive;
import net.minegasm.core.HapticRole;
import net.minegasm.core.HapticRoute;
import net.minegasm.core.HapticScene;
import net.minegasm.runtime.StopReason;
import net.minegasm.runtime.GovernedOutput;
import net.minegasm.runtime.SceneGovernor;
import net.minegasm.runtime.ResolvedDestinationSnapshot;
import net.minegasm.time.FakeClock;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Backend behavior: send when open and enabled, drop otherwise, and stop-all on every stop path. */
class BridgeBackendTest {

    private static final URI ENDPOINT = URI.create("ws://127.0.0.1:12345");

    private final FakeClock clock = new FakeClock(1_000_000_000L);
    private final FakeBridgeTransport transport = new FakeBridgeTransport();
    private final BridgeBackend backend = new BridgeBackend(() -> transport, ENDPOINT, clock);

    @Test
    void submitSendsAnAuthoritativeOutputSnapshotWhenOpenAndEnabled() {
        backend.start();
        clock.advanceMillis(10);
        backend.onGovernedOutput(output(scene(), clock.nanoTime()));
        assertEquals(1, transport.sent.size());
        JsonObject frame = JsonParser.parseString(transport.sent.get(0)).getAsJsonObject();
        assertEquals("output", frame.get("type").getAsString());
        assertEquals(0.8f, frame.getAsJsonArray("destinations").get(0).getAsJsonObject()
                .get("level").getAsFloat(), 1e-6f,
                "the sampled impulse is at full level after its attack");
    }

    @Test
    void aVanishedSceneRetractsByDroppingItsRoleToZero() {
        backend.start();
        clock.advanceMillis(10);
        backend.onGovernedOutput(output(scene(), clock.nanoTime()));
        assertEquals(0.8f, destinations(transport.sent.get(0)).get(0).getAsJsonObject()
                .get("level").getAsFloat(), 1e-6f);
        // The scene ends: the next governed set is empty, so the authoritative snapshot drops impact to 0
        // rather than leaving the adapter holding the last effect (second follow-up review P1-3).
        backend.onGovernedOutput(new GovernedOutput(java.util.Collections.emptyList(),
                new ResolvedDestinationSnapshot(2L, clock.nanoTime(), java.util.Collections.emptyMap())));
        assertEquals(2, transport.sent.size(), "an emptied set still sends: the retraction snapshot");
        assertEquals(0, destinations(transport.sent.get(1)).size());
    }

    @Test
    void submitDropsWhenNoAdapterConnected() {
        backend.onGovernedOutput(output(scene(), clock.nanoTime())); // never started, so transport not open
        assertTrue(transport.sent.isEmpty());
    }

    @Test
    void disconnectedTestReportsFailureWithoutPoisoningBackendHealth() {
        backend.test(scene(), clock.nanoTime());

        assertEquals(BackendOperation.TEST, backend.latestOutcome().operation());
        assertEquals(BackendOutcomeState.FAILED, backend.latestOutcome().state());
        assertNull(backend.unresolvedFailure(), "a diagnostic failure is action feedback, not quarantine");
    }

    @Test
    void disablingOutputStopsThenDropsOutput() {
        backend.start();
        backend.setOutputEnabled(false);
        assertEquals(1, transport.sent.size(), "disabling output sends a stop so the adapter zeros now");
        assertEquals("stop", type(transport.sent.get(0)));
        backend.onGovernedOutput(output(scene(), clock.nanoTime()));
        assertEquals(1, transport.sent.size(), "a disabled backend must not emit output");
    }

    @Test
    void aConnectedEnabledBridgeCountsAsBodyDriving() {
        assertTrue(!backend.isBodyDriving(), "an unconnected bridge does not drive the body");
        backend.start();
        assertTrue(backend.isBodyDriving(), "a connected, enabled bridge counts toward fatigue");
        backend.setOutputEnabled(false);
        assertTrue(!backend.isBodyDriving(), "a latched-off bridge does not count");
    }

    @Test
    void tracksDownstreamStateFromAdapterMessages() {
        backend.start();
        assertEquals(DownstreamState.UNKNOWN, backend.downstream(),
                "unknown until the adapter reports its onward link");

        transport.deliver("{\"v\":1,\"type\":\"hello\",\"downstream\":\"unavailable\"}");
        assertEquals(DownstreamState.UNAVAILABLE, backend.downstream());

        transport.deliver("{\"v\":1,\"type\":\"status\",\"downstream\":\"ready\"}");
        assertEquals(DownstreamState.READY, backend.downstream());

        backend.close();
        assertEquals(DownstreamState.UNKNOWN, backend.downstream(),
                "downstream is unknown again once the socket is gone");
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

    private static com.google.gson.JsonArray destinations(String frame) {
        return JsonParser.parseString(frame).getAsJsonObject().getAsJsonArray("destinations");
    }

    private static GovernedOutput output(HapticScene scene, long nowNs) {
        SceneGovernor governor = new SceneGovernor(1);
        governor.submit(scene, nowNs);
        return governor.resolve(nowNs, false, false);
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
        private Consumer<String> onMessage = m -> {};

        /** Simulate the adapter sending a line to the mod. */
        void deliver(String frame) {
            onMessage.accept(frame);
        }

        @Override
        public CompletionStage<Void> connect(URI uri, Consumer<String> onMessage,
                                              Consumer<Throwable> onClose) {
            this.onMessage = onMessage;
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
