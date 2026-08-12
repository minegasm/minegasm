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

/**
 * The Buttplug rendering backend (brief 0003 §3.2, ADR-018). It owns the device-specific half of the
 * pipeline, the {@link SceneMixer}, the {@link FeatureScheduler}, and the {@link HapticProvider}, and
 * turns the central governed scene set into per-feature commands each cycle. It is the first rendering
 * backend, not a privileged one: a future native integration implements the same seam and runs
 * alongside it.
 *
 * <p>The governance driver calls {@link #onGovernedScenes} on its thread once per cycle; rendering and
 * dispatch happen inline there, and {@link #stop(StopReason)} clears the scheduler and sends the protocol
 * stop. Because the driver serializes cycle against stop under its own monitor, and these calls are
 * synchronous, no command can be dispatched after a stop returns.
 */
public final class ButtplugBackend implements HapticBackend {

    private final SceneMixer mixer = new SceneMixer();
    private final FeatureScheduler scheduler = new FeatureScheduler();
    private final HapticProvider provider;
    private final Supplier<RuntimeConfig> config;

    private volatile boolean outputEnabled = true;
    private long pausedRegistryGeneration;
    private volatile long lastHealthyCycleNs;
    private volatile List<OutputCommand> lastCommands = Collections.emptyList();
    private volatile HapticScene testScene; // an isolated test injected into this backend's own render

    @Override
    public void test(HapticScene scene, long nowNs) {
        this.testScene = scene; // rendered alongside the governed set until its own expiry
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
        RuntimeConfig cfg = config.get();
        DeviceRegistrySnapshot snapshot = provider.devices();
        List<HapticScene> effective = withTest(governed, nowNs);
        Map<String, EndpointTarget> targets = (cfg.enabled() && outputEnabled)
                ? mixer.render(effective, snapshot, cfg, nowNs)
                : Collections.emptyMap(); // drive any held endpoints to zero, then stay silent
        List<OutputCommand> commands = scheduler.accept(targets, snapshot, nowNs);
        for (OutputCommand command : commands) {
            provider.send(command);
        }
        lastCommands = commands;
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
            return governed;
        }
        List<HapticScene> combined = new java.util.ArrayList<>(governed);
        combined.add(test);
        return combined;
    }

    @Override
    public boolean isRenderingActive() {
        return config.get().enabled() && outputEnabled && !provider.devices().isEmpty();
    }

    @Override
    public boolean registryChangedSincePause() {
        return provider.devices().generation() != pausedRegistryGeneration;
    }

    @Override
    public void stop(StopReason reason) {
        testScene = null; // a live isolated test must never outlast a stop and resume onto the body
        scheduler.reset();
        lastCommands = Collections.emptyList(); // nothing is being dispatched after a stop
        provider.stop(StopSelection.all());
    }

    @Override
    public void pause() {
        testScene = null; // don't let a test resume when the session does
        pausedRegistryGeneration = provider.devices().generation();
        scheduler.reset();
        lastCommands = Collections.emptyList(); // hardware is stopped while paused
        provider.stop(StopSelection.all());
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
            testScene = null; // panic/master-off drops any live test so it can't resume on re-enable
        }
    }

    @Override
    public long lastHealthyCycleNs() {
        return lastHealthyCycleNs;
    }

    @Override
    public void close() {
        testScene = null;
        // The provider is owned and closed by MinegasmClient.
    }

    /** The commands dispatched on the last cycle, for tests and diagnostics (e.g. the test pulse path). */
    public List<OutputCommand> lastCommands() {
        return lastCommands;
    }
}
