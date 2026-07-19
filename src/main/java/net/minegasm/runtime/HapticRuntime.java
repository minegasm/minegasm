package net.minegasm.runtime;

import net.minegasm.buttplug.HapticProvider;
import net.minegasm.config.RuntimeConfig;
import net.minegasm.core.RawGameEvent;
import net.minegasm.observe.ClientStateSnapshot;
import net.minegasm.observe.HapticAggregator;
import net.minegasm.observe.StateTracker;
import net.minegasm.observe.StateTransitions;
import net.minegasm.observe.TickEventBuffer;
import net.minegasm.recipe.RecipeEngine;
import net.minegasm.time.Clock;

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
    private final RecipeEngine recipe = new RecipeEngine();
    private final SceneIngressQueue ingress = new SceneIngressQueue();
    private final HapticProvider provider;
    private final HapticWorker worker;
    private final LifecycleController lifecycle;
    private final Watchdog watchdog;
    private final Clock clock;
    private final Supplier<RuntimeConfig> config;

    private boolean gameActive;
    private boolean worldPresent;

    public HapticRuntime(HapticProvider provider, Clock clock, Supplier<RuntimeConfig> config) {
        this.provider = provider;
        this.clock = clock;
        this.config = config;
        this.worker = new HapticWorker(ingress, provider, clock, config);
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

        for (var intent : aggregator.aggregate(discrete, transitions, snapshot, cfg, now)) {
            recipe.resolve(intent, cfg).ifPresent(scene -> ingress.offer(scene, now));
        }
        // Accumulation mode decays and refreshes even without new events.
        recipe.tickAccumulation(cfg, now).ifPresent(scene -> ingress.offer(scene, now));
    }

    /** Run a single worker cycle now (used by the real loop and by tests). */
    public List<net.minegasm.buttplug.OutputCommand> pump(long nowNs) {
        return worker.cycle(nowNs);
    }

    public void start() {
        worker.start();
    }

    public void shutdown() {
        worker.shutdown();
    }

    public HapticWorker worker() {
        return worker;
    }

    public LifecycleController lifecycle() {
        return lifecycle;
    }

    public HapticProvider provider() {
        return provider;
    }

    public SceneIngressQueue ingress() {
        return ingress;
    }
}
