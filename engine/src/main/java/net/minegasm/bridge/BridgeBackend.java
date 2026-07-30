package net.minegasm.bridge;

import net.minegasm.backend.HapticBackend;
import net.minegasm.core.HapticScene;
import net.minegasm.runtime.StopReason;
import net.minegasm.time.Clock;

import java.net.URI;

/**
 * The local-bridge backend (brief 0002 §4.3, 0003 §3.4): fans each scene out as a versioned JSON
 * message to a user-run adapter over an outbound, loopback-by-default connection. It is the extension
 * point for integrations that do not justify code in the mod (XToys, DIY hardware, and eventually
 * OpenShock). The concrete socket lives behind {@link BridgeTransport} in the loader layer; this class
 * is Java 8 and library-free.
 *
 * <p><b>Governance scope (v1).</b> The coordinator fans out raw, pre-mix scenes: mixing, fatigue, and
 * {@code SafetyCaps} currently live inside the Buttplug worker, not centrally (brief 0003 §3.3). So the
 * bridge emits ungoverned, unmixed scenes, which is acceptable for vibration-class adapters but nothing
 * stronger. Lifting the central mixer/governor/body-budget up to the coordinator (§3.3) is a hard
 * prerequisite before any electrostim adapter rides this bridge: ADR-016 requires shock to be governed
 * against the central body budget, so a coarse ungoverned v1 must never become the shock path.
 *
 * <p><b>No backpressure yet (v1).</b> {@link #submit} serializes and sends inline with no bounded queue
 * and no coalescing. Because raw pre-mix scenes are fanned out, continuous scenes (mining texture,
 * accumulation, stroke) are re-offered every client tick, so once a real transport is attached the
 * bridge would emit a near-identical full-scene frame per continuous scene per tick. The Buttplug path
 * avoids this because {@code SceneMixer} coalesces by {@code continuousKey} and {@code FeatureScheduler}
 * deadbands; the bridge bypasses both. Per-{@code continuousKey} coalescing and a bounded drop-oldest
 * queue must land together with the real transport (brief 0002 §4.3, "queues are bounded and stale
 * messages are dropped").
 */
public final class BridgeBackend implements HapticBackend {

    private final BridgeTransport transport;
    private final BridgeCodec codec = new BridgeCodec();
    private final URI endpoint;
    private final Clock clock;

    private volatile boolean outputEnabled = true;
    private volatile long lastHealthyCycleNs;

    public BridgeBackend(BridgeTransport transport, URI endpoint, Clock clock) {
        this.transport = transport;
        this.endpoint = endpoint;
        this.clock = clock;
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
        transport.send(codec.encodeEffect(scene, clock.nanoTime()));
        lastHealthyCycleNs = clock.nanoTime();
    }

    @Override
    public void stop(StopReason reason) {
        // Send stop-all even while output is disabled; a no-op if the transport is closed. The effect
        // takes hold synchronously (serialize + hand to the non-blocking transport), and every effect
        // also carries a TTL so a dropped connection self-clears regardless.
        transport.send(codec.encodeStop());
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
