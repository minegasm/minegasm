package net.minegasm.backend;

import net.minegasm.runtime.StopReason;

/**
 * One output backend behind the engine's device-neutral seam (brief 0003 §3.2). Scene fan-out is now
 * governed centrally (ADR-018): the {@link net.minegasm.runtime.SceneGovernor} holds and governs scenes,
 * the worker pulls the governed set, and a semantic backend receives it change-driven. So this interface
 * carries lifecycle, not scene delivery: the {@link BackendCoordinator} fans start/stop/pause/close to
 * every backend, but scenes reach each backend through the governor, not through this interface.
 *
 * <p>{@code stop} must be non-blocking to the caller and take effect synchronously (when it returns,
 * output is stopping and no later cycle can reassert it): the in-memory clear happens inline, any
 * hardware/network I/O off the caller's thread inside the backend.
 */
public interface HapticBackend extends AutoCloseable {

    /** Stable identifier for routing, status, and diagnostics (e.g. {@code "buttplug"}). */
    String id();

    /** Begin running (start the worker loop, open a connection, etc.). */
    void start();

    /** Stop all output for this backend now. Must be non-blocking and take effect synchronously. */
    void stop(StopReason reason);

    /** Stop hardware but preserve state for a possible resume (pause behavior). */
    void pause();

    /** Resume preserved state after a {@link #pause()}. */
    void resume();

    /** Drop any preserved pause state without resuming it. */
    void discardPause();

    /** Master enable/disable for this backend's output (used by panic and config). */
    void setOutputEnabled(boolean enabled);

    /** Monotonic nanos of this backend's last healthy activity, for health/watchdog aggregation. */
    long lastHealthyCycleNs();

    /** Permanently release this backend's resources. */
    @Override
    void close();
}
