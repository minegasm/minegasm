package net.minegasm.client;

import net.minegasm.buttplug.ConnectionState;
import net.minegasm.buttplug.HapticProvider;
import net.minegasm.buttplug.OutputCommand;
import net.minegasm.buttplug.ProviderStatus;
import net.minegasm.buttplug.StopSelection;
import net.minegasm.device.DeviceRegistrySnapshot;
import net.minegasm.observe.ClientStateSnapshot;
import net.minegasm.time.FakeClock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers how {@link MinegasmClient} drives the reconnect supervisor: it polls the provider every tick,
 * retries a wanted-but-dropped connection, and stops retrying once the user disconnects. The supervisor
 * timing itself is unit-tested in {@code ReconnectSupervisorTest}; this pins the client-side wiring of
 * the desired-connected latch.
 */
class MinegasmClientReconnectTest {

    @TempDir
    Path temp;

    /** A provider that never truly connects (server down), so every attempt falls back to DISCONNECTED. */
    private static final class FakeProvider implements HapticProvider {
        final AtomicInteger connects = new AtomicInteger();
        final AtomicInteger polls = new AtomicInteger();
        final AtomicInteger stopScans = new AtomicInteger();
        volatile boolean closed;
        volatile ProviderStatus status = ProviderStatus.disconnected();

        @Override
        public CompletionStage<ProviderStatus> connect(URI uri) {
            connects.incrementAndGet();
            status = ProviderStatus.disconnected();
            return CompletableFuture.completedFuture(status);
        }

        @Override
        public void poll() {
            polls.incrementAndGet();
        }

        @Override
        public ProviderStatus status() {
            return status;
        }

        @Override
        public DeviceRegistrySnapshot devices() {
            return DeviceRegistrySnapshot.empty();
        }

        @Override
        public CompletionStage<Void> startScanning() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> stopScanning() {
            stopScans.incrementAndGet();
            return CompletableFuture.completedFuture(null); // leaves status SCANNING, so single-fire shows
        }

        @Override
        public CompletionStage<Void> refreshDevices() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> send(OutputCommand command) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> stop(StopSelection selection) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void setStatusListener(Consumer<ProviderStatus> listener) {
        }

        @Override
        public void setRegistryListener(Consumer<DeviceRegistrySnapshot> listener) {
        }

        @Override
        public void disconnect() {
            status = ProviderStatus.disconnected();
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    private MinegasmClient client(FakeProvider provider, FakeClock clock) {
        return new MinegasmClient(temp.resolve("minegasm.json"), provider, clock);
    }

    private void tickFor(MinegasmClient client, FakeClock clock, int ticks) {
        for (int i = 0; i < ticks; i++) {
            clock.advanceMillis(2_000); // step past the backoff windows (base 1s, capped 30s)
            client.onClientTickEnd(ClientStateSnapshot.empty(i));
        }
        client.shutdown();
    }

    @Test
    void setBackendSwapsBuildsTheNewBackendAndClosesTheOld() {
        java.util.Map<String, FakeProvider> built = new java.util.HashMap<>();
        java.util.function.Function<String, net.minegasm.buttplug.HapticProvider> factory =
                name -> built.computeIfAbsent(name, n -> new FakeProvider());
        MinegasmClient client = new MinegasmClient(temp.resolve("minegasm.json"), factory, new FakeClock());

        assertEquals("native", client.backend()); // the default the config was created with
        FakeProvider first = built.get("native");

        assertTrue(client.setBackend("buttplug4j"));
        assertEquals("buttplug4j", client.backend());
        assertTrue(built.containsKey("buttplug4j"), "the new backend is built through the factory");
        assertTrue(first.closed, "the old backend is closed on swap");

        assertFalse(client.setBackend("buttplug4j"), "switching to the active backend is a no-op");
        assertFalse(client.setBackend("bogus"), "an unknown backend name is rejected");
        client.shutdown();
    }

    @Test
    void pollsTheProviderEveryTick() {
        FakeProvider provider = new FakeProvider();
        MinegasmClient client = client(provider, new FakeClock());
        int before = provider.polls.get();
        client.onClientTickEnd(ClientStateSnapshot.empty(0));
        client.onClientTickEnd(ClientStateSnapshot.empty(1));
        assertEquals(before + 2, provider.polls.get());
        client.shutdown();
    }

    @Test
    void retriesAWantedDroppedConnection() {
        FakeProvider provider = new FakeProvider();
        FakeClock clock = new FakeClock();
        MinegasmClient client = client(provider, clock);
        client.start(); // autoConnect default true: connects once and latches "wanted"
        int afterStart = provider.connects.get();
        assertTrue(afterStart >= 1, "startup auto-connect should attempt once");

        tickFor(client, clock, 40);
        assertTrue(provider.connects.get() > afterStart,
                "a wanted connection that stays down should be retried");
    }

    @Test
    void stopsAScanThatSticksInScanningState() {
        FakeProvider provider = new FakeProvider();
        FakeClock clock = new FakeClock();
        MinegasmClient client = client(provider, clock);
        client.start();
        provider.status = new ProviderStatus(ConnectionState.SCANNING,
                java.util.Optional.empty(), 0, java.util.Optional.empty(), 0L);

        tickFor(client, clock, 20); // 40s of ticks, well past the ~10s scan window
        assertEquals(1, provider.stopScans.get(),
                "a scan stuck in SCANNING should be stopped exactly once");
    }

    @Test
    void stopsRetryingAfterAManualDisconnect() {
        FakeProvider provider = new FakeProvider();
        FakeClock clock = new FakeClock();
        MinegasmClient client = client(provider, clock);
        client.connect();      // latch "wanted"
        client.disconnect();   // user opts out: clear the latch
        int afterDisconnect = provider.connects.get();

        tickFor(client, clock, 40);
        assertEquals(afterDisconnect, provider.connects.get(),
                "a manual disconnect must not be reconnected against the user");
    }
}
