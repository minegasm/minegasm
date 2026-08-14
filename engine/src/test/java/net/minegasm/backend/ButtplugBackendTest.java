package net.minegasm.backend;

import net.minegasm.buttplug.HapticProvider;
import net.minegasm.buttplug.OutputCommand;
import net.minegasm.buttplug.ProviderStatus;
import net.minegasm.buttplug.StopSelection;
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
import net.minegasm.device.DeviceRegistrySnapshot;
import net.minegasm.runtime.StopReason;
import net.minegasm.testsupport.Configs;
import net.minegasm.testsupport.Devices;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An isolated Buttplug test is a live scene injected into this backend's own render until it expires. It
 * must never survive a stop-like transition: if panic, pause, or master-off clears while the test's long
 * lifetime is still running, the old test must not resume onto the body.
 */
class ButtplugBackendTest {

    private static final long MS = 1_000_000L;

    private final RecordingProvider provider = new RecordingProvider(Devices.singleVibrate());
    private final RuntimeConfig cfg = Configs.enabled(MinegasmMode.REACTION, RecipePackId.BALANCED);
    private final ButtplugBackend backend = new ButtplugBackend(provider, () -> cfg);

    /** A long-lived test scene so its own expiry never ends it during the test window. */
    private static HapticScene longTest(long now) {
        HapticLayer layer = new HapticLayer("t", HapticRole.TEXTURE,
                new HapticPrimitive.Hold(0.6f, 30_000_000, 0, 0), HapticRoute.buzzAll(),
                CouplingMode.MAX, 0, 0, Long.MAX_VALUE / 4, "t");
        return new HapticScene("test", GameEventKind.AMBIENT, 0, Collections.singletonList(layer),
                now, now + 30_000L * MS, "test");
    }

    private boolean drivesOutput() {
        for (OutputCommand c : backend.lastCommands()) {
            if (c.value() > 0) {
                return true;
            }
        }
        return false;
    }

    @Test
    void liveTestDrivesOutputWhileItRuns() {
        long now = 1_000_000_000L;
        backend.test(longTest(now), now);
        backend.onGovernedScenes(Collections.emptyList(), now);
        assertTrue(drivesOutput(), "an injected test scene renders to a positive command while live");
    }

    @Test
    void testDoesNotResumeAfterStop() {
        long now = 1_000_000_000L;
        backend.test(longTest(now), now);
        backend.onGovernedScenes(Collections.emptyList(), now);
        assertTrue(drivesOutput());

        backend.stop(StopReason.PANIC);
        backend.onGovernedScenes(Collections.emptyList(), now + MS); // still inside the test's lifetime
        assertFalse(drivesOutput(), "a stopped test must not resume when cycling continues");
    }

    @Test
    void testDoesNotResumeAfterPause() {
        long now = 1_000_000_000L;
        backend.test(longTest(now), now);
        backend.onGovernedScenes(Collections.emptyList(), now);

        backend.pause();
        backend.resume();
        backend.onGovernedScenes(Collections.emptyList(), now + MS);
        assertFalse(drivesOutput(), "pause clears the live test so resume does not restart it");
    }

    @Test
    void testDoesNotResumeAfterOutputDisabledThenReEnabled() {
        long now = 1_000_000_000L;
        backend.test(longTest(now), now);
        backend.onGovernedScenes(Collections.emptyList(), now);

        backend.setOutputEnabled(false);
        backend.setOutputEnabled(true); // panic cleared / master re-enabled before the test would expire
        backend.onGovernedScenes(Collections.emptyList(), now + MS);
        assertFalse(drivesOutput(), "master-off drops the live test so re-enabling does not restart it");
    }

    @Test
    void lateSendFailureQuarantinesTheBackend() {
        CompletableFuture<Void> send = new CompletableFuture<>();
        provider.nextSend = send;
        BackendCoordinator coordinator = new BackendCoordinator(Collections.singletonList(backend));

        backend.onGovernedScenes(Collections.singletonList(longTest(1_000_000_000L)),
                1_000_000_000L);
        assertEquals(BackendOutcomeState.ACCEPTED, backend.latestOutcome().state());

        send.completeExceptionally(new IllegalStateException("device write failed"));

        assertEquals(BackendOutcomeState.FAILED, backend.unresolvedFailure().state());
        assertTrue(coordinator.quarantined().contains("buttplug"),
                "an asynchronous provider error enters persistent backend health");
    }

    @Test
    void lateStopFailureRemainsAnUnresolvedFault() {
        CompletableFuture<Void> stop = new CompletableFuture<>();
        provider.nextStop = stop;
        BackendCoordinator coordinator = new BackendCoordinator(Collections.singletonList(backend));

        backend.stop(StopReason.PANIC);
        assertEquals(BackendOutcomeState.ACCEPTED, backend.latestOutcome().state(),
                "stop requested and stop confirmed are distinct states");
        stop.completeExceptionally(new IllegalStateException("stop rejected"));

        assertEquals(BackendOutcomeState.FAILED, backend.unresolvedFailure().state());
        assertEquals(BackendOperation.STOP, backend.unresolvedFailure().operation());
        assertTrue(coordinator.quarantined().contains("buttplug"));
    }

    @Test
    void watchdogStopPreventsACycleAlreadyRenderingFromSendingAfterTheStop() throws Exception {
        provider.blockDevices = true;
        Thread cycle = new Thread(() -> backend.onGovernedScenes(
                Collections.singletonList(longTest(1_000_000_000L)), 1_000_000_000L));
        cycle.start();
        assertTrue(provider.devicesEntered.await(2, TimeUnit.SECONDS));

        backend.emergencyStop(StopReason.WATCHDOG);
        provider.releaseDevices.countDown();
        cycle.join(2_000L);

        assertFalse(cycle.isAlive());
        assertTrue(provider.sent.isEmpty(), "the in-progress render cannot dispatch behind its stop");
    }

    /** A provider over a fixed device snapshot that records the commands it is asked to send. */
    private static final class RecordingProvider implements HapticProvider {
        private final DeviceRegistrySnapshot snapshot;
        final List<OutputCommand> sent = new ArrayList<>();
        CompletableFuture<Void> nextSend;
        CompletableFuture<Void> nextStop;
        volatile boolean blockDevices;
        final CountDownLatch devicesEntered = new CountDownLatch(1);
        final CountDownLatch releaseDevices = new CountDownLatch(1);

        RecordingProvider(DeviceRegistrySnapshot snapshot) {
            this.snapshot = snapshot;
        }

        @Override
        public CompletionStage<ProviderStatus> connect(URI uri) {
            return CompletableFuture.completedFuture(status());
        }

        @Override
        public CompletionStage<Void> startScanning() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> stopScanning() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> refreshDevices() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> send(OutputCommand command) {
            sent.add(command);
            CompletableFuture<Void> selected = nextSend;
            nextSend = null;
            return selected == null ? CompletableFuture.completedFuture(null) : selected;
        }

        @Override
        public CompletionStage<Void> stop(StopSelection selection) {
            CompletableFuture<Void> selected = nextStop;
            nextStop = null;
            return selected == null ? CompletableFuture.completedFuture(null) : selected;
        }

        @Override
        public DeviceRegistrySnapshot devices() {
            if (blockDevices) {
                devicesEntered.countDown();
                try {
                    releaseDevices.await(2, TimeUnit.SECONDS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
            return snapshot;
        }

        @Override
        public ProviderStatus status() {
            return ProviderStatus.disconnected();
        }

        @Override
        public void setStatusListener(Consumer<ProviderStatus> listener) {
        }

        @Override
        public void setRegistryListener(Consumer<DeviceRegistrySnapshot> listener) {
        }

        @Override
        public void disconnect() {
        }

        @Override
        public void close() {
        }
    }
}
