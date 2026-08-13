package net.minegasm.runtime;

import net.minegasm.backend.BackendCoordinator;
import net.minegasm.backend.HapticBackend;
import net.minegasm.config.MinegasmMode;
import net.minegasm.config.RecipePackId;
import net.minegasm.core.HapticScene;
import net.minegasm.testsupport.Configs;
import net.minegasm.time.FakeClock;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The watchdog latches output off on a stall and auto-recovers once healthy cycles resume, with the
 * explicit output state and the master latch staying in agreement (review follow-up P1-1).
 */
class WatchdogRecoveryTest {

    @Test
    void latchesOnStallAndRecoversWhenHealthy() {
        FakeClock clock = new FakeClock(1_000_000_000L);
        RecordingBackend backend = new RecordingBackend();
        HapticWorker worker = new HapticWorker(new SceneGovernor(),
                new BackendCoordinator(Collections.singletonList(backend)), clock,
                () -> Configs.enabled(MinegasmMode.REACTION, RecipePackId.BALANCED));
        Watchdog watchdog = new Watchdog(worker, clock, 2000);

        worker.cycle(clock.nanoTime()); // a healthy heartbeat
        assertFalse(watchdog.check());
        assertEquals(OutputState.RUNNING, worker.outputState());
        assertTrue(worker.isOutputEnabled());

        clock.advanceMillis(3000); // stall past the threshold
        assertTrue(watchdog.check(), "the watchdog fires on a stall");
        assertEquals(OutputState.WATCHDOG_STOPPED, worker.outputState());
        assertFalse(worker.isOutputEnabled(), "output is latched off, so the banner shows it");
        assertTrue(backend.emergencyStopped, "the backend hardware was stopped out of band");

        worker.cycle(clock.nanoTime()); // a healthy cycle again
        assertFalse(watchdog.check());
        assertEquals(OutputState.RUNNING, worker.outputState(), "the watchdog stop auto-recovers");
        assertTrue(worker.isOutputEnabled(), "output resumes once cycles flow again");
    }

    @Test
    void aUserPanicIsNotClearedByWatchdogRecovery() {
        FakeClock clock = new FakeClock(1_000_000_000L);
        HapticWorker worker = new HapticWorker(new SceneGovernor(),
                new BackendCoordinator(Collections.singletonList(new RecordingBackend())), clock,
                () -> Configs.enabled(MinegasmMode.REACTION, RecipePackId.BALANCED));
        Watchdog watchdog = new Watchdog(worker, clock, 2000);

        worker.cycle(clock.nanoTime());
        worker.enterUserStop(); // the user panicked
        assertEquals(OutputState.USER_STOPPED, worker.outputState());

        worker.cycle(clock.nanoTime());
        watchdog.check(); // healthy: recovery must not touch a user panic
        assertEquals(OutputState.USER_STOPPED, worker.outputState(), "a user panic stays latched");
        assertFalse(worker.isOutputEnabled());
    }

    /** Records the out-of-band stop and the latch fan-out. */
    private static final class RecordingBackend implements HapticBackend {
        volatile boolean emergencyStopped;
        volatile boolean outputEnabled = true;

        @Override
        public String id() {
            return "rec";
        }

        @Override
        public void start() {
        }

        @Override
        public void onGovernedScenes(List<HapticScene> governed, long nowNs) {
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
            outputEnabled = enabled;
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
