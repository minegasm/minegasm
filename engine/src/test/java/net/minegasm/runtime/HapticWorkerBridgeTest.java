package net.minegasm.runtime;

import net.minegasm.backend.BackendCoordinator;
import net.minegasm.backend.HapticBackend;
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
import net.minegasm.testsupport.Configs;
import net.minegasm.time.FakeClock;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The neutral driver fans the governed set to every backend each cycle, and a stop empties the governor
 * before anything else is fanned, so no backend can render or forward after a stop (ADR-018).
 */
class HapticWorkerBridgeTest {

    private static final long MS = 1_000_000L;

    private final FakeClock clock = new FakeClock(1_000_000_000L);
    private final RuntimeConfig cfg = Configs.enabled(MinegasmMode.REACTION, RecipePackId.BALANCED);

    private static HapticScene continuousScene(long createdNs) {
        HapticLayer layer = new HapticLayer("l", HapticRole.TEXTURE,
                new HapticPrimitive.Hold(0.5f, 600_000, 0, 0), HapticRoute.buzzAll(),
                CouplingMode.MAX, 0, 0, Long.MAX_VALUE / 4, "tex");
        return new HapticScene("tex", GameEventKind.AMBIENT, 0, Collections.singletonList(layer),
                createdNs, createdNs + 500 * MS, "tex");
    }

    private HapticWorker driverWith(HapticBackend backend) {
        return new HapticWorker(new SceneGovernor(),
                new BackendCoordinator(Collections.singletonList(backend)), clock, () -> cfg);
    }

    @Test
    void cycleFansTheGovernedSetToEveryBackend() {
        RecordingBackend backend = new RecordingBackend();
        HapticWorker driver = driverWith(backend);

        driver.offer(continuousScene(clock.nanoTime()));
        driver.cycle(clock.nanoTime());

        assertEquals(1, backend.lastGoverned.size(), "the held scene reaches the backend each cycle");
    }

    @Test
    void stopEmptiesTheGovernorSoNothingIsFannedAfterward() {
        RecordingBackend backend = new RecordingBackend();
        HapticWorker driver = driverWith(backend);
        driver.offer(continuousScene(clock.nanoTime()));
        driver.cycle(clock.nanoTime());
        assertEquals(1, backend.lastGoverned.size());

        driver.stopAll(StopReason.PANIC);
        assertTrue(backend.stopped, "stop fans to the backend");

        clock.advanceMillis(15);
        driver.cycle(clock.nanoTime());
        assertTrue(backend.lastGoverned.isEmpty(),
                "after a stop the governor is empty, so the next cycle fans nothing");
    }

    /** Records the governed set it last received and whether it was stopped. */
    private static final class RecordingBackend implements HapticBackend {
        List<HapticScene> lastGoverned = new ArrayList<>();
        boolean stopped;

        @Override
        public String id() {
            return "rec";
        }

        @Override
        public void start() {
        }

        @Override
        public void onGovernedScenes(List<HapticScene> governed, long nowNs) {
            lastGoverned = new ArrayList<>(governed);
        }

        @Override
        public void stop(StopReason reason) {
            stopped = true;
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
