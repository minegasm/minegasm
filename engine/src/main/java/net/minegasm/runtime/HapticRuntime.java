package net.minegasm.runtime;

import net.minegasm.backend.BackendCoordinator;
import net.minegasm.backend.ButtplugBackend;
import net.minegasm.backend.HapticBackend;
import net.minegasm.bridge.BridgeBackend;
import net.minegasm.bridge.BridgeTransport;
import net.minegasm.buttplug.HapticProvider;
import net.minegasm.config.RuntimeConfig;
import net.minegasm.core.HapticIntent;
import net.minegasm.core.RawGameEvent;
import net.minegasm.observe.ClientStateSnapshot;
import net.minegasm.observe.HapticAggregator;
import net.minegasm.observe.StateTracker;
import net.minegasm.observe.StateTransitions;
import net.minegasm.observe.TickEventBuffer;
import net.minegasm.pack.PackRegistry;
import net.minegasm.recipe.RecipeEngine;
import net.minegasm.time.Clock;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Top-level engine facade wiring observation → intents → scenes → worker (brief §5, §6.3). The
 * Minecraft layer feeds it raw events and a per-tick state snapshot; everything downstream is pure
 * and off the client thread's critical path (the client thread only builds immutable objects and
 * offers them to a bounded queue). This class is Minecraft- and Buttplug-library-free.
 */
public final class HapticRuntime {

    /** A worker silent for longer than this is considered stalled and gets a safety stop. */
    private static final long WATCHDOG_STALL_MS = 2_000;

    private final TickEventBuffer tickBuffer = new TickEventBuffer();
    private final StateTracker tracker = new StateTracker();
    private final HapticAggregator aggregator = new HapticAggregator();
    private final RecipeEngine recipe;
    private final SceneGovernor sceneGovernor = new SceneGovernor();
    private final HapticProvider provider;
    private final ButtplugBackend buttplug;
    private final HapticWorker worker;
    private final BackendCoordinator coordinator;
    private final LifecycleController lifecycle;
    private final Watchdog watchdog;
    private final Clock clock;
    private final Supplier<RuntimeConfig> config;

    private boolean gameActive;
    private boolean worldPresent;

    public HapticRuntime(HapticProvider provider, Clock clock, Supplier<RuntimeConfig> config) {
        this(provider, clock, config, new PackRegistry(), null);
    }

    public HapticRuntime(HapticProvider provider, Clock clock, Supplier<RuntimeConfig> config,
                         PackRegistry packs) {
        this(provider, clock, config, packs, null);
    }

    /**
     * @param bridgeTransport an outbound bridge transport to fan scenes to a local adapter, or null for
     *     no bridge (the loader injects it only when the bridge is enabled and passed the loopback/remote
     *     check; brief 0003 §3.4).
     */
    public HapticRuntime(HapticProvider provider, Clock clock, Supplier<RuntimeConfig> config,
                         PackRegistry packs, BridgeTransport bridgeTransport) {
        this.provider = provider;
        this.clock = clock;
        this.config = config;
        this.recipe = new RecipeEngine(packs);
        // Central governance (ADR-018): the SceneGovernor holds and governs scenes; the neutral driver
        // advances it once per cycle and fans the governed set to every backend. Rendering backends
        // (Buttplug, and any future native integration) render it to their devices; semantic backends
        // (the bridge) forward it change-driven. Any number of each run concurrently.
        this.buttplug = new ButtplugBackend(provider, config);
        List<HapticBackend> backends = new ArrayList<>();
        backends.add(buttplug);
        if (bridgeTransport != null) {
            backends.add(new BridgeBackend(bridgeTransport, URI.create(config.get().bridgeUrl()), clock));
        }
        this.coordinator = new BackendCoordinator(backends);
        this.worker = new HapticWorker(sceneGovernor, coordinator, clock, config);
        this.lifecycle = new LifecycleController(worker, config);
        this.watchdog = new Watchdog(worker, clock, WATCHDOG_STALL_MS);
    }

    /** Record a discrete observation from the client thread (cheap, bounded). */
    public void recordEvent(RawGameEvent event) {
        tickBuffer.add(event);
    }

    /**
     * Called at the end of each client tick with the sampled state (brief §6.3). Drains discrete
     * events, computes transitions, aggregates intents, resolves scenes, and offers them to the
     * worker. Performs no blocking I/O.
     */
    public void onClientTickEnd(ClientStateSnapshot snapshot) {
        long now = clock.nanoTime();

        if (!snapshot.worldReady()) {
            if (worldPresent) {
                lifecycle.onWorldUnload();
            }
            worldPresent = false;
            gameActive = false;
            tracker.reset();
            aggregator.reset();
            recipe.resetAccumulation();
            recipe.resetStroke();
            tickBuffer.clear();
            return;
        }

        worldPresent = true;
        if (snapshot.paused()) {
            if (gameActive) {
                lifecycle.onPause();
                tracker.reset();
                aggregator.reset();
                recipe.resetAccumulation();
            recipe.resetStroke();
                tickBuffer.clear();
                gameActive = false;
            }
            return;
        }
        lifecycle.onResume();
        gameActive = true;
        // The client tick is an observer independent of the worker thread: if the worker has
        // stalled, fail toward stopped output before feeding it more scenes (brief §12.4).
        watchdog.check();

        List<RawGameEvent> discrete = tickBuffer.drain();
        StateTransitions transitions = tracker.update(snapshot);
        RuntimeConfig cfg = config.get();

        for (HapticIntent intent : aggregator.aggregate(discrete, transitions, snapshot, cfg, now)) {
            recipe.resolve(intent, cfg).ifPresent(scene -> sceneGovernor.submit(scene, now));
        }
        // Accumulation mode decays and refreshes even without new events.
        recipe.tickAccumulation(cfg, now).ifPresent(scene -> sceneGovernor.submit(scene, now));
        // Rhythmic stroking for position devices decays and refreshes the same way (Balanced only).
        recipe.tickStroke(cfg, now).ifPresent(scene -> sceneGovernor.submit(scene, now));
    }

    /**
     * Advance one cycle now and return the Buttplug commands it dispatched (used by the real loop and by
     * tests). The driver renders nothing itself; the commands come from the Buttplug rendering backend.
     */
    public List<net.minegasm.buttplug.OutputCommand> pump(long nowNs) {
        worker.cycle(nowNs);
        return buttplug.lastCommands();
    }

    public void start() {
        coordinator.start();
        worker.start();
    }

    public void shutdown() {
        worker.shutdown();
        coordinator.close();
    }

    public HapticWorker worker() {
        return worker;
    }

    public BackendCoordinator coordinator() {
        return coordinator;
    }

    public LifecycleController lifecycle() {
        return lifecycle;
    }

    public HapticProvider provider() {
        return provider;
    }

    public SceneGovernor sceneGovernor() {
        return sceneGovernor;
    }
}
