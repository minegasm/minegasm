package net.minegasm.bridge;

import net.minegasm.backend.HapticBackend;
import net.minegasm.core.HapticScene;
import net.minegasm.runtime.GovernedSceneForwarder;
import net.minegasm.runtime.StopReason;
import net.minegasm.time.Clock;

import java.net.URI;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * The local-bridge backend (brief 0002 §4.3, 0003 §3.4): fans each scene out as a versioned JSON
 * message to a user-run adapter over an outbound, loopback-by-default connection. The extension point
 * for integrations that do not justify code in the mod (XToys, DIY hardware, eventually OpenShock). The
 * socket lives behind {@link BridgeTransport} in the loader layer; this class is Java 8 and library-free.
 *
 * <p><b>Governed input.</b> The bridge now consumes the central governed set (ADR-018): scenes arrive
 * already coalesced and fatigue-attenuated from {@link net.minegasm.runtime.SceneGovernor} via
 * {@link net.minegasm.runtime.GovernedSceneForwarder}, which forwards a scene only when its content
 * changes or its TTL needs refreshing, so a steady continuous effect is sent once rather than every tick.
 * Scene-level {@code SafetyCaps} still apply per backend at render for Buttplug; the aggregate body
 * budget (Phase 6) will attenuate the governed scene centrally before it reaches here, the prerequisite
 * before any electrostim adapter rides this bridge (ADR-016). Outbound frames go through a bounded,
 * one-in-flight {@link OutboundQueue} that drops oldest when full, so a burst cannot grow memory and a
 * stop cannot be overtaken; every frame also carries a TTL so a dropped connection self-clears.
 */
public final class BridgeBackend implements HapticBackend {

    /** Outbound frame bound: a burst beyond this drops oldest rather than growing memory. */
    private static final int QUEUE_CAPACITY = 64;

    /** How often the supervisor retries a dead connection, so the adapter can start or restart anytime. */
    private static final long RECONNECT_INTERVAL_MS = 2_000;

    private final Supplier<BridgeTransport> transportFactory;
    private final BridgeCodec codec = new BridgeCodec();
    private final OutboundQueue outbound;
    private final GovernedSceneForwarder forwarder;
    private final URI endpoint;
    private final String id;
    private final Clock clock;

    private volatile BridgeTransport transport;
    private volatile boolean connecting;
    private volatile boolean stopped;
    private volatile boolean outputEnabled = true;
    private volatile long lastHealthyCycleNs;
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
    private long cycleGeneration; // worker-thread only: the generation captured for the current cycle
    private ScheduledExecutorService reconnect;

    public BridgeBackend(Supplier<BridgeTransport> transportFactory, URI endpoint, Clock clock) {
        this(transportFactory, endpoint, "bridge", clock);
    }

    /** @param id stable per-endpoint identifier, so several bridges can coexist (multi-endpoint). */
    public BridgeBackend(Supplier<BridgeTransport> transportFactory, URI endpoint, String id, Clock clock) {
        this.transportFactory = transportFactory;
        this.endpoint = endpoint;
        this.id = id == null || id.trim().isEmpty() ? "bridge" : id;
        this.clock = clock;
        this.outbound = new OutboundQueue(QUEUE_CAPACITY, this::sendFrame);
        this.forwarder = new GovernedSceneForwarder(this::submit);
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
    public void start() {
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
        BridgeTransport fresh = transportFactory.get();
        transport = fresh;
        fresh.connect(endpoint, this::onMessage, this::onClose).whenComplete((v, error) -> {
            connecting = false;
            if (error == null) {
                lastHealthyCycleNs = clock.nanoTime();
                resyncOnNextCycle = true; // re-send in-flight effects to the newly connected adapter
            }
        });
    }

    /** Send a frame through whatever transport is currently connected; dropped if there is none. */
    private java.util.concurrent.CompletionStage<Void> sendFrame(String frame) {
        BridgeTransport current = transport;
        if (current != null) {
            return current.send(frame);
        }
        return java.util.concurrent.CompletableFuture.completedFuture(null);
    }

    @Override
    public boolean isBodyDriving() {
        // A connected, output-enabled bridge can drive a physical device through its adapter, so it counts
        // toward fatigue even though it renders no level itself (review P1-6). Conservative: it counts
        // whenever the adapter link is up, without waiting for downstream device confirmation.
        return outputEnabled && isConnected();
    }

    @Override
    public void onGovernedScenes(List<HapticScene> governed, long nowNs) {
        // Change-driven: this backend's forwarder decides what actually goes on the wire (steady effects
        // sent once, TTL re-armed). It self-gates via submit() below when panicked or disconnected. All
        // forwarder state lives on this thread: reset it here (never cross-thread) when a link came up or a
        // stop asked for a resync, and capture the stop generation so a stop mid-cycle drops the rest.
        if (resyncOnNextCycle) {
            resyncOnNextCycle = false;
            forwarder.reset();
        }
        cycleGeneration = stopGeneration.get();
        forwarder.forward(governed, nowNs);
    }

    /**
     * The forwarder's sink: encode one governed scene as an effect frame if the adapter can receive it.
     * Returns whether the frame was accepted, so the forwarder records it as sent only when it actually
     * went out and retries a drop on the next cycle rather than treating it as delivered. Drops if a stop
     * bumped the generation after this cycle began, so an effect never lands behind a stop.
     */
    private boolean submit(HapticScene scene) {
        BridgeTransport current = transport;
        if (!outputEnabled || current == null || !current.isOpen()
                || cycleGeneration != stopGeneration.get()) {
            return false; // panic-latched, disconnected, or a stop raced this cycle: drop and retry later
        }
        outbound.offer(codec.encodeEffect(scene, clock.nanoTime()));
        lastHealthyCycleNs = clock.nanoTime();
        return true;
    }

    @Override
    public void test(HapticScene scene, long nowNs) {
        // Send one effect frame straight to this bridge's adapter. It carries a TTL, so the adapter holds
        // it for the scene's lifetime and releases it; the change-driven forward path stays quiet meanwhile,
        // so nothing overwrites it. Isolated to this bridge: no other backend sees it.
        BridgeTransport current = transport;
        if (outputEnabled && current != null && current.isOpen()) {
            outbound.offer(codec.encodeEffect(scene, nowNs));
        }
    }

    @Override
    public void stop(StopReason reason) {
        // Bump the stop generation first so any cycle already forwarding drops the rest of its scenes and
        // cannot enqueue an effect behind this stop. Then clearAndOffer the stop, dropping every queued
        // effect. The forwarder is reset on the next cycle (worker thread), not here, so its maps are never
        // touched cross-thread. Every effect also carries a TTL, so a dropped connection self-clears.
        stopGeneration.incrementAndGet();
        resyncOnNextCycle = true;
        outbound.clearAndOffer(codec.encodeStop());
    }

    @Override
    public void emergencyStop(StopReason reason) {
        // Out-of-band (watchdog) path: only thread-safe actions. Same generation bump so a concurrent cycle
        // can't append an effect behind the stop, and clearAndOffer through the synchronized queue tells the
        // adapter to zero now. The forwarder reset is deferred to the worker thread via resyncOnNextCycle.
        stopGeneration.incrementAndGet();
        resyncOnNextCycle = true;
        outbound.clearAndOffer(codec.encodeStop());
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
    }
}
