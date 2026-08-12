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
    // Set when a fresh link comes up; the next worker cycle resets the forwarder so an in-flight
    // continuous effect resynchronizes to the new adapter now instead of waiting for its re-arm.
    private volatile boolean resyncOnNextCycle;
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
    public void onGovernedScenes(List<HapticScene> governed, long nowNs) {
        // Change-driven: this backend's forwarder decides what actually goes on the wire (steady effects
        // sent once, TTL re-armed). It self-gates via submit() below when panicked or disconnected.
        if (resyncOnNextCycle) {
            resyncOnNextCycle = false;
            forwarder.reset(); // a link just came up: forget prior state so live effects re-send now
        }
        forwarder.forward(governed, nowNs);
    }

    /**
     * The forwarder's sink: encode one governed scene as an effect frame if the adapter can receive it.
     * Returns whether the frame was accepted, so the forwarder records it as sent only when it actually
     * went out and retries a drop on the next cycle rather than treating it as delivered.
     */
    private boolean submit(HapticScene scene) {
        BridgeTransport current = transport;
        if (!outputEnabled || current == null || !current.isOpen()) {
            return false; // panic-latched or no adapter connected: drop, retried when the link returns
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
        // Forget forwarding state first so the stop frame is never suppressed and the next real scene is
        // sent afresh. Then send stop-all even while output is disabled: clearAndOffer drops every queued
        // effect so none can be delivered after the stop; a single already-in-flight effect completes
        // first. Every effect also carries a TTL, so a dropped connection self-clears regardless.
        forwarder.reset();
        outbound.clearAndOffer(codec.encodeStop());
    }

    @Override
    public void emergencyStop(StopReason reason) {
        // Out-of-band (watchdog) path: only thread-safe actions, no forwarder reset, since a cycle may be
        // forwarding on the driver thread. The volatile latch makes submit() drop; clearAndOffer goes
        // through the synchronized queue and replaces any queued effect with a stop.
        outputEnabled = false;
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
        // v1 does not act on adapter messages (acks/health are a later addition); receiving one still
        // shows the adapter is alive.
        lastHealthyCycleNs = clock.nanoTime();
    }

    private void onClose(Throwable cause) {
        // The transport reports closed via isOpen(); the reconnect supervisor dials a fresh one on its
        // next tick, so a dropped or restarted adapter reconnects without a game restart.
    }
}
