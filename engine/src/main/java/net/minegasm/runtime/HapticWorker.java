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
    private final Object heartbeatLock = new Object();
    // Recovery must never re-enable output halfway through a cycle whose scene snapshot was taken while
    // the watchdog latch was still active. A non-blocking tryLock lets the watchdog defer recovery when a
    // backend is genuinely hung without making the client or watchdog thread wait for that backend.
    private final java.util.concurrent.locks.ReentrantLock cycleTransition =
            new java.util.concurrent.locks.ReentrantLock();
    private volatile StopReason lastStopReason;
    private volatile boolean outputEnabled = true; // master latch mirror, recomputed from the causes below
    // The runtime causes holding output off, tracked independently so a watchdog stop and a user panic can
    // coexist and clearing one never clears the other (second follow-up review P1-2). Guarded by causeLock,
    // a dedicated lock that is never the cycle monitor, so the watchdog's out-of-band stop stays off it.
    private final Object causeLock = new Object();
    private final java.util.EnumSet<StopCause> stopCauses = java.util.EnumSet.noneOf(StopCause.class);
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
        cycleTransition.lock();
        try {
            if (paused) {
                recordHealthyCycle(nowNs);
                return;
            }
            RuntimeConfig cfg = config.get();
            // Only accrue fatigue when a rendering backend can actually drive the body; with nothing
            // rendering, nothing fatigues (brief §10.6). The governor expires stale scenes, decays and
            // accounts fatigue, and bakes the attenuation into the primitives it hands back.
            boolean accountLoad = cfg.enabled() && backends.anyBodyDriving();
            GovernedOutput output = scenes.resolve(nowNs, cfg.fatigueProtection(), accountLoad);
            int faulted = backends.onGovernedOutput(output);
            // Don't claim a healthy heartbeat for a cycle where a backend faulted (it is now quarantined
            // and stopped). Subsequent cycles skip it, so one failure does not trip the watchdog.
            if (faulted == 0) {
                recordHealthyCycle(nowNs);
            }
        } finally {
            cycleTransition.unlock();
        }
    }

    private void recordHealthyCycle(long nowNs) {
        synchronized (heartbeatLock) {
            lastHealthyCycleNs.set(nowNs);
        }
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
     * fans an emergency stop to every backend without taking the cycle monitor, so a backend hung inside
     * {@link #cycle} cannot keep it from reaching the devices the way a synchronized {@link #stopAll}
     * would deadlock. It stops the hardware but does not latch master output off: a watchdog stall is
     * usually a transient hitch (a GC pause, a world-load spike), and while the worker is stalled it
     * produces no new output anyway, so once it resumes healthy cycling output recovers on its own rather
     * than staying stopped until the user clears a panic. It does not touch the governor (that needs the
     * monitor); a real panic still latches through {@link #setOutputEnabled}.
     */
    public void emergencyStop(StopReason reason) {
        this.lastStopReason = reason;
        addCause(StopCause.WATCHDOG);   // latch output off, independent of any user panic already active
        backends.emergencyStop(reason); // stop the hardware out of band (no cycle monitor)
    }

    /**
     * Watchdog recovery on a healthy tick: clears <em>only</em> the watchdog cause, and only if a watchdog
     * stop is actually latched, so it can never resume output while a user panic is still active. Uses
     * causeLock, not the cycle monitor, so it never blocks; the governor's own lock serializes the reset.
     */
    public boolean recoverFromWatchdogStop() {
        if (!cycleTransition.tryLock()) {
            return false;
        }
        try {
            synchronized (causeLock) {
                if (stopCauses.remove(StopCause.WATCHDOG)) {
                    scenes.reset(); // drop stale held scenes before output resumes
                    applyLatch();
                }
            }
            return true;
        } finally {
            cycleTransition.unlock();
        }
    }

    /**
     * Fire only if the heartbeat is still the stale value the watchdog observed. The heartbeat and this
     * transition share one small lock, closing the final gap where a healthy cycle could complete between
     * a watchdog re-read and its stop.
     */
    boolean emergencyStopIfHeartbeatStalled(long observedHeartbeatNs, long nowNs, long thresholdNs,
                                             StopReason reason) {
        synchronized (heartbeatLock) {
            long current = lastHealthyCycleNs.get();
            if (current == 0L || current != observedHeartbeatNs || nowNs - current <= thresholdNs) {
                return false;
            }
            emergencyStop(reason);
            return true;
        }
    }

    /** Latch output off for a user panic. Independent of the watchdog cause. */
    public void enterUserStop() {
        addCause(StopCause.USER_STOP);
    }

    /**
     * Clear a user panic. Clears <em>only</em> the user-stop cause: if the watchdog also has output
     * latched off, output stays off, so a resume can never override a live watchdog stall.
     */
    public void clearUserStop() {
        removeCause(StopCause.USER_STOP);
    }

    private void addCause(StopCause cause) {
        synchronized (causeLock) {
            if (stopCauses.add(cause)) {
                applyLatch();
            }
        }
    }

    private void removeCause(StopCause cause) {
        synchronized (causeLock) {
            if (stopCauses.remove(cause)) {
                applyLatch();
            }
        }
    }

    /** Recompute the fanned master latch from the current causes. Caller holds causeLock. */
    private void applyLatch() {
        boolean enabled = stopCauses.isEmpty();
        this.outputEnabled = enabled;
        backends.setOutputEnabled(enabled);
    }

    /** A snapshot of the runtime causes holding output off; the client folds in config and quarantine. */
    public OutputStatus outputStatus() {
        synchronized (causeLock) {
            return OutputStatus.of(stopCauses);
        }
    }

    public boolean isUserStopped() {
        synchronized (causeLock) {
            return stopCauses.contains(StopCause.USER_STOP);
        }
    }

    public boolean isWatchdogStopped() {
        synchronized (causeLock) {
            return stopCauses.contains(StopCause.WATCHDOG);
        }
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

    /** Whether output is currently permitted (no runtime cause holds it off); the fanned latch mirror. */
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
