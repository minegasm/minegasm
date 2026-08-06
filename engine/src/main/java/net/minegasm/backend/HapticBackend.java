package net.minegasm.backend;

import net.minegasm.core.HapticScene;
import net.minegasm.runtime.StopReason;

import java.util.List;

/**
 * One output backend behind the engine's device-neutral seam (brief 0003 §3.2, ADR-018). The neutral
 * governance driver advances the central {@link net.minegasm.runtime.SceneGovernor} once per cycle and
 * fans the governed scene set to every backend through {@link #onGovernedScenes}. What a backend does
 * with that set is its own concern: a <em>rendering</em> backend (Buttplug, a future native integration)
 * samples the effect and dispatches device commands; a <em>semantic</em> backend (the local bridge)
 * forwards the scene to an external adapter change-driven. Any number of each run concurrently.
 *
 * <p>{@code stop} and {@code onGovernedScenes} must be non-blocking to the caller and synchronous, so the
 * driver's stop-safety holds transitively: the driver resets the governor and then fans {@code stop} under
 * one monitor, and no backend may render or forward after that returns.
 */
public interface HapticBackend extends AutoCloseable {

    /** Stable identifier for routing, status, and diagnostics (e.g. {@code "buttplug"}). */
    String id();

    /** Begin running (open a connection, etc.). */
    void start();

    /**
     * Consume the governed scene set for this cycle: render it to devices, or forward it. Called on the
     * driver thread once per cycle with the already-governed (coalesced, fatigue-attenuated) set. Must be
     * non-blocking and self-gate on the backend's own enabled/output state. Default is a no-op so a
     * lifecycle-only backend needs no scene handling.
     */
    default void onGovernedScenes(List<HapticScene> governed, long nowNs) {
    }

    /**
     * Whether this backend is a rendering backend that is currently able to drive the body (enabled, not
     * panicked, with a device present). The driver uses it to decide whether fatigue accrues this cycle:
     * with nothing rendering, nothing fatigues. Semantic backends return false. Default false.
     */
    default boolean isRenderingActive() {
        return false;
    }

    /**
     * Whether this backend's device set changed while paused, so frozen scenes should be dropped rather
     * than resumed onto a different device set. Device-specific; the driver asks every backend on resume.
     * Default false. (Resetting the shared governor on any one backend's change is correct while only one
     * renderer exists; revisit when a second rendering backend lands.)
     */
    default boolean registryChangedSincePause() {
        return false;
    }

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
