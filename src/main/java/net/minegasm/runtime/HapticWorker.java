package net.minegasm.runtime;

import net.minegasm.buttplug.HapticProvider;
import net.minegasm.buttplug.OutputCommand;
import net.minegasm.buttplug.StopSelection;
import net.minegasm.config.RuntimeConfig;
import net.minegasm.core.HapticRole;
import net.minegasm.core.HapticScene;
import net.minegasm.device.DeviceRegistrySnapshot;
import net.minegasm.render.EndpointTarget;
import net.minegasm.time.Clock;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * The single haptic worker (brief §6.4). Each cycle: drain the scene ingress into the mixer, expire
 * stale scenes, render per-feature targets against the live registry, schedule concrete commands,
 * and dispatch them to the provider. Timing is monotonic; the worker owns all mixer/scheduler state
 * so no fine-grained locks are needed. Cycles are also driveable directly by tests with a
 * {@link net.minegasm.time.FakeClock}.
 */
public final class HapticWorker {

    private static final long CYCLE_MS = 15;

    private final SceneIngressQueue ingress;
    private final SceneMixer mixer = new SceneMixer();
    private final FeatureScheduler scheduler = new FeatureScheduler();
    private final FatigueGovernor governor = new FatigueGovernor();
    private final HapticProvider provider;
    private final Clock clock;
    private final Supplier<RuntimeConfig> config;

    private final AtomicLong lastHealthyCycleNs = new AtomicLong();
    private long lastCycleNs;
    private volatile boolean outputEnabled = true;
    private volatile StopReason lastStopReason;
    private boolean paused;
    private long pausedAtNs;
    private long pausedRegistryGeneration;
    private ScheduledExecutorService executor;
    private ScheduledFuture<?> loop;

    public HapticWorker(SceneIngressQueue ingress, HapticProvider provider, Clock clock,
                        Supplier<RuntimeConfig> config) {
        this.ingress = ingress;
        this.provider = provider;
        this.clock = clock;
        this.config = config;
    }

    /** Start the real-time loop on a dedicated daemon thread. */
    public synchronized void start() {
        if (loop != null) {
            return;
        }
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "minegasm-worker");
            t.setDaemon(true);
            return t;
        });
        loop = executor.scheduleAtFixedRate(() -> {
            try {
                cycle(clock.nanoTime());
            } catch (RuntimeException ex) {
                // A worker fault must fail toward stopped output, never escape silently (brief §12.4).
                requestStop(StopReason.WATCHDOG);
            }
        }, CYCLE_MS, CYCLE_MS, TimeUnit.MILLISECONDS);
    }

    public synchronized void shutdown() {
        if (loop != null) {
            loop.cancel(false);
            loop = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
        requestStop(StopReason.SHUTDOWN);
    }

    /** Offer a scene from the client thread; non-blocking and bounded. */
    public void offer(HapticScene scene) {
        ingress.offer(scene, clock.nanoTime());
    }

    /**
     * Run one worker cycle at {@code nowNs} and return the commands dispatched (for tests/diagnostics).
     */
    public synchronized List<OutputCommand> cycle(long nowNs) {
        if (paused) {
            lastHealthyCycleNs.set(nowNs);
            return List.of();
        }
        for (HapticScene scene : ingress.drain()) {
            mixer.add(scene);
        }
        mixer.update(nowNs);
        governor.update(nowNs);

        RuntimeConfig cfg = config.get();
        DeviceRegistrySnapshot snapshot = provider.devices();
        long dt = lastCycleNs == 0 ? 0 : nowNs - lastCycleNs;
        lastCycleNs = nowNs;

        Map<String, EndpointTarget> targets;
        if (!cfg.enabled() || !outputEnabled) {
            targets = Map.of(); // drive any held endpoints to zero, then stay silent
        } else {
            targets = mixer.render(snapshot, cfg, governor, cfg.fatigueProtection(), nowNs);
            recordFatigue(targets, dt);
        }

        List<OutputCommand> commands = scheduler.accept(targets, snapshot, nowNs);
        for (OutputCommand command : commands) {
            provider.send(command);
        }
        lastHealthyCycleNs.set(nowNs);
        return commands;
    }

    /**
     * Stop all output immediately and clear local state so a delayed cycle cannot reassert output
     * (brief §9.10). Sends the protocol {@code StopCmd} (bypasses the timing gap) and forgets all
     * scheduler/mixer state.
     */
    public synchronized void requestStop(StopReason reason) {
        this.lastStopReason = reason;
        mixer.clear();
        ingress.clear();
        scheduler.reset();
        governor.reset();
        paused = false;
        pausedAtNs = 0;
        provider.stop(StopSelection.all());
    }

    /** Stop hardware but preserve and freeze scene state for a possible resume. */
    public synchronized void pause() {
        if (paused) return;
        for (HapticScene scene : ingress.drain()) {
            mixer.add(scene);
        }
        paused = true;
        pausedAtNs = clock.nanoTime();
        pausedRegistryGeneration = provider.devices().generation();
        scheduler.reset();
        provider.stop(StopSelection.all());
    }

    /** Resume preserved scenes only when devices still represent the same registry generation. */
    public synchronized void resume() {
        if (!paused) return;
        long nowNs = clock.nanoTime();
        if (provider.devices().generation() != pausedRegistryGeneration) {
            mixer.clear();
            ingress.clear();
            governor.reset();
        } else {
            long deltaNs = Math.max(0, nowNs - pausedAtNs);
            mixer.shiftTime(deltaNs);
            governor.shiftTime(deltaNs);
        }
        scheduler.reset();
        lastCycleNs = 0;
        paused = false;
        pausedAtNs = 0;
    }

    public synchronized void discardPause() {
        if (!paused) return;
        mixer.clear();
        ingress.clear();
        scheduler.reset();
        governor.reset();
        paused = false;
        pausedAtNs = 0;
    }

    public synchronized boolean isPaused() {
        return paused;
    }

    public void setOutputEnabled(boolean enabled) {
        this.outputEnabled = enabled;
    }

    public boolean isOutputEnabled() {
        return outputEnabled;
    }

    public long lastHealthyCycleNs() {
        return lastHealthyCycleNs.get();
    }

    public StopReason lastStopReason() {
        return lastStopReason;
    }

    public int activeSceneCount() {
        return mixer.activeSceneCount();
    }

    private void recordFatigue(Map<String, EndpointTarget> targets, long dtNs) {
        if (dtNs <= 0 || targets.isEmpty()) {
            return;
        }
        Map<HapticRole, Float> maxByRole = new EnumMap<>(HapticRole.class);
        for (EndpointTarget t : targets.values()) {
            maxByRole.merge(t.role(), t.level(), Math::max);
        }
        maxByRole.forEach((role, level) -> governor.record(role, level, dtNs));
    }
}
