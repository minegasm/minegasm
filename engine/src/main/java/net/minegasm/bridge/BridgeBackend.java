package net.minegasm.bridge;

import net.minegasm.backend.HapticBackend;
import net.minegasm.backend.BackendOperation;
import net.minegasm.backend.BackendOutcome;
import net.minegasm.backend.BackendOutcomeTracker;
import net.minegasm.core.HapticScene;
import net.minegasm.runtime.BridgeDestinationForwarder;
import net.minegasm.runtime.GovernedOutput;
import net.minegasm.runtime.ResolvedDestinationSnapshot;
import net.minegasm.runtime.SceneGovernor;
import net.minegasm.runtime.StopReason;
import net.minegasm.time.Clock;

import java.net.URI;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.function.Consumer;

/**
 * The local-bridge backend (brief 0002 §4.3, 0003 §3.4): forwards authoritative logical destination
 * snapshots as versioned JSON over an outbound, loopback-by-default connection. The extension point
 * for integrations that do not justify code in the mod (XToys, DIY hardware, eventually OpenShock). The
 * socket lives behind {@link BridgeTransport} in the loader layer; this class is Java 8 and library-free.
 *
 * <p><b>Governed input.</b> The bridge consumes the central governed set (ADR-018): scenes arrive already
 * coalesced, fatigue-attenuated, and exclusivity-resolved from {@link net.minegasm.runtime.SceneGovernor}.
 * {@link net.minegasm.runtime.BridgeDestinationForwarder} sends the central destination snapshot
 * and sends the whole snapshot only when it changes (or to refresh the TTL), so a steady effect is sent
 * once rather than every tick and, because each frame is the full state, a scene ending or being
 * suppressed retracts at the adapter when its destination disappears. The aggregate body budget will
 * attenuate the governed scene centrally before it reaches here, the prerequisite before any electrostim
 * adapter rides this bridge (ADR-016). Outbound frames go through a bounded, one-in-flight
 * {@link OutboundQueue} that drops oldest when full, so a burst cannot grow memory and a stop cannot be
 * overtaken; every frame also carries a TTL so a dropped connection self-clears.
 */
public final class BridgeBackend implements HapticBackend {

    /** Outbound frame bound: a burst beyond this drops oldest rather than growing memory. */
    private static final int QUEUE_CAPACITY = 64;

    /** How often the supervisor retries a dead connection, so the adapter can start or restart anytime. */
    private static final long RECONNECT_INTERVAL_MS = 2_000;

    /**
     * How long an adapter holds an output snapshot without a fresh one before zeroing. Longer than the
     * forwarder's re-send interval, so ordinary jitter never expires a steady effect, but a genuinely
     * dropped or half-open link self-clears within a few seconds.
     */
    private static final long OUTPUT_TTL_MS = 6_000;

    private final Supplier<BridgeTransport> transportFactory;
    private final BridgeCodec codec = new BridgeCodec();
    private final OutboundQueue outbound;
    private final BridgeDestinationForwarder forwarder;
    private final URI endpoint;
    private final String id;
    private final Clock clock;

    private volatile BridgeTransport transport;
    private volatile boolean connecting;
    private volatile boolean stopped;
    private volatile boolean outputEnabled = true;
    private volatile long lastHealthyCycleNs;
    private volatile boolean outputActive;
    // What the adapter reports about its own onward link (XToys webhook, device, ...). UNKNOWN until the
    // adapter says otherwise, and reset to UNKNOWN when the socket drops.
    private volatile DownstreamState downstream = DownstreamState.UNKNOWN;
    // Set when a fresh link comes up; the next worker cycle resets the forwarder so an in-flight
    // continuous effect resynchronizes to the new adapter now instead of waiting for its re-arm.
    private volatile boolean resyncOnNextCycle;
    // Bumped by any stop (panic, disable, removal) from any thread. A cycle captures it before forwarding
    // and submit() refuses to enqueue if it changed mid-cycle, so a stop can't be overtaken by an effect
    // the worker was already forwarding (review P1-3). The forwarder itself is only ever reset on the
    // worker thread (via resyncOnNextCycle), never cross-thread, so its maps are not raced.
    private final java.util.concurrent.atomic.AtomicLong stopGeneration =
            new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong pendingStopGeneration =
            new java.util.concurrent.atomic.AtomicLong();
    // Check-and-enqueue shares this boundary with stop generation and queue replacement. Without one
    // ordering point, an output could pass its generation check, pause, then append behind a stop.
    private final Object enqueueLock = new Object();
    private long cycleGeneration; // worker-thread only: the generation captured for the current cycle
    private ScheduledExecutorService reconnect;
    private final ScheduledExecutorService deliveryTimeouts;
    private final java.util.concurrent.atomic.AtomicLong operationGeneration =
            new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong wireGeneration =
            new java.util.concurrent.atomic.AtomicLong();
    private final BackendOutcomeTracker outcomes;

    public BridgeBackend(Supplier<BridgeTransport> transportFactory, URI endpoint, Clock clock) {
        this(transportFactory, endpoint, "bridge", clock);
    }

    /** @param id stable per-endpoint identifier, so several bridges can coexist (multi-endpoint). */
    public BridgeBackend(Supplier<BridgeTransport> transportFactory, URI endpoint, String id, Clock clock) {
        this.transportFactory = transportFactory;
        this.endpoint = endpoint;
        this.id = id == null || id.trim().isEmpty() ? "bridge" : id;
        this.clock = clock;
        this.outcomes = new BackendOutcomeTracker(this.id, clock::nanoTime, 5_000L);
        this.deliveryTimeouts = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "minegasm-bridge-timeout-" + this.id);
            thread.setDaemon(true);
            return thread;
        });
        this.outbound = new OutboundQueue(QUEUE_CAPACITY, this::sendFrame);
        this.forwarder = new BridgeDestinationForwarder(this::submit);
    }

    @Override
    public String id() {
        return id;
    }

    /**
     * Whether the outbound connection to the adapter is currently open. This is the mod-to-adapter link
     * only; it does not say whether the adapter's own connection onward (e.g. XToys) is up.
     */
    public boolean isConnected() {
        BridgeTransport current = transport;
        return current != null && current.isOpen();
    }

    /**
     * What the adapter last reported about its onward link (the next hop past the mod-to-adapter socket).
     * {@link DownstreamState#UNKNOWN} if the adapter hasn't said (an older adapter, or not yet connected).
     */
    public DownstreamState downstream() {
        return isConnected() ? downstream : DownstreamState.UNKNOWN;
    }

    @Override
    public synchronized void start() {
        if (!stopped && reconnect != null) {
            return;
        }
        stopped = false;
        ensureConnected(); // dial once now (so tests and a ready adapter connect immediately)
        // Then keep a supervisor dialing: retry until the adapter is up, reconnect if it restarts.
        reconnect = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "minegasm-bridge-reconnect-" + id);
            t.setDaemon(true);
            return t;
        });
        reconnect.scheduleAtFixedRate(this::ensureConnected, RECONNECT_INTERVAL_MS,
                RECONNECT_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    /** Dial a fresh transport when there is no open connection; a no-op while one is up or in flight. */
    private synchronized void ensureConnected() {
        if (stopped || connecting) {
            return;
        }
        BridgeTransport current = transport;
        if (current != null && current.isOpen()) {
            return;
        }
        connecting = true;
        if (current != null) {
            current.close(); // close the dead transport before replacing it, so it can't leak its writer
        }
        BridgeTransport fresh = transportFactory.get();
        transport = fresh;
        fresh.connect(endpoint, this::onMessage, this::onClose).whenComplete((v, error) -> {
            connecting = false;
            if (error == null) {
                lastHealthyCycleNs = clock.nanoTime();
                resyncOnNextCycle = true; // re-send in-flight effects to the newly connected adapter
                if (pendingStopGeneration.get() > 0L) {
                    outbound.clearAndOffer(codec.encodeStop());
                }
            }
        });
    }

    /** Send through the current transport, reporting timed-out or failed delivery on a live link. */
    private java.util.concurrent.CompletionStage<Void> sendFrame(String frame) {
        BridgeTransport current = transport;
        if (current == null || !current.isOpen()) {
            // Not connected: the frame cannot go out, but a disconnected bridge is an expected, visible
            // state (its row shows the link is down) and it drives nothing, so an undelivered frame here is
            // not a health fault. A stop is vacuously satisfied since there is no live link left running,
            // and it stays pending so the reconnect resend re-zeroes the adapter. Record no outcome and
            // complete normally, so an emergency stop against a down link never raises a false stop fault.
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }
        java.util.concurrent.CompletionStage<Void> stage = withTimeout(current.send(frame), current);
        final long operation = operationGeneration.incrementAndGet();
        final long lifecycle = stopGeneration.get();
        final BackendOperation kind = frame.contains("\"type\":\"stop\"")
                ? BackendOperation.STOP : frame.contains("\"purpose\":\"test\"")
                ? BackendOperation.TEST : BackendOperation.SEND;
        outcomes.observe(kind, operation, stage, () -> lifecycle != stopGeneration.get());
        if (kind == BackendOperation.SEND) {
            final boolean activeAfterDelivery = !frame.contains("\"destinations\":[]");
            stage.whenComplete((ignored, error) -> {
                if (error == null && lifecycle == stopGeneration.get()) {
                    outputActive = activeAfterDelivery;
                }
            });
        }
        if (kind == BackendOperation.STOP) {
            stage.whenComplete((ignored, error) -> {
                if (error == null) {
                    pendingStopGeneration.compareAndSet(lifecycle, 0L);
                }
            });
        }
        return stage;
    }

    private java.util.concurrent.CompletionStage<Void> withTimeout(
            java.util.concurrent.CompletionStage<Void> source, BridgeTransport owner) {
        final java.util.concurrent.CompletableFuture<Void> bounded =
                new java.util.concurrent.CompletableFuture<>();
        final java.util.concurrent.ScheduledFuture<?> timeout = deliveryTimeouts.schedule(() -> {
            if (bounded.completeExceptionally(
                    new java.util.concurrent.TimeoutException("bridge write timed out"))) {
                owner.close();
            }
        }, 5_000L, TimeUnit.MILLISECONDS);
        source.whenComplete((ignored, error) -> {
            timeout.cancel(false);
            if (error == null) {
                bounded.complete(null);
            } else {
                bounded.completeExceptionally(error);
            }
        });
        return bounded;
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
    public boolean isBodyDriving() {
        // A connected, output-enabled bridge can drive a physical device through its adapter, so it counts
        // toward fatigue even though it renders no level itself (review P1-6). Conservative: it counts
        // whenever the adapter link is up, without waiting for downstream device confirmation.
        return outputEnabled && isConnected();
    }

    @Override
    public boolean isOutputActive() {
        return outputActive;
    }

    @Override
    public void onGovernedOutput(GovernedOutput output) {
        // Change-driven: this backend's forwarder decides what actually goes on the wire (steady effects
        // sent once, TTL re-armed). It self-gates via submit() below when panicked or disconnected. All
        // forwarder state lives on this thread: reset it here (never cross-thread) when a link came up or a
        // stop asked for a resync, and capture the stop generation so a stop mid-cycle drops the rest.
        if (resyncOnNextCycle) {
            resyncOnNextCycle = false;
            forwarder.reset();
        }
        cycleGeneration = stopGeneration.get();
        forwarder.forward(output.destinations());
    }

    /**
     * The forwarder's sink: encode the authoritative destination snapshot as an {@code output} frame if the
     * adapter can receive it. Returns whether the frame was accepted, so the forwarder records it as sent
     * only when it actually went out and retries a drop on the next cycle rather than treating it as
     * delivered. Drops if a stop bumped the generation after this cycle began, so an output never lands
     * behind a stop.
     */
    private boolean submit(ResolvedDestinationSnapshot snapshot) {
        synchronized (enqueueLock) {
            BridgeTransport current = transport;
            if (!outputEnabled || current == null || !current.isOpen()
                    || cycleGeneration != stopGeneration.get() || pendingStopGeneration.get() > 0L) {
                return false; // panic, disconnect, or stop race: retry on a later governed cycle
            }
            outbound.offer(codec.encodeOutput(wireSnapshot(snapshot), OUTPUT_TTL_MS));
            lastHealthyCycleNs = clock.nanoTime();
            return true;
        }
    }

    @Override
    public void test(HapticScene scene, long nowNs) {
        // Render one scene to the same authoritative destination snapshot the steady path uses and send it with
        // the scene's remaining lifetime as its TTL, so the adapter holds it that long and then zeroes. The
        // steady forward path stays silent while idle (an all-off snapshot sends nothing), so it does not
        // overwrite the test. Isolated to this bridge: no other backend sees it.
        synchronized (enqueueLock) {
            BridgeTransport current = transport;
            String blocked = !outputEnabled ? "output is disabled"
                    : current == null || !current.isOpen() ? "bridge transport is not connected"
                    : pendingStopGeneration.get() > 0L ? "the previous stop is not confirmed"
                    : null;
            if (blocked != null) {
                reportTestFailure(blocked);
                return;
            }
            long ttlMs = Math.max(0L, (scene.expiresAtNs() - nowNs) / 1_000_000L);
            SceneGovernor isolated = new SceneGovernor(1);
            isolated.submit(scene, nowNs);
            outbound.offer(codec.encodeTestOutput(wireSnapshot(
                    isolated.resolve(nowNs, false, false).destinations()), ttlMs));
        }
    }

    private void reportTestFailure(String detail) {
        java.util.concurrent.CompletableFuture<Void> failed = new java.util.concurrent.CompletableFuture<>();
        failed.completeExceptionally(new IllegalStateException(detail));
        long generation = operationGeneration.incrementAndGet();
        outcomes.observe(BackendOperation.TEST, generation, failed, () -> false);
    }

    private ResolvedDestinationSnapshot wireSnapshot(ResolvedDestinationSnapshot snapshot) {
        return new ResolvedDestinationSnapshot(wireGeneration.incrementAndGet(),
                snapshot.sampledAtNs(), snapshot.levels());
    }

    @Override
    public void stop(StopReason reason) {
        // Bump the stop generation first so any cycle already forwarding drops the rest of its scenes and
        // cannot enqueue an effect behind this stop. Then clearAndOffer the stop, dropping every queued
        // effect. The forwarder is reset on the next cycle (worker thread), not here, so its maps are never
        // touched cross-thread. Every effect also carries a TTL, so a dropped connection self-clears.
        synchronized (enqueueLock) {
            long generation = stopGeneration.incrementAndGet();
            outputActive = false;
            pendingStopGeneration.set(generation);
            resyncOnNextCycle = true;
            interruptInFlightWrite();
            outbound.clearAndOffer(codec.encodeStop());
        }
    }

    @Override
    public void emergencyStop(StopReason reason) {
        // Out-of-band (watchdog) path: only thread-safe actions. Same generation bump so a concurrent cycle
        // can't append an effect behind the stop, and clearAndOffer through the synchronized queue tells the
        // adapter to zero now. The forwarder reset is deferred to the worker thread via resyncOnNextCycle.
        synchronized (enqueueLock) {
            long generation = stopGeneration.incrementAndGet();
            outputActive = false;
            pendingStopGeneration.set(generation);
            resyncOnNextCycle = true;
            interruptInFlightWrite();
            outbound.clearAndOffer(codec.encodeStop());
        }
    }

    private void interruptInFlightWrite() {
        if (!outbound.hasInFlight()) {
            return;
        }
        BridgeTransport current = transport;
        if (current != null) {
            current.close();
        }
    }

    @Override
    public void pause() {
        stop(StopReason.PAUSE); // the bridge holds no resumable state; stopping is the safe pause
    }

    @Override
    public void resume() {
        // Nothing to restore: scenes will be re-submitted by the running pipeline.
    }

    @Override
    public void discardPause() {
        // No preserved state to drop.
    }

    @Override
    public void setOutputEnabled(boolean enabled) {
        boolean was = this.outputEnabled;
        this.outputEnabled = enabled;
        if (was && !enabled) {
            // Disabling output (panic/master off) must actively stop the adapter, not just go silent.
            // Buttplug's next cycle pushes zeros to its devices; the bridge has to send an explicit stop
            // or the toy would coast until the frame's TTL lapses. stop() also resets the forwarder, so
            // re-enabling re-sends the next changed scene afresh.
            stop(StopReason.PANIC);
        }
    }

    @Override
    public long lastHealthyCycleNs() {
        return lastHealthyCycleNs;
    }

    @Override
    public void close() {
        stopped = true;
        if (reconnect != null) {
            reconnect.shutdownNow();
        }
        outbound.close();
        deliveryTimeouts.shutdownNow();
        outcomes.close();
        BridgeTransport current = transport;
        if (current != null) {
            current.close();
        }
    }

    private void onMessage(String frame) {
        // Receiving anything shows the adapter is alive. A hello/status frame also tells us the adapter's
        // onward link state (downstream), which the UI surfaces as a distinct step in the chain.
        lastHealthyCycleNs = clock.nanoTime();
        DownstreamState reported = codec.decodeDownstream(frame);
        if (reported != null) {
            downstream = reported;
        }
    }

    private void onClose(Throwable cause) {
        // The transport reports closed via isOpen(); the reconnect supervisor dials a fresh one on its
        // next tick, so a dropped or restarted adapter reconnects without a game restart. We no longer
        // know the downstream state once the socket is gone.
        downstream = DownstreamState.UNKNOWN;
        outputActive = false;
    }
}
