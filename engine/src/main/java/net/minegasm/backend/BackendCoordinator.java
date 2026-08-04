package net.minegasm.backend;

import net.minegasm.core.HapticScene;
import net.minegasm.runtime.StopReason;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Owns every enabled {@link HapticBackend} and fans the device-independent scene stream to all of them
 * (brief 0003 §3.2), guarding each call so one backend's failure never blocks the others.
 *
 * <p>Calls run inline on the caller's thread, which keeps stops synchronous: after a stop returns, every
 * backend's StopCmd is out and local state cleared, so a delayed cycle cannot reassert output. Backends
 * must keep {@code submit}/{@code stop} non-blocking, so inline fan-out never holds up the caller
 * (including panic); isolating a hung backend is that backend's job.
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
