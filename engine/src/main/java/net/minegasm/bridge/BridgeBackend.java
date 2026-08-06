package net.minegasm.bridge;

import net.minegasm.backend.HapticBackend;
import net.minegasm.core.HapticScene;
import net.minegasm.runtime.GovernedSceneForwarder;
import net.minegasm.runtime.StopReason;
import net.minegasm.time.Clock;

import java.net.URI;
import java.util.List;

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

    private final BridgeTransport transport;
    private final BridgeCodec codec = new BridgeCodec();
    private final OutboundQueue outbound;
    private final GovernedSceneForwarder forwarder;
    private final URI endpoint;
    private final String id;
    private final Clock clock;

    private volatile boolean outputEnabled = true;
    private volatile long lastHealthyCycleNs;

    public BridgeBackend(BridgeTransport transport, URI endpoint, Clock clock) {
        this(transport, endpoint, "bridge", clock);
    }

    /** @param id stable per-endpoint identifier, so several bridges can coexist (multi-endpoint). */
    public BridgeBackend(BridgeTransport transport, URI endpoint, String id, Clock clock) {
        this.transport = transport;
        this.endpoint = endpoint;
        this.id = id == null || id.trim().isEmpty() ? "bridge" : id;
        this.clock = clock;
        this.outbound = new OutboundQueue(QUEUE_CAPACITY, transport::send);
        this.forwarder = new GovernedSceneForwarder(this::submit);
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public void start() {
        transport.connect(endpoint, this::onMessage, this::onClose);
        lastHealthyCycleNs = clock.nanoTime();
    }

    @Override
    public void onGovernedScenes(List<HapticScene> governed, long nowNs) {
        // Change-driven: this backend's forwarder decides what actually goes on the wire (steady effects
        // sent once, TTL re-armed). It self-gates via submit() below when panicked or disconnected.
        forwarder.forward(governed, nowNs);
    }

    /** The forwarder's sink: encode one governed scene as an effect frame if the adapter can receive it. */
    private void submit(HapticScene scene) {
        if (!outputEnabled || !transport.isOpen()) {
            return; // panic-latched or no adapter connected: drop, do not buffer stale output
        }
        outbound.offer(codec.encodeEffect(scene, clock.nanoTime()));
        lastHealthyCycleNs = clock.nanoTime();
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
        this.outputEnabled = enabled;
    }

    @Override
    public long lastHealthyCycleNs() {
        return lastHealthyCycleNs;
    }

    @Override
    public void close() {
        outbound.close();
        transport.close();
    }

    private void onMessage(String frame) {
        // v1 does not act on adapter messages (acks/health are a later addition); receiving one still
        // shows the adapter is alive.
        lastHealthyCycleNs = clock.nanoTime();
    }

    private void onClose(Throwable cause) {
        // The transport reports closed via isOpen(); nothing to clear here. submit() drops while closed.
    }
}
