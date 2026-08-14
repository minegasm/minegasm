package net.minegasm.buttplug.b4j;

import io.github.blackspherefollower.buttplug4j.client.ButtplugClientDevice;
import net.minegasm.buttplug.StopSelection;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Buttplug4jProviderBoundaryTest {

    @Test
    void lateLibraryStopFailureCompletesExceptionally() {
        FakeClient client = new FakeClient();
        client.stopFailure = new IllegalStateException("hardware rejected stop");
        Buttplug4jProvider provider = connected(client);
        try {
            CompletionStage<Void> stop = provider.stop(StopSelection.all());
            assertThrows(CompletionException.class, () -> stop.toCompletableFuture().join());
        } finally {
            provider.close();
        }
    }

    @Test
    void blockingLibraryStopRemainsPendingUntilTheBoundaryReturns() throws Exception {
        FakeClient client = new FakeClient();
        client.blockStop = true;
        Buttplug4jProvider provider = connected(client);
        try {
            CompletionStage<Void> stop = provider.stop(StopSelection.all());
            assertTrue(client.stopEntered.await(2, TimeUnit.SECONDS));
            assertFalse(stop.toCompletableFuture().isDone(),
                    "the provider stage represents the real blocking library call");
            client.releaseStop.countDown();
            stop.toCompletableFuture().get(2, TimeUnit.SECONDS);
        } finally {
            client.releaseStop.countDown();
            provider.close();
        }
    }

    private static Buttplug4jProvider connected(FakeClient client) {
        Buttplug4jProvider provider = new Buttplug4jProvider(client);
        provider.connect(URI.create("ws://127.0.0.1:12345"))
                .toCompletableFuture().join();
        return provider;
    }

    private static final class FakeClient implements B4jClientFacade {
        volatile boolean connected;
        volatile boolean blockStop;
        volatile RuntimeException stopFailure;
        final CountDownLatch stopEntered = new CountDownLatch(1);
        final CountDownLatch releaseStop = new CountDownLatch(1);

        @Override public void onDeviceChanged(Runnable handler) { }
        @Override public void onScanningFinished(Runnable handler) { }
        @Override public void onError(Consumer<String> handler) { }
        @Override public boolean isConnected() { return connected; }
        @Override public void connect(URI uri) { connected = true; }
        @Override public void disconnect() { connected = false; }
        @Override public void startScanning() { }
        @Override public void stopScanning() { }
        @Override public void requestDeviceList() { }
        @Override public List<ButtplugClientDevice> devices() { return Collections.emptyList(); }

        @Override
        public void stopAllDevices() throws Exception {
            stopEntered.countDown();
            if (blockStop) {
                if (!releaseStop.await(3, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("test stop remained blocked");
                }
            }
            if (stopFailure != null) {
                throw stopFailure;
            }
        }
    }
}
