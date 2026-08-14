package net.minegasm.backend;

import net.minegasm.core.HapticScene;
import net.minegasm.runtime.StopReason;
import net.minegasm.runtime.GovernedOutput;

import java.util.List;
import java.util.function.Consumer;

/**
 * One output backend behind the engine's device-neutral seam (brief 0003 §3.2, ADR-018). The neutral
 * governance driver advances the central {@link net.minegasm.runtime.SceneGovernor} once per cycle and
 * fans one {@link GovernedOutput} to every backend through {@link #onGovernedOutput}. Rendering backends
 * use its active scenes for physical routing. Semantic backends use its authoritative logical destination
 * snapshot. Any number of each run concurrently.
 *
 * <p>Calls are non-blocking. A stop must synchronously invalidate newer output locally, then report its
 * eventual transport or hardware completion through {@link BackendOutcome}. Requested and confirmed stop
 * are deliberately different states.
 */
public interface HapticBackend extends AutoCloseable {

    /** Install the coordinator's outcome sink. Backends call it from completion threads. */
    default void setOutcomeListener(Consumer<BackendOutcome> listener) {
    }

    /** Most recent accepted or completed operation, for status cards and structured test feedback. */
    default BackendOutcome latestOutcome() {
        return null;
    }

    /** Persistent failed or timed-out operation, cleared only by explicit recovery. */
    default BackendOutcome unresolvedFailure() {
        return null;
    }

    default void clearOutcomeFailure() {
    }

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
     * Consume the complete central result. Rendering backends can retain scene-level physical route
     * refinement; semantic backends should use the authoritative destination snapshot. The compatibility
     * default keeps lifecycle-only and existing rendering backends source-simple.
     */
    default void onGovernedOutput(GovernedOutput output) {
        onGovernedScenes(output.scenes(), output.destinations().sampledAtNs());
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
     * Whether this backend is currently able to drive the body, for fatigue accounting when the governed
     * result contains output. Rendering backends answer this with {@link #isRenderingActive}. A connected
     * semantic bridge counts conservatively because its adapter may own a physical device. This is
     * capability, distinct from {@link #isOutputActive()}.
     */
    default boolean isBodyDriving() {
        return isRenderingActive();
    }

    /** Whether this backend's most recent authoritative state contains live physical output. */
    default boolean isOutputActive() {
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

    /**
     * Fire an isolated, bounded test on just this backend, outside the governed pipeline so it does not
     * fan to other backends (the per-integration "test output" buttons). The backend holds the scene for
     * its lifetime and releases it; the driver keeps cycling meanwhile, so a rendering backend must inject
     * the scene into its own render until it expires rather than being overwritten. Default no-op.
     */
    default void test(HapticScene scene, long nowNs) {
    }

    /** Invalidate output now and request a stop. Physical confirmation is reported asynchronously. */
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
