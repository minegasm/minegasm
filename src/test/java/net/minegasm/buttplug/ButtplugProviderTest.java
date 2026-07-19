package net.minegasm.buttplug;

import net.minegasm.core.OutputKind;
import net.minegasm.testsupport.FakeButtplugServer;
import net.minegasm.testsupport.FakeButtplugServer.FakeDevice;
import net.minegasm.testsupport.FakeButtplugServer.FakeFeature;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ButtplugProviderTest {

    private static final URI URL = URI.create("ws://127.0.0.1:12345");

    private FakeDevice vibe(int index, int gap) {
        return new FakeDevice(index, "Vibe" + index, null, gap,
                List.of(new FakeFeature("motor", Map.of("Vibrate", new int[]{0, 20}))));
    }

    @Test
    void connectNegotiatesAndListsDevices() {
        FakeButtplugServer server = new FakeButtplugServer().withDevices(vibe(0, 0));
        try (ButtplugProvider provider = new ButtplugProvider(server, "test")) {
            var status = provider.connect(URL).toCompletableFuture().join();
            assertEquals(ConnectionState.READY, status.state());
            assertEquals("4.0", status.negotiatedVersion().orElseThrow());
            assertEquals(1, provider.devices().all().size());
            assertEquals(1L, provider.devices().generation());
        }
    }

    @Test
    void emptyDeviceListIsConnectedNoDevices() {
        FakeButtplugServer server = new FakeButtplugServer();
        try (ButtplugProvider provider = new ButtplugProvider(server, "test")) {
            var status = provider.connect(URL).toCompletableFuture().join();
            assertEquals(ConnectionState.CONNECTED_NO_DEVICES, status.state());
        }
    }

    @Test
    void failedTransportConnectionReturnsToDisconnected() {
        ButtplugTransport unavailable = new ButtplugTransport() {
            @Override
            public java.util.concurrent.CompletionStage<Void> connect(URI uri,
                    java.util.function.Consumer<String> onMessage,
                    java.util.function.Consumer<Throwable> onClose) {
                return CompletableFuture.failedFuture(new java.net.ConnectException("refused"));
            }

            @Override public void send(String frame) {}
            @Override public boolean isOpen() { return false; }
            @Override public void close() {}
        };
        try (ButtplugProvider provider = new ButtplugProvider(unavailable, "test")) {
            assertThrows(RuntimeException.class,
                    () -> provider.connect(URL).toCompletableFuture().join());
            assertEquals(ConnectionState.DISCONNECTED, provider.status().state());
            assertEquals("refused", provider.status().lastError().orElseThrow());
        }
    }

    @Test
    void deviceListReplacementIncrementsGeneration() {
        FakeButtplugServer server = new FakeButtplugServer().withDevices(vibe(0, 0));
        try (ButtplugProvider provider = new ButtplugProvider(server, "test")) {
            provider.connect(URL).toCompletableFuture().join();
            long gen1 = provider.devices().generation();
            server.pushDeviceList(vibe(0, 0)); // reused index 0, new snapshot
            assertTrue(provider.devices().generation() > gen1,
                    "a reused index in a new list is a new generation");
        }
    }

    @Test
    void outputScaledAndRecorded() {
        FakeButtplugServer server = new FakeButtplugServer().withDevices(vibe(0, 0));
        try (ButtplugProvider provider = new ButtplugProvider(server, "test")) {
            provider.connect(URL).toCompletableFuture().join();
            long gen = provider.devices().generation();
            provider.send(new OutputCommand(0, 0, OutputKind.VIBRATE, 12, null, gen));
            FakeButtplugServer.Recorded last = server.recorded.get(server.recorded.size() - 1);
            assertEquals("OutputCmd", last.type());
            assertEquals(12, last.value());
        }
    }

    @Test
    void staleGenerationOutputDropped() {
        FakeButtplugServer server = new FakeButtplugServer().withDevices(vibe(0, 0));
        try (ButtplugProvider provider = new ButtplugProvider(server, "test")) {
            provider.connect(URL).toCompletableFuture().join();
            int before = server.recorded.size();
            provider.send(new OutputCommand(0, 0, OutputKind.VIBRATE, 12, null, 999L));
            assertEquals(before, server.recorded.size(), "stale-generation command must be dropped");
        }
    }

    @Test
    void stopAllSendsStopCmd() {
        FakeButtplugServer server = new FakeButtplugServer().withDevices(vibe(0, 0));
        try (ButtplugProvider provider = new ButtplugProvider(server, "test")) {
            provider.connect(URL).toCompletableFuture().join();
            provider.stop(StopSelection.all());
            assertEquals(1, server.stopAllCount);
        }
    }

    @Test
    void deviceAndFeatureStopsUseStopCmdSelections() {
        FakeButtplugServer server = new FakeButtplugServer().withDevices(vibe(0, 0), vibe(1, 0));
        try (ButtplugProvider provider = new ButtplugProvider(server, "test")) {
            provider.connect(URL).toCompletableFuture().join();
            provider.stop(new StopSelection.Device(1));
            provider.stop(new StopSelection.Feature(0, 0));
            assertEquals(0, server.stopAllCount, "scoped stops must not be stop-alls");
            assertTrue(server.recorded.stream().anyMatch(r ->
                    "StopCmd".equals(r.type()) && r.deviceIndex() == 1 && r.featureIndex() == -1));
            assertTrue(server.recorded.stream().anyMatch(r ->
                    "StopCmd".equals(r.type()) && r.deviceIndex() == 0 && r.featureIndex() == 0));
        }
    }

    @Test
    void errorResponseToOutputDoesNotThrow() {
        FakeButtplugServer server = new FakeButtplugServer().withDevices(vibe(0, 0));
        server.errorOnOutput = true;
        try (ButtplugProvider provider = new ButtplugProvider(server, "test")) {
            provider.connect(URL).toCompletableFuture().join();
            long gen = provider.devices().generation();
            provider.send(new OutputCommand(0, 0, OutputKind.VIBRATE, 5, null, gen));
            // No exception is the assertion; a late Error for a fire-and-forget output is ignored.
        }
    }

    @Test
    void unexpectedDisconnectClearsRegistry() {
        FakeButtplugServer server = new FakeButtplugServer().withDevices(vibe(0, 0));
        try (ButtplugProvider provider = new ButtplugProvider(server, "test")) {
            provider.connect(URL).toCompletableFuture().join();
            assertFalse(provider.devices().isEmpty());
            server.dropConnection(new RuntimeException("intiface died"));
            assertTrue(provider.devices().isEmpty());
            assertEquals(ConnectionState.DISCONNECTED, provider.status().state());
        }
    }
}
