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
     * Whether this backend is currently driving the body at all, for fatigue accounting. Rendering
     * backends answer this with {@link #isRenderingActive}. A semantic backend (the bridge) can also drive
     * a physical device through its adapter, so an active, connected bridge conservatively counts as
     * body-driving even though it renders no level itself, until richer downstream feedback exists (review
     * P1-6). Default follows {@link #isRenderingActive}.
     */
    default boolean isBodyDriving() {
        return isRenderingActive();
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

    /**
     * Fire an isolated, bounded test on just this backend, outside the governed pipeline so it does not
     * fan to other backends (the per-integration "test output" buttons). The backend holds the scene for
     * its lifetime and releases it; the driver keeps cycling meanwhile, so a rendering backend must inject
     * the scene into its own render until it expires rather than being overwritten. Default no-op.
     */
    default void test(HapticScene scene, long nowNs) {
    }

    /** Stop all output for this backend now. Must be non-blocking and take effect synchronously. */
    void stop(StopReason reason);

    /**
     * Out-of-band emergency stop: halt this backend's output from a thread other than the driver, without
     * waiting on the driver's cycle monitor, so a watchdog can stop a backend even while it is hung inside
     * a cycle. Implementations must touch only thread-safe state here (a volatile latch, a synchronized
     * queue, a provider stop dispatched off-thread) and never the single-threaded render bookkeeping that
     * {@link #stop} clears, since a cycle may be running concurrently. Default no-op. The governor is not
     * reset on this path, so callers keep output latched off until it is explicitly re-enabled.
     */
    default void emergencyStop(StopReason reason) {
    }

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
