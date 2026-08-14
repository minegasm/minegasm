package net.minegasm.backend;

import net.minegasm.buttplug.HapticProvider;
import net.minegasm.buttplug.OutputCommand;
import net.minegasm.buttplug.StopSelection;
import net.minegasm.config.RuntimeConfig;
import net.minegasm.core.HapticScene;
import net.minegasm.device.DeviceRegistrySnapshot;
import net.minegasm.render.EndpointTarget;
import net.minegasm.runtime.FeatureScheduler;
import net.minegasm.runtime.SceneMixer;
import net.minegasm.runtime.StopReason;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.function.Consumer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The Buttplug rendering backend (brief 0003 §3.2, ADR-018). It owns the device-specific half of the
 * pipeline, the {@link SceneMixer}, the {@link FeatureScheduler}, and the {@link HapticProvider}, and
 * turns the central governed scene set into per-feature commands each cycle. It is the first rendering
 * backend, not a privileged one: a future native integration implements the same seam and runs
 * alongside it.
 *
 * <p>The governance driver calls {@link #onGovernedScenes} once per cycle. Rendering happens inline, while
 * providers complete delivery asynchronously. Lifecycle generations and a short dispatch boundary ensure
 * that a normal or out-of-band stop cannot be followed by a command from an older render.
 */
public final class ButtplugBackend implements HapticBackend {

    private final SceneMixer mixer = new SceneMixer();
    private final FeatureScheduler scheduler = new FeatureScheduler();
    private final HapticProvider provider;
    private final Supplier<RuntimeConfig> config;

    private volatile boolean outputEnabled = true;
    private long pausedRegistryGeneration;
    private volatile long lastHealthyCycleNs;
    private volatile boolean outputActive;
    private volatile List<OutputCommand> lastCommands = Collections.emptyList();
    private volatile HapticScene testScene; // an isolated test injected into this backend's own render
    private final AtomicLong operationGeneration = new AtomicLong();
    private final AtomicLong lifecycleGeneration = new AtomicLong();
    // Rendering may take long enough for an out-of-band watchdog stop to land mid-cycle. The lifecycle
    // generation is captured before rendering, then checked together with the non-blocking provider call
    // under this tiny lock. That gives stop a strict ordering point without ever waiting for rendering.
    private final Object dispatchLock = new Object();
    private final BackendOutcomeTracker outcomes = new BackendOutcomeTracker(
            "buttplug", System::nanoTime, 5_000L);
    private volatile CompletableFuture<Void> pendingTest;
    private volatile long pendingTestGeneration;

    @Override
    public void test(HapticScene scene, long nowNs) {
        cancelPendingTest();
        this.testScene = scene; // rendered alongside the governed set until its own expiry
        long generation = operationGeneration.incrementAndGet();
        pendingTestGeneration = generation;
        CompletableFuture<Void> completion = new CompletableFuture<>();
        pendingTest = completion;
        outcomes.observe(BackendOperation.TEST, generation, completion,
                () -> generation != pendingTestGeneration);
    }

    public ButtplugBackend(HapticProvider provider, Supplier<RuntimeConfig> config) {
        this.provider = provider;
        this.config = config;
    }

    @Override
    public String id() {
        return "buttplug";
    }

    @Override
    public void start() {
        // The provider's connection lifecycle is managed by MinegasmClient; nothing to start here.
    }

    @Override
    public void onGovernedScenes(List<HapticScene> governed, long nowNs) {
        final long lifecycle = lifecycleGeneration.get();
        RuntimeConfig cfg = config.get();
        DeviceRegistrySnapshot snapshot = provider.devices();
        List<HapticScene> effective = withTest(governed, nowNs);
        Map<String, EndpointTarget> targets = (cfg.enabled() && outputEnabled)
                ? mixer.render(effective, snapshot, cfg, nowNs)
                : Collections.emptyMap(); // drive any held endpoints to zero, then stay silent
        boolean desiredActive = false;
        for (EndpointTarget target : targets.values()) {
            if (target.level() > 0f) {
                desiredActive = true;
                break;
            }
        }
        List<OutputCommand> commands = scheduler.accept(targets, snapshot, nowNs);
        List<OutputCommand> dispatched = new java.util.ArrayList<>();
        List<CompletableFuture<Void>> completions = new java.util.ArrayList<>();
        for (OutputCommand command : commands) {
            long generation = operationGeneration.incrementAndGet();
            CompletionStage<Void> sent;
            synchronized (dispatchLock) {
                if (lifecycle != lifecycleGeneration.get() || !outputEnabled) {
                    break;
                }
                sent = provider.send(command);
            }
            outcomes.observe(BackendOperation.SEND, generation, sent,
                    () -> lifecycle != lifecycleGeneration.get());
            dispatched.add(command);
            completions.add(sent.toCompletableFuture());
        }
        completePendingTest(dispatched, completions);
        lastCommands = dispatched;
        synchronized (dispatchLock) {
            outputActive = lifecycle == lifecycleGeneration.get() && outputEnabled && desiredActive;
        }
        lastHealthyCycleNs = nowNs;
    }

    /** Append a live isolated test scene to the governed set, dropping it once its lifetime has passed. */
    private List<HapticScene> withTest(List<HapticScene> governed, long nowNs) {
        HapticScene test = testScene;
        if (test == null) {
            return governed;
        }
        if (nowNs >= test.expiresAtNs()) {
            testScene = null;
            CompletableFuture<Void> completion = pendingTest;
            if (completion != null && !completion.isDone()) {
                completion.completeExceptionally(new IllegalStateException("no compatible test target"));
            }
            pendingTest = null;
            return governed;
        }
        List<HapticScene> combined = new java.util.ArrayList<>(governed);
        combined.add(test);
        return combined;
    }

    private void completePendingTest(List<OutputCommand> commands,
                                     List<CompletableFuture<Void>> completions) {
        final CompletableFuture<Void> test = pendingTest;
        if (test == null || test.isDone() || commands.isEmpty()) {
            return;
        }
        pendingTest = null;
        CompletableFuture.allOf(completions.toArray(new CompletableFuture<?>[completions.size()]))
                .whenComplete((ignored, error) -> {
                    if (error == null) {
                        test.complete(null);
                    } else {
                        test.completeExceptionally(error);
                    }
                });
    }

    private void cancelPendingTest() {
        pendingTestGeneration = operationGeneration.incrementAndGet();
        CompletableFuture<Void> test = pendingTest;
        pendingTest = null;
        if (test != null) {
            test.cancel(false);
        }
    }

    @Override
    public void setOutcomeListener(Consumer<BackendOutcome> listener) {
        outcomes.setListener(listener);
    }

    @Override
    public BackendOutcome latestOutcome() {
        return outcomes.latest();
    }

    @Override
    public BackendOutcome unresolvedFailure() {
        return outcomes.unresolvedFailure();
    }

    @Override
    public void clearOutcomeFailure() {
        outcomes.clearFailure();
    }

    @Override
    public boolean isRenderingActive() {
        return config.get().enabled() && outputEnabled && !provider.devices().isEmpty();
    }

    @Override
    public boolean isOutputActive() {
        return outputActive;
    }

    @Override
    public boolean registryChangedSincePause() {
        return provider.devices().generation() != pausedRegistryGeneration;
    }

    @Override
    public void stop(StopReason reason) {
        long lifecycle = lifecycleGeneration.incrementAndGet();
        outputActive = false;
        cancelPendingTest();
        testScene = null; // a live isolated test must never outlast a stop and resume onto the body
        scheduler.reset();
        lastCommands = Collections.emptyList(); // nothing is being dispatched after a stop
        requestStop(lifecycle);
    }

    @Override
    public void emergencyStop(StopReason reason) {
        // Out-of-band (watchdog) path: only thread-safe actions, no scheduler touch, since a cycle may be
        // running on the driver thread. Zero the hardware through the provider's stop, which is dispatched
        // off the caller's thread so it lands even while the worker is hung. Deliberately does not latch
        // outputEnabled: the watchdog stop is transient and recovers on its own (a real panic latches
        // through setOutputEnabled instead). Clearing the volatile test scene is safe and prevents a
        // pending test from surviving the stop.
        long lifecycle = lifecycleGeneration.incrementAndGet();
        outputActive = false;
        cancelPendingTest();
        testScene = null;
        requestStop(lifecycle);
    }

    @Override
    public void pause() {
        long lifecycle = lifecycleGeneration.incrementAndGet();
        outputActive = false;
        cancelPendingTest();
        testScene = null; // don't let a test resume when the session does
        pausedRegistryGeneration = provider.devices().generation();
        scheduler.reset();
        lastCommands = Collections.emptyList(); // hardware is stopped while paused
        requestStop(lifecycle);
    }

    /** Order a provider stop after any send that already crossed the dispatch boundary. */
    private void requestStop(long lifecycle) {
        CompletionStage<Void> stopped;
        synchronized (dispatchLock) {
            stopped = provider.stop(StopSelection.all());
        }
        long generation = operationGeneration.incrementAndGet();
        outcomes.observe(BackendOperation.STOP, generation, stopped,
                () -> lifecycle != lifecycleGeneration.get());
    }

    @Override
    public void resume() {
        // Scene freeze/shift and the discard decision live in the driver; the renderer only clears its
        // per-feature scheduling state so it re-derives cleanly against the live device snapshot.
        scheduler.reset();
    }

    @Override
    public void discardPause() {
        testScene = null;
        scheduler.reset();
    }

    @Override
    public void setOutputEnabled(boolean enabled) {
        this.outputEnabled = enabled;
        if (!enabled) {
            outputActive = false;
            testScene = null; // panic/master-off drops any live test so it can't resume on re-enable
        }
    }

    @Override
    public long lastHealthyCycleNs() {
        return lastHealthyCycleNs;
    }

    @Override
    public void close() {
        cancelPendingTest();
        testScene = null;
        outcomes.close();
        // The provider is owned and closed by MinegasmClient.
    }

    /** The commands dispatched on the last cycle, for tests and diagnostics (e.g. the test pulse path). */
    public List<OutputCommand> lastCommands() {
        return lastCommands;
    }
}
