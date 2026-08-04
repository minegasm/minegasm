package net.minegasm.bridge;

import net.minegasm.backend.HapticBackend;
import net.minegasm.core.HapticScene;
import net.minegasm.runtime.StopReason;
import net.minegasm.time.Clock;

import java.net.URI;

/**
 * The local-bridge backend (brief 0002 §4.3, 0003 §3.4): fans each scene out as a versioned JSON
 * message to a user-run adapter over an outbound, loopback-by-default connection. The extension point
 * for integrations that do not justify code in the mod (XToys, DIY hardware, eventually OpenShock). The
 * socket lives behind {@link BridgeTransport} in the loader layer; this class is Java 8 and library-free.
 *
 * <p><b>v1 scope.</b> The coordinator fans out raw, pre-mix scenes: mixing, fatigue, and
 * {@code SafetyCaps} live in the Buttplug worker, not centrally (brief 0003 §3.3). So the bridge emits
 * ungoverned, unmixed scenes, fine for vibration-class adapters but nothing stronger; lifting the central
 * mixer/governor/body-budget up (§3.3, ADR-018) is a hard prerequisite before any electrostim adapter
 * rides this bridge (ADR-016 requires shock governed against the central body budget). Outbound frames go
 * through a bounded, one-in-flight {@link OutboundQueue} that drops oldest when full, so a burst cannot
 * grow memory and a stop cannot be overtaken. It does not yet coalesce continuous scenes (re-offered each
 * tick); the same §3.3 lift provides that for free.
 */
public final class BridgeBackend implements HapticBackend {

    /** Outbound frame bound: a burst beyond this drops oldest rather than growing memory. */
    private static final int QUEUE_CAPACITY = 64;

    private final BridgeTransport transport;
    private final BridgeCodec codec = new BridgeCodec();
    private final OutboundQueue outbound;
    private final URI endpoint;
    private final Clock clock;

    private volatile boolean outputEnabled = true;
    private volatile long lastHealthyCycleNs;

    public BridgeBackend(BridgeTransport transport, URI endpoint, Clock clock) {
        this.transport = transport;
        this.endpoint = endpoint;
        this.clock = clock;
        this.outbound = new OutboundQueue(QUEUE_CAPACITY, transport::send);
    }

    @Override
    public String id() {
        return "bridge";
    }

    @Override
    public void start() {
        transport.connect(endpoint, this::onMessage, this::onClose);
        lastHealthyCycleNs = clock.nanoTime();
    }

    @Override
    public void submit(HapticScene scene) {
        if (!outputEnabled || !transport.isOpen()) {
            return; // panic-latched or no adapter connected: drop, do not buffer stale output
        }
        outbound.offer(codec.encodeEffect(scene, clock.nanoTime()));
        lastHealthyCycleNs = clock.nanoTime();
    }

    @Override
    public void stop(StopReason reason) {
        // Send stop-all even while output is disabled. clearAndOffer drops every queued effect so none
        // can be delivered after the stop; a single already-in-flight effect completes first. Every
        // effect also carries a TTL, so a dropped connection self-clears regardless.
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
