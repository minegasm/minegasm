package net.minegasm.backend;

import net.minegasm.core.HapticScene;
import net.minegasm.runtime.StopReason;
import net.minegasm.runtime.GovernedOutput;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Owns every enabled {@link HapticBackend} and fans lifecycle calls to all of them (brief 0003 §3.2),
 * guarding each call so one backend's failure never blocks the others. Scenes and logical destinations
 * are governed centrally by {@link net.minegasm.runtime.SceneGovernor}; this class distributes that one
 * result and coordinates lifecycle, quarantine, and outcome health.
 *
 * <p>Calls run inline long enough to invalidate local output and request a stop. Transport completion is
 * asynchronous and reaches this coordinator through structured outcomes, so a failed or timed-out stop
 * remains quarantined and visible.
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
    // Ids of backends taken out of scene fan-out after a render fault, so a repeatedly failing backend
    // can't keep re-entering service and re-driving output. Cleared when a fresh backend with that id is
    // added, or explicitly on reconnect.
    private final java.util.Set<String> quarantined =
            java.util.concurrent.ConcurrentHashMap.newKeySet();
    // The last settled test result per backend, so a screen or /mg status can report how a test actually
    // finished (delivered, failed, timed out, superseded) instead of only the synchronous "accepted". Kept
    // apart from latestOutcomes() on purpose: an idle send-to-zero on the ordinary path overwrites the
    // shared latest slot, which would clobber the test result a moment after it lands.
    private final java.util.concurrent.ConcurrentMap<String, BackendOutcome> lastTestOutcomes =
            new java.util.concurrent.ConcurrentHashMap<>();
    // Per-backend test fire and settle counts, so a caller can tell when the specific test it fired (and
    // every test before it) has settled, rather than latching on an earlier superseded one. A fire is
    // counted synchronously at dispatch; a settle is counted when that test reaches a terminal state. The
    // caller records the fire ordinal it got and waits for the settle count to reach it.
    private final java.util.concurrent.ConcurrentMap<String, AtomicLong> testFires =
            new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentMap<String, AtomicLong> testSettles =
            new java.util.concurrent.ConcurrentHashMap<>();

    public BackendCoordinator(List<HapticBackend> backends) {
        this.backends = new CopyOnWriteArrayList<>(backends);
        for (HapticBackend backend : backends) {
            registerOutcomeListener(backend);
        }
    }

    public List<HapticBackend> backends() {
        return Collections.unmodifiableList(backends);
    }

    /** Add a backend that is already started, so it joins the fan-out from the next cycle. */
    public void add(HapticBackend backend) {
        quarantined.remove(backend.id()); // a freshly added backend starts un-quarantined
        registerOutcomeListener(backend);
        backends.add(backend);
    }

    private void registerOutcomeListener(final HapticBackend backend) {
        backend.setOutcomeListener(outcome -> {
            if (outcome == null) {
                return;
            }
            // A test never becomes a health fault (that early-returns below), but its settled result is
            // worth showing back to the user. Record the terminal states only; the immediate ACCEPTED is
            // already what the caller saw synchronously.
            if (outcome.operation() == BackendOperation.TEST) {
                if (outcome.state() != BackendOutcomeState.ACCEPTED) {
                    // Store the result and count the settle together, so a caller that has already seen the
                    // settle count reach its fire ordinal is guaranteed to read this outcome, not a stale one.
                    lastTestOutcomes.put(backend.id(), outcome);
                    counter(testSettles, backend.id()).incrementAndGet();
                }
                return;
            }
            if (!outcome.unresolvedFault()) {
                return;
            }
            boolean newlyQuarantined = quarantined.add(backend.id());
            RuntimeException failure = new IllegalStateException(outcome.toString());
            recordFault(backend.id() + " " + outcome.operation().name().toLowerCase()
                    + " " + outcome.state().name().toLowerCase(), failure);
            // A failed ordinary write should trigger one best-effort emergency zero. A failed stop is
            // already unresolved and must not recursively retry itself.
            if (newlyQuarantined && outcome.operation() == BackendOperation.SEND) {
                RuntimeException stopFault = runGuarded(
                        () -> backend.emergencyStop(StopReason.BACKEND_FAULT));
                if (stopFault != null) {
                    recordFault(backend.id() + " emergency stop failed", stopFault);
                }
            }
        });
    }

    /** Latest structured outcome per backend, preserving configured backend order. */
    public java.util.Map<String, BackendOutcome> latestOutcomes() {
        java.util.Map<String, BackendOutcome> out = new java.util.LinkedHashMap<>();
        for (HapticBackend backend : backends) {
            BackendOutcome latest = backend.latestOutcome();
            if (latest != null) {
                out.put(backend.id(), latest);
            }
        }
        return java.util.Collections.unmodifiableMap(out);
    }

    /** The last settled test result for a backend, or null if it has run no test this session. */
    public BackendOutcome lastTestOutcome(String backendId) {
        return lastTestOutcomes.get(backendId);
    }

    /** Count a dispatched test on a backend and return its fire ordinal, for the caller to wait on. */
    public long recordTestFire(String backendId) {
        return counter(testFires, backendId).incrementAndGet();
    }

    /** How many tests have been dispatched on a backend so far (the latest fire ordinal). */
    public long testFireCount(String backendId) {
        return counter(testFires, backendId).get();
    }

    /** How many dispatched tests on a backend have reached a terminal state. */
    public long testSettleCount(String backendId) {
        return counter(testSettles, backendId).get();
    }

    private static AtomicLong counter(java.util.concurrent.ConcurrentMap<String, AtomicLong> map,
                                      String backendId) {
        return map.computeIfAbsent(backendId, id -> new AtomicLong());
    }

    /** Failures stay visible even when a compensating stop or reconnect produces a newer outcome. */
    public java.util.Map<String, BackendOutcome> unresolvedFailures() {
        java.util.Map<String, BackendOutcome> out = new java.util.LinkedHashMap<>();
        for (HapticBackend backend : backends) {
            BackendOutcome failure = backend.unresolvedFailure();
            if (failure != null) {
                out.put(backend.id(), failure);
            }
        }
        return java.util.Collections.unmodifiableMap(out);
    }

    /** Lift a backend's quarantine (e.g. after the user reconnects it), letting it rejoin the fan-out. */
    public void clearQuarantine(String backendId) {
        quarantined.remove(backendId);
        for (HapticBackend backend : backends) {
            if (backend.id().equals(backendId)) {
                backend.clearOutcomeFailure();
            }
        }
    }

    /** Ids of backends currently quarantined after a render fault, for a persistent hub fault badge. */
    public java.util.Set<String> quarantined() {
        return java.util.Collections.unmodifiableSet(new java.util.HashSet<>(quarantined));
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
     * Fan the governed scene set to every non-quarantined backend for this cycle (ADR-018). Inline and
     * guarded, so one backend's render or forward failing cannot skip another or block the driver. A
     * backend that throws is stopped and quarantined (taken out of fan-out) so it can't keep re-entering
     * service and re-driving output; the fault, and a separately-failed stop, are recorded for the hub.
     * Returns the number of backends that faulted this cycle, so the driver can withhold its healthy
     * heartbeat for a faulting cycle.
     */
    public int onGovernedScenes(final List<HapticScene> governed, final long nowNs) {
        return onGovernedOutput(new GovernedOutput(governed,
                new net.minegasm.runtime.ResolvedDestinationSnapshot(0L, nowNs,
                        java.util.Collections.emptyMap())));
    }

    /** Fan one complete central governance result to every healthy backend. */
    public int onGovernedOutput(final GovernedOutput output) {
        int faulted = 0;
        for (final HapticBackend b : backends) {
            if (quarantined.contains(b.id())) {
                continue;
            }
            RuntimeException fault = runGuarded(() -> b.onGovernedOutput(output));
            if (fault != null) {
                faulted++;
                // Fail toward stopped for this backend, then quarantine it until it is reconnected.
                RuntimeException stopFault = runGuarded(() -> b.stop(StopReason.BACKEND_FAULT));
                quarantined.add(b.id());
                recordFault(b.id() + " render fault", fault);
                if (stopFault != null) {
                    recordFault(b.id() + " stop failed", stopFault);
                }
            }
        }
        return faulted;
    }

    private void recordFault(String label, RuntimeException cause) {
        faultCount.incrementAndGet();
        String message = cause.getClass().getSimpleName();
        String detail = cause.getMessage();
        if (detail != null && !detail.isEmpty()) {
            message += ": " + (detail.length() > 120 ? detail.substring(0, 120) : detail);
        }
        faults.addLast(label + " (" + message + ")");
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
            if (!quarantined.contains(b.id()) && b.isRenderingActive()) {
                return true;
            }
        }
        return false;
    }

    /** Whether any backend is driving the body (a renderer or an active bridge), for fatigue accounting. */
    public boolean anyBodyDriving() {
        for (HapticBackend b : backends) {
            if (!quarantined.contains(b.id()) && b.isBodyDriving()) {
                return true;
            }
        }
        return false;
    }

    /** Whether at least one healthy backend currently holds a non-zero authoritative output state. */
    public boolean anyOutputActive() {
        for (HapticBackend b : backends) {
            if (!quarantined.contains(b.id()) && b.isOutputActive()) {
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
     * backend's stop has run, and one backend throwing cannot skip another. A stop that throws is not
     * discarded: the backend is recorded as faulted and quarantined, so a failed stop, the operation that
     * matters most to safety, becomes a visible unresolved fault rather than a silent one that the UI still
     * reports as stopped (review P1-6). Returns the number of backends whose stop was not confirmed.
     */
    public int stopAll(final StopReason reason) {
        int unconfirmed = 0;
        for (final HapticBackend b : backends) {
            if (!confirmStop(b, "stop failed", () -> b.stop(reason))) {
                unconfirmed++;
            }
        }
        return unconfirmed;
    }

    /**
     * Fan an out-of-band emergency stop to every backend (the watchdog path). Like the others it never
     * blocks the caller, but unlike {@link #stopAll} each backend does only thread-safe work here, so this
     * is safe to call from the watchdog thread while the driver is mid-cycle. A failed emergency stop is
     * recorded and quarantined the same way, since this is the watchdog's last resort. The fault surface is
     * thread-safe, so recording from the watchdog thread is fine. Returns the number left unconfirmed.
     */
    public int emergencyStop(final StopReason reason) {
        int unconfirmed = 0;
        for (final HapticBackend b : backends) {
            if (!confirmStop(b, "emergency stop failed", () -> b.emergencyStop(reason))) {
                unconfirmed++;
            }
        }
        return unconfirmed;
    }

    /**
     * Run a stop action; on a synchronous failure record the fault and quarantine the backend, so a stop
     * that could not be confirmed leaves the backend in a fault state instead of being swallowed. Returns
     * whether the synchronous request was accepted. A backend whose call throws is taken out of fan-out
     * immediately. A later provider failure arrives through the backend outcome listener and applies the
     * same quarantine without blocking this caller.
     */
    private boolean confirmStop(HapticBackend b, String label, Runnable action) {
        RuntimeException fault = runGuarded(action);
        if (fault != null) {
            quarantined.add(b.id());
            recordFault(b.id() + " " + label, fault);
            return false;
        }
        return true;
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
        return runGuarded(action) == null;
    }

    /** Run an action, returning the runtime exception it threw or null if it completed. */
    private static RuntimeException runGuarded(Runnable action) {
        try {
            action.run();
            return null;
        } catch (RuntimeException isolated) {
            // Isolated: one backend failing must not stop the fan-out to the others. The caller decides
            // whether to act on the failure (scene fan-out stops, records, and quarantines it).
            return isolated;
        }
    }
}
