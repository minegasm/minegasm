package net.minegasm.backend;

import net.minegasm.core.HapticScene;
import net.minegasm.runtime.StopReason;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Owns every enabled {@link HapticBackend} and fans lifecycle calls to all of them (brief 0003 §3.2),
 * guarding each call so one backend's failure never blocks the others. Scene fan-out is not here: scenes
 * are governed centrally and reach each backend through the {@link net.minegasm.runtime.SceneGovernor}
 * (ADR-018), so this class only starts, stops, pauses, and closes backends.
 *
 * <p>Calls run inline on the caller's thread, which keeps stops synchronous: after a stop returns, every
 * backend's StopCmd is out and local state cleared, so a delayed cycle cannot reassert output. Backends
 * must keep {@code stop} non-blocking, so inline fan-out never holds up the caller (including panic);
 * isolating a hung backend is that backend's job.
 */
public final class BackendCoordinator implements AutoCloseable {

    // Copy-on-write so the worker thread can fan out lock-free while the client thread adds or removes a
    // bridge backend live (an enable toggle or a newly added bridge, no game restart).
    private final CopyOnWriteArrayList<HapticBackend> backends;

    public BackendCoordinator(List<HapticBackend> backends) {
        this.backends = new CopyOnWriteArrayList<>(backends);
    }

    public List<HapticBackend> backends() {
        return Collections.unmodifiableList(backends);
    }

    /** Add a backend that is already started, so it joins the fan-out from the next cycle. */
    public void add(HapticBackend backend) {
        backends.add(backend);
    }

    /** Remove a backend from the fan-out; the caller stops and closes it. */
    public void remove(HapticBackend backend) {
        backends.remove(backend);
    }

    public void start() {
        for (HapticBackend b : backends) {
            guard(() -> b.start());
        }
    }

    /**
     * Fan the governed scene set to every backend for this cycle (ADR-018). Inline and guarded, so one
     * backend's render or forward failing cannot skip another or block the driver.
     */
    public void onGovernedScenes(final List<HapticScene> governed, final long nowNs) {
        for (final HapticBackend b : backends) {
            guard(() -> b.onGovernedScenes(governed, nowNs));
        }
    }

    /** Whether any rendering backend is currently able to drive the body, so fatigue should accrue. */
    public boolean anyRenderingActive() {
        for (HapticBackend b : backends) {
            if (b.isRenderingActive()) {
                return true;
            }
        }
        return false;
    }

    /** Whether any backend's device set changed while paused, so frozen scenes must be discarded. */
    public boolean anyRegistryChangedSincePause() {
        for (HapticBackend b : backends) {
            if (b.registryChangedSincePause()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Stop every backend now, inline and guarded (brief 0003 §3.3): when this returns every reachable
     * backend's stop has run, and one backend throwing cannot skip another.
     */
    public void stopAll(final StopReason reason) {
        for (final HapticBackend b : backends) {
            guard(() -> b.stop(reason));
        }
    }

    public void pauseAll() {
        for (HapticBackend b : backends) {
            guard(() -> b.pause());
        }
    }

    public void resumeAll() {
        for (HapticBackend b : backends) {
            guard(() -> b.resume());
        }
    }

    public void discardPauseAll() {
        for (HapticBackend b : backends) {
            guard(() -> b.discardPause());
        }
    }

    public void setOutputEnabled(final boolean enabled) {
        for (HapticBackend b : backends) {
            guard(() -> b.setOutputEnabled(enabled));
        }
    }

    @Override
    public void close() {
        for (HapticBackend b : backends) {
            guard(() -> b.close());
        }
    }

    /**
     * Run one backend action, swallowing a runtime failure so one misbehaving backend cannot affect the
     * others.
     *
     * <p>TODO(0003 §3.2): a backend that throws should surface as unhealthy to the watchdog rather than
     * failing silently; left as a silent swallow until per-backend health reporting lands.
     */
    private static void guard(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException isolated) {
            // Intentionally isolated: one backend failing must not stop the fan-out to the others.
        }
    }
}
