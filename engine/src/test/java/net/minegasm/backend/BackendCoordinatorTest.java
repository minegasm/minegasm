package net.minegasm.backend;

import net.minegasm.core.HapticScene;
import net.minegasm.runtime.StopReason;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The coordinator fans lifecycle across backends (brief 0003 §3.2): a universal stop reaches every
 * backend synchronously, one backend throwing on stop does not stop the others, and start/close reach
 * all. Scene delivery is not here (ADR-018): scenes are governed centrally and reach backends through
 * the SceneGovernor, not the coordinator.
 */
class BackendCoordinatorTest {

    @Test
    void stopReachesEveryBackendSynchronously() {
        FakeBackend a = new FakeBackend("a");
        FakeBackend b = new FakeBackend("b");
        BackendCoordinator coordinator = new BackendCoordinator(Arrays.asList(a, b));

        coordinator.stopAll(StopReason.PANIC);

        // Synchronous: every backend has already been stopped by the time stopAll returns.
        assertEquals(Collections.singletonList(StopReason.PANIC), a.stops);
        assertEquals(Collections.singletonList(StopReason.PANIC), b.stops);
        coordinator.close();
    }

    @Test
    void oneBackendThrowingOnStopDoesNotStopOthers() {
        FakeBackend boom = new FakeBackend("boom");
        boom.throwOnStop = true;
        FakeBackend ok = new FakeBackend("ok");
        BackendCoordinator coordinator = new BackendCoordinator(Arrays.asList(boom, ok));

        coordinator.stopAll(StopReason.PANIC); // must not throw out of the coordinator

        assertTrue(ok.stops.contains(StopReason.PANIC),
                "a throwing backend must not prevent the other from stopping");
        assertTrue(boom.stops.contains(StopReason.PANIC), "the throwing backend still received the stop");
        coordinator.close();
    }

    @Test
    void startAndCloseFanToEveryBackend() {
        FakeBackend a = new FakeBackend("a");
        FakeBackend b = new FakeBackend("b");
        BackendCoordinator coordinator = new BackendCoordinator(Arrays.asList(a, b));

        coordinator.start();
        assertTrue(a.started && b.started, "start must reach every backend");

        assertFalse(a.closed || b.closed);
        coordinator.close();
        assertTrue(a.closed && b.closed, "close must reach every backend");
    }

    @Test
    void aBackendThrowingDuringFanOutIsStoppedAndRecordedNotSwallowed() {
        FakeBackend boom = new FakeBackend("boom");
        boom.throwOnGoverned = true;
        FakeBackend ok = new FakeBackend("ok");
        BackendCoordinator coordinator = new BackendCoordinator(Arrays.asList(boom, ok));

        int faulted = coordinator.onGovernedScenes(Collections.emptyList(), 1_000L);

        assertEquals(1, faulted, "one backend faulted this cycle");
        assertTrue(boom.stops.contains(StopReason.BACKEND_FAULT),
                "a faulted backend is stopped so it can't hold stale output");
        assertTrue(ok.governedCalls > 0, "the healthy backend still received the fan-out");
        assertEquals(1, coordinator.faultCount(), "the fault is recorded, not silently swallowed");
        assertFalse(coordinator.recentFaults().isEmpty(), "the fault is visible for health reporting");
        assertTrue(coordinator.quarantined().contains("boom"), "the faulted backend is quarantined");

        // A quarantined backend is skipped on the next cycle, so it can't re-enter and re-fault.
        int boomCalls = boom.governedCalls;
        int okCalls = ok.governedCalls;
        coordinator.onGovernedScenes(Collections.emptyList(), 2_000L);
        assertEquals(boomCalls, boom.governedCalls, "the quarantined backend is not called again");
        assertTrue(ok.governedCalls > okCalls, "the healthy backend keeps running");
        assertEquals(1, coordinator.faultCount(), "no new fault while quarantined");

        coordinator.clearQuarantine("boom");
        boom.throwOnGoverned = false;
        coordinator.onGovernedScenes(Collections.emptyList(), 3_000L);
        assertTrue(boom.governedCalls > boomCalls, "clearing the quarantine lets it rejoin the fan-out");
        coordinator.close();
    }

    /** A backend that records the stops it received and can be told to throw. Stops run synchronously. */
    private static final class FakeBackend implements HapticBackend {
        private final String id;
        final List<StopReason> stops = new ArrayList<>();
        boolean throwOnStop;
        boolean throwOnGoverned;
        int governedCalls;
        boolean started;
        boolean closed;

        FakeBackend(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public void start() {
            started = true;
        }

        @Override
        public void onGovernedScenes(List<HapticScene> governed, long nowNs) {
            governedCalls++;
            if (throwOnGoverned) {
                throw new RuntimeException("render boom from " + id);
            }
        }

        @Override
        public void stop(StopReason reason) {
            stops.add(reason);
            if (throwOnStop) {
                throw new RuntimeException("stop boom from " + id);
            }
        }

        @Override
        public void pause() {
        }

        @Override
        public void resume() {
        }

        @Override
        public void discardPause() {
        }

        @Override
        public void setOutputEnabled(boolean enabled) {
        }

        @Override
        public long lastHealthyCycleNs() {
            return 0L;
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
