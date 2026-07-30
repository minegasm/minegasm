package net.minegasm.backend;

import net.minegasm.core.HapticScene;
import net.minegasm.runtime.StopReason;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Owns every enabled {@link HapticBackend} and fans the device-independent scene stream out to all of
 * them (brief 0003 §3.2). A failure in one backend must never block output or stopping on another, so
 * every fan-out call is guarded per backend.
 *
 * <p>Every call runs inline on the caller's thread. That is deliberate for stops: the engine's stop
 * semantics are synchronous (after a stop call returns, the StopCmd is out and local state is cleared,
 * so a delayed cycle cannot reassert output), and the live pipeline already calls the worker's stop
 * inline from the client thread. A {@link HapticBackend} is required to keep {@code submit} and
 * {@code stop} non-blocking, so fanning inline never blocks the caller, including panic. Isolating a
 * hung backend belongs inside that backend, not here.
 */
public final class BackendCoordinator implements AutoCloseable {

    private final List<HapticBackend> backends;

    public BackendCoordinator(List<HapticBackend> backends) {
        this.backends = Collections.unmodifiableList(new ArrayList<>(backends));
    }

    public List<HapticBackend> backends() {
        return backends;
    }

    public void start() {
        for (HapticBackend b : backends) {
            guard(() -> b.start());
        }
    }

    /** Fan a scene to every backend; inline and non-blocking (brief 0003 §3.2). */
    public void submit(final HapticScene scene) {
        for (final HapticBackend b : backends) {
            guard(() -> b.submit(scene));
        }
    }

    /**
     * Stop every backend now, inline and guarded, so one backend that throws cannot stop another from
     * being told to stop (brief 0003 §3.3 safety intent). Preserves the engine's synchronous stop: when
     * this returns, every reachable backend's stop has run. Each backend's stop must be non-blocking, so
     * the caller thread (including panic) is never held up.
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
     * Run one backend action, swallowing any runtime failure so a single misbehaving backend cannot
     * affect the fan-out to the others.
     *
     * <p>TODO(0003 §3.2): a backend that throws here should surface as unhealthy to the watchdog rather
     * than failing silently ("a backend that cannot confirm a stop is shown as unhealthy"). Left as a
     * silent swallow until per-backend health reporting lands.
     */
    private static void guard(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException isolated) {
            // Intentionally isolated: one backend failing must not stop the fan-out to the others.
        }
    }
}
