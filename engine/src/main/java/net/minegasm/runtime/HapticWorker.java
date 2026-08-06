package net.minegasm.runtime;

import net.minegasm.buttplug.HapticProvider;
import net.minegasm.buttplug.OutputCommand;
import net.minegasm.buttplug.StopSelection;
import net.minegasm.config.RuntimeConfig;
import net.minegasm.core.HapticScene;
import net.minegasm.device.DeviceRegistrySnapshot;
import net.minegasm.render.EndpointTarget;
import net.minegasm.time.Clock;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * The single haptic worker (brief §6.4). Each cycle: pull the governed scene snapshot from the central
 * {@link SceneGovernor}, render per-feature targets against the live registry, schedule concrete
 * commands, and dispatch them to the provider. Scene holding, coalescing, and expiry now live in the
 * governor (ADR-018); the worker owns the scheduler and per-cycle rendering. Timing is monotonic, and
 * cycles are driveable directly by tests with a {@link net.minegasm.time.FakeClock}.
 *
 * <p>The worker's own monitor is what makes a stop safe: {@link #cycle} snapshots, renders, and
 * dispatches while holding it, and {@link #requestStop} clears the governor and sends the protocol stop
 * while holding it too, so a stop can never interleave with a cycle and leave a stale scene to render.
 */
public final class HapticWorker {

    private static final long CYCLE_MS = 15;

    private final SceneGovernor scenes;
    private final SceneMixer mixer = new SceneMixer();
    private final FeatureScheduler scheduler = new FeatureScheduler();
    private final HapticProvider provider;
    private final Clock clock;
    private final Supplier<RuntimeConfig> config;
    private GovernedSceneForwarder bridgeForwarder; // null unless a bridge backend is wired

    private final AtomicLong lastHealthyCycleNs = new AtomicLong();
    private volatile boolean outputEnabled = true;
    private volatile StopReason lastStopReason;
    private boolean paused;
    private long pausedAtNs;
    private long pausedRegistryGeneration;
    private ScheduledExecutorService executor;
    private ScheduledFuture<?> loop;

    public HapticWorker(SceneGovernor scenes, HapticProvider provider, Clock clock,
                        Supplier<RuntimeConfig> config) {
        this.scenes = scenes;
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
        scenes.submit(scene, clock.nanoTime());
    }

    /**
     * Wire a change-driven forwarder that fans the governed scene set to a semantic backend (the bridge).
     * Set once at construction time before the loop starts; null means no such backend.
     */
    public synchronized void setBridgeForwarder(GovernedSceneForwarder forwarder) {
        this.bridgeForwarder = forwarder;
    }

    /**
     * Run one worker cycle at {@code nowNs} and return the commands dispatched (for tests/diagnostics).
     */
    public synchronized List<OutputCommand> cycle(long nowNs) {
        if (paused) {
            lastHealthyCycleNs.set(nowNs);
            return java.util.Collections.emptyList();
        }
        RuntimeConfig cfg = config.get();
        DeviceRegistrySnapshot snapshot = provider.devices();
        boolean outputActive = cfg.enabled() && outputEnabled;
        // Pull the governed set: the governor expires stale scenes, decays and accounts fatigue, and
        // bakes fatigue attenuation into the primitives. Only accrue load when output can actually reach
        // the body, i.e. a device is connected, so an enabled mod with nothing attached never fatigues
        // (the old per-render accounting got this for free; brief §10.6).
        boolean accountLoad = outputActive && !snapshot.isEmpty();
        List<HapticScene> held = scenes.govern(nowNs, cfg.fatigueProtection(), accountLoad);

        Map<String, EndpointTarget> targets;
        if (!outputActive) {
            targets = java.util.Collections.emptyMap(); // drive any held endpoints to zero, then stay silent
        } else {
            targets = mixer.render(held, snapshot, cfg, nowNs);
        }

        List<OutputCommand> commands = scheduler.accept(targets, snapshot, nowNs);
        for (OutputCommand command : commands) {
            provider.send(command);
        }
        // Fan the same governed set to the semantic bridge, change-driven. Skipped while output is
        // suppressed; a running effect self-expires on the adapter via its TTL.
        if (bridgeForwarder != null && outputActive) {
            bridgeForwarder.forward(held, nowNs);
        }
        lastHealthyCycleNs.set(nowNs);
        return commands;
    }

    /**
     * Stop all output immediately and clear held scene state so a delayed cycle cannot reassert output
     * (brief §9.10). Clearing the governor and sending the protocol {@code StopCmd} both happen under
     * the worker monitor, so this cannot interleave with a {@link #cycle}: once it returns, the governor
     * is empty and the next cycle renders nothing.
     */
    public synchronized void requestStop(StopReason reason) {
        this.lastStopReason = reason;
        scenes.reset(); // drops held scenes and forgets fatigue load
        scheduler.reset();
        if (bridgeForwarder != null) {
            // Forget forwarding state under the worker monitor: the governor is now empty, so the next
            // cycle forwards nothing and cannot overtake the bridge's stop frame; and the next real scene
            // after a resume is sent afresh rather than being suppressed as a duplicate.
            bridgeForwarder.reset();
        }
        paused = false;
        pausedAtNs = 0;
        provider.stop(StopSelection.all());
    }

    /** Stop hardware but preserve and freeze scene state for a possible resume. */
    public synchronized void pause() {
        if (paused) return;
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
            // Device-specific decision, kept on the worker: a scene frozen against a device set that has
            // since changed must not resume onto whatever now occupies those indices.
            scenes.reset();
        } else {
            long deltaNs = Math.max(0, nowNs - pausedAtNs);
            scenes.shiftTime(deltaNs); // shifts held scenes and the fatigue clock together
        }
        scheduler.reset();
        paused = false;
        pausedAtNs = 0;
    }

    public synchronized void discardPause() {
        if (!paused) return;
        scenes.reset();
        scheduler.reset();
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
        return scenes.activeSceneCount();
    }
}
