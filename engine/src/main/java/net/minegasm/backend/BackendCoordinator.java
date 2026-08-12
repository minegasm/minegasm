package net.minegasm.backend;

import net.minegasm.core.HapticScene;
import net.minegasm.runtime.StopReason;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

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

    /** Cap on retained fault messages, so a chronically failing backend cannot grow memory. */
    private static final int FAULT_LOG_LIMIT = 32;

    // Copy-on-write so the worker thread can fan out lock-free while the client thread adds or removes a
    // bridge backend live (an enable toggle or a newly added bridge, no game restart).
    private final CopyOnWriteArrayList<HapticBackend> backends;

    // Recent render faults, newest last, bounded. A backend that throws in scene fan-out is stopped and
    // recorded here rather than swallowed, so the failure is visible to health/status reporting.
    private final ConcurrentLinkedDeque<String> faults = new ConcurrentLinkedDeque<>();
    private final AtomicLong faultCount = new AtomicLong();

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
     * backend's render or forward failing cannot skip another or block the driver. A backend that throws
     * is not silently swallowed: its stop is attempted immediately so it can't keep holding the output it
     * failed to update, and the fault is recorded for health reporting. It stays in the fan-out so it can
     * recover on a later cycle. Returns the number of backends that faulted this cycle.
     */
    public int onGovernedScenes(final List<HapticScene> governed, final long nowNs) {
        int faulted = 0;
        for (final HapticBackend b : backends) {
            if (!guard(() -> b.onGovernedScenes(governed, nowNs))) {
                faulted++;
                // Fail toward stopped for this backend: don't let it hold a stale non-zero value after a
                // failed update. Best-effort; if the stop also throws it is guarded too.
                guard(() -> b.stop(StopReason.BACKEND_FAULT));
                recordFault(b.id());
            }
        }
        return faulted;
    }

    private void recordFault(String backendId) {
        faultCount.incrementAndGet();
        faults.addLast(backendId + " render fault");
        while (faults.size() > FAULT_LOG_LIMIT) {
            faults.pollFirst();
        }
    }

    /** Total render faults seen since start, for health/status (not just the retained tail). */
    public long faultCount() {
        return faultCount.get();
    }

    /** A bounded snapshot of the most recent render faults, oldest first. */
    public List<String> recentFaults() {
        return Collections.unmodifiableList(new java.util.ArrayList<>(faults));
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

    /**
     * Fan an out-of-band emergency stop to every backend (the watchdog path). Like the others it never
     * blocks the caller, but unlike {@link #stopAll} each backend does only thread-safe work here, so this
     * is safe to call from the watchdog thread while the driver is mid-cycle.
     */
    public void emergencyStop(final StopReason reason) {
        for (final HapticBackend b : backends) {
            guard(() -> b.emergencyStop(reason));
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
     * Run one backend action, isolating a runtime failure so one misbehaving backend cannot affect the
     * others. Returns true if the action completed, false if it threw. Lifecycle callers ignore the
     * result (a failed stop still must not skip the next backend); scene fan-out uses it to stop and
     * record the faulted backend rather than swallowing the failure.
     */
    private static boolean guard(Runnable action) {
        try {
            action.run();
            return true;
        } catch (RuntimeException isolated) {
            // Isolated: one backend failing must not stop the fan-out to the others. The caller decides
            // whether to act on the failure (scene fan-out stops and records it).
            return false;
        }
    }
}
