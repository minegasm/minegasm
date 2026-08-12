package net.minegasm.runtime;

import net.minegasm.backend.BackendCoordinator;
import net.minegasm.backend.HapticBackend;
import net.minegasm.config.MinegasmMode;
import net.minegasm.config.RecipePackId;
import net.minegasm.core.HapticScene;
import net.minegasm.testsupport.Configs;
import net.minegasm.time.FakeClock;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The watchdog's stop path (brief §12.4) must not wait on the driver's cycle monitor: if a backend hangs
 * inside a cycle, {@link HapticWorker#emergencyStop} still latches output off and reaches every other
 * backend, where a synchronized stop would deadlock behind the held monitor.
 */
class HapticWorkerEmergencyStopTest {

    @Test
    void emergencyStopDoesNotWaitOnAHungCycle() throws Exception {
        CountDownLatch inCycle = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        BlockingBackend blocking = new BlockingBackend(inCycle, release);
        RecordingBackend other = new RecordingBackend();
        HapticWorker driver = new HapticWorker(new SceneGovernor(),
                new BackendCoordinator(Arrays.asList(blocking, other)),
                new FakeClock(1_000_000_000L),
                () -> Configs.enabled(MinegasmMode.REACTION, RecipePackId.BALANCED));

        Thread cycleThread = new Thread(() -> driver.cycle(1_000_000_000L), "cycle-under-test");
        cycleThread.setDaemon(true);
        cycleThread.start();
        assertTrue(inCycle.await(2, TimeUnit.SECONDS), "the cycle entered the blocking backend");

        // The cycle thread holds the synchronized cycle monitor while it hangs. emergencyStop is out of
        // band, so it must return promptly rather than deadlocking behind that monitor, and it must still
        // reach the other backend to stop its hardware.
        assertTimeoutPreemptively(Duration.ofSeconds(2),
                () -> driver.emergencyStop(StopReason.WATCHDOG));

        assertTrue(other.emergencyStopped, "the healthy backend received the emergency stop");
        assertEquals(StopReason.WATCHDOG, driver.lastStopReason(), "the stop reason is recorded");

        release.countDown(); // let the hung cycle finish so the thread exits cleanly
        cycleThread.join(2000);
    }

    /** Blocks inside the cycle until released, standing in for a hung provider call. */
    private static final class BlockingBackend implements HapticBackend {
        private final CountDownLatch inCycle;
        private final CountDownLatch release;

        BlockingBackend(CountDownLatch inCycle, CountDownLatch release) {
            this.inCycle = inCycle;
            this.release = release;
        }

        @Override
        public String id() {
            return "blocking";
        }

        @Override
        public void start() {
        }

        @Override
        public void onGovernedScenes(List<HapticScene> governed, long nowNs) {
            inCycle.countDown();
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        @Override
        public void stop(StopReason reason) {
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
        }
    }

    /** Records that the out-of-band emergency stop reached it. */
    private static final class RecordingBackend implements HapticBackend {
        volatile boolean emergencyStopped;

        @Override
        public String id() {
            return "other";
        }

        @Override
        public void start() {
        }

        @Override
        public void emergencyStop(StopReason reason) {
            emergencyStopped = true;
        }

        @Override
        public void stop(StopReason reason) {
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
        }
    }
}
