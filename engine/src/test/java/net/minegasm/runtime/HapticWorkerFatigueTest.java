package net.minegasm.runtime;

import net.minegasm.backend.BackendCoordinator;
import net.minegasm.backend.HapticBackend;
import net.minegasm.config.HapticConfig;
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
 * The driver gates fatigue accrual on a rendering backend actually being able to drive the body
 * (ADR-018): with nothing rendering, nothing fatigues; once a renderer is active, sustained ambient
 * fatigues past its budget. Observed through the governed scene the driver fans to backends.
 */
class HapticWorkerFatigueTest {

    private static final long SECOND = 1_000_000_000L;

    // Fatigue protection on, so attenuation is observable; the cases then differ only in whether a
    // rendering backend is active, isolating the accrual gate.
    private final RuntimeConfig cfg = withFatigueProtection(
            Configs.enabled(MinegasmMode.REACTION, RecipePackId.BALANCED));

    private static RuntimeConfig withFatigueProtection(RuntimeConfig base) {
        HapticConfig raw = base.raw();
        HapticConfig.Global g = raw.global();
        return RuntimeConfig.of(new HapticConfig(raw.schemaVersion(), raw.profile(),
                new HapticConfig.Global(g.enabled(), g.intensity(), g.variation(), true,
                        g.pauseBehavior(), g.stopOnWorldUnload(), g.panicKey(), g.testMaxPercent(),
                        g.testMaxDurationMs(), g.unsafeTestMaxPercent(), g.unsafeTestMaxDurationMs()),
                raw.buttplug(), raw.events(), raw.outputPolicy(), raw.devices(),
                raw.positionCalibrations(), raw.accumulation(), raw.customIntensity(), raw.bridges()));
    }

    private static HapticScene sustainedTexture(long createdNs) {
        HapticLayer layer = new HapticLayer("l", HapticRole.TEXTURE,
                new HapticPrimitive.Hold(1.0f, 600_000, 0, 0), HapticRoute.buzzAll(),
                CouplingMode.MAX, 0, 0, Long.MAX_VALUE / 4, "tex");
        return new HapticScene("tex", GameEventKind.AMBIENT, 0, Collections.singletonList(layer),
                createdNs, createdNs + 500L * 1_000_000L, "tex");
    }

    private float lastGovernedLevelOver25s(boolean renderingActive) {
        FakeClock clock = new FakeClock(1_000_000_000L);
        RecordingRenderer renderer = new RecordingRenderer(renderingActive);
        HapticWorker driver = new HapticWorker(new SceneGovernor(),
                new BackendCoordinator(Collections.singletonList(renderer)), clock, () -> cfg);
        for (int i = 0; i <= 25; i++) {
            driver.offer(sustainedTexture(clock.nanoTime())); // continuous scene re-submitted each tick
            driver.cycle(clock.nanoTime());
            clock.advanceMillis(1_000);
        }
        return renderer.lastGoverned.get(0).layers().get(0).primitive().level();
    }

    @Test
    void nothingRenderingMeansNoFatigueAccrual() {
        assertEquals(1.0f, lastGovernedLevelOver25s(false), 1e-6,
                "with no rendering backend active, sustained ambient never fatigues");
    }

    @Test
    void anActiveRendererAccruesFatigue() {
        assertTrue(lastGovernedLevelOver25s(true) < 0.9f,
                "with a rendering backend active, sustained ambient fatigues past its budget");
    }

    /** A rendering backend with a settable active state that records the governed set it receives. */
    private static final class RecordingRenderer implements HapticBackend {
        private final boolean active;
        List<HapticScene> lastGoverned = new ArrayList<>();

        RecordingRenderer(boolean active) {
            this.active = active;
        }

        @Override
        public String id() {
            return "renderer";
        }

        @Override
        public boolean isRenderingActive() {
            return active;
        }

        @Override
        public void onGovernedScenes(List<HapticScene> governed, long nowNs) {
            lastGoverned = new ArrayList<>(governed);
        }

        @Override
        public void start() {
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
