package net.minegasm.runtime;

import net.minegasm.backend.BackendCoordinator;
import net.minegasm.config.RuntimeConfig;
import net.minegasm.core.HapticScene;
import net.minegasm.time.Clock;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * The neutral governance driver (brief §6.4, ADR-018). It owns the monotonic loop and is the single
 * caller of {@link SceneGovernor#govern}; each cycle it advances governance once and fans the governed
 * scene set to every backend. It renders nothing itself: rendering lives in the rendering backends
 * (Buttplug and any future native integration), forwarding lives in the semantic backends (the bridge),
 * and they all run concurrently off this one governed set. Cycles are driveable directly by tests with a
 * {@link net.minegasm.time.FakeClock}.
 *
 * <p>The driver's monitor is what makes a stop safe: {@link #cycle} governs and fans while holding it, and
 * {@link #stopAll} resets the governor and then fans stop while holding it too. So a stop can never
 * interleave with a cycle, and because it resets the governor <em>before</em> fanning backend stops, no
 * backend can render or forward a scene after its stop has run.
 */
public final class HapticWorker {

    private static final long CYCLE_MS = 15;

    private final SceneGovernor scenes;
    private final BackendCoordinator backends;
    private final Clock clock;
    private final Supplier<RuntimeConfig> config;

    private final AtomicLong lastHealthyCycleNs = new AtomicLong();
    private volatile StopReason lastStopReason;
    private volatile boolean outputEnabled = true; // master panic latch, mirrored to every backend
    private boolean paused;
    private long pausedAtNs;
    private ScheduledExecutorService executor;
    private ScheduledFuture<?> loop;

    public HapticWorker(SceneGovernor scenes, BackendCoordinator backends, Clock clock,
                        Supplier<RuntimeConfig> config) {
        this.scenes = scenes;
        this.backends = backends;
        this.clock = clock;
        this.config = config;
    }

    /** Start the real-time loop on a dedicated daemon thread. */
    public synchronized void start() {
        if (loop != null) {
            return;
        }
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "minegasm-worker");
            t.setDaemon(true);
            return t;
        });
        loop = executor.scheduleAtFixedRate(() -> {
            try {
                cycle(clock.nanoTime());
            } catch (RuntimeException ex) {
                // A driver fault must fail toward stopped output, never escape silently (brief §12.4).
                stopAll(StopReason.WATCHDOG);
            }
        }, CYCLE_MS, CYCLE_MS, TimeUnit.MILLISECONDS);
    }

    public synchronized void shutdown() {
        if (loop != null) {
            loop.cancel(false);
            loop = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
        stopAll(StopReason.SHUTDOWN);
    }

    /** Submit a scene to the central governor from the client thread; non-blocking and bounded. */
    public void offer(HapticScene scene) {
        scenes.submit(scene, clock.nanoTime());
    }

    /**
     * Advance governance once and fan the governed set to every backend. Returns nothing: a rendering
     * backend keeps its own dispatched commands (see {@code ButtplugBackend.lastCommands}).
     */
    public synchronized void cycle(long nowNs) {
        if (paused) {
            lastHealthyCycleNs.set(nowNs);
            return;
        }
        RuntimeConfig cfg = config.get();
        // Only accrue fatigue when a rendering backend can actually drive the body; with nothing
        // rendering, nothing fatigues (brief §10.6). The governor expires stale scenes, decays and
        // accounts fatigue, and bakes the attenuation into the primitives it hands back.
        boolean accountLoad = cfg.enabled() && backends.anyRenderingActive();
        List<HapticScene> held = scenes.govern(nowNs, cfg.fatigueProtection(), accountLoad);
        backends.onGovernedScenes(held, nowNs);
        lastHealthyCycleNs.set(nowNs);
    }

    /**
     * Stop all output immediately (brief §9.10). Resets the governor first so the held set is empty, then
     * fans stop to every backend, all under the driver monitor: once this returns, the governor is empty
     * and every backend has stopped, so no later cycle can reassert output.
     */
    public synchronized void stopAll(StopReason reason) {
        this.lastStopReason = reason;
        scenes.reset(); // drops held scenes and forgets fatigue load
        backends.stopAll(reason);
        paused = false;
        pausedAtNs = 0;
    }

    /**
     * Out-of-band safety stop for the watchdog (brief §12.4). Deliberately <em>not</em> synchronized: it
     * latches master output off and fans an emergency stop to every backend without taking the cycle
     * monitor, so a backend hung inside {@link #cycle} cannot keep this from tripping the way a
     * synchronized {@link #stopAll} would. The governor is not reset here (that needs the monitor); output
     * stays latched off, so any held scenes cannot drive a backend until output is explicitly re-enabled.
     */
    public void emergencyStop(StopReason reason) {
        this.lastStopReason = reason;
        this.outputEnabled = false; // volatile master latch; every backend gates its output on its own copy
        backends.emergencyStop(reason);
    }

    /** Freeze the governed scenes and stop hardware for a possible resume. */
    public synchronized void pauseAll() {
        if (paused) {
            return;
        }
        paused = true;
        pausedAtNs = clock.nanoTime();
        backends.pauseAll(); // each backend stops its hardware and records its device generation
    }

    /** Resume the frozen scenes, unless a backend's device set changed while paused. */
    public synchronized void resumeAll() {
        if (!paused) {
            return;
        }
        long nowNs = clock.nanoTime();
        if (backends.anyRegistryChangedSincePause()) {
            scenes.reset();
        } else {
            scenes.shiftTime(Math.max(0, nowNs - pausedAtNs)); // shifts held scenes and the fatigue clock
        }
        backends.resumeAll();
        paused = false;
        pausedAtNs = 0;
    }

    public synchronized void discardPauseAll() {
        if (!paused) {
            return;
        }
        scenes.reset();
        backends.discardPauseAll();
        paused = false;
        pausedAtNs = 0;
    }

    public synchronized boolean isPaused() {
        return paused;
    }

    /** Master enable/disable across every backend (panic latch and config). */
    public void setOutputEnabled(boolean enabled) {
        this.outputEnabled = enabled;
        backends.setOutputEnabled(enabled);
    }

    public boolean isOutputEnabled() {
        return outputEnabled;
    }

    public long lastHealthyCycleNs() {
        return lastHealthyCycleNs.get();
    }

    public StopReason lastStopReason() {
        return lastStopReason;
    }

    public int activeSceneCount() {
        return scenes.activeSceneCount();
    }
}
