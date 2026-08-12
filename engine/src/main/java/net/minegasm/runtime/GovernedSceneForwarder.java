package net.minegasm.runtime;

import net.minegasm.core.HapticLayer;
import net.minegasm.core.HapticScene;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Forwards the central governed scene set to a semantic sink (the local bridge) change-driven, so a
 * steady effect is sent once instead of on every worker cycle (ADR-018). The worker calls
 * {@link #forward} with the return of {@link SceneGovernor#govern} each cycle; because the governor holds
 * the same authored continuous scene across the ~3 worker cycles between client ticks, the change check
 * naturally caps outgoing frames near the submit rate without a separate rate limiter.
 *
 * <p>Change detection keys on amplitude with a small epsilon plus a structural signature, not scene
 * equality: a governed scene's level drifts slightly as fatigue decays, so exact equality would re-send
 * forever, but a change of role, primitive type, timing, coupling, priority, or layer membership at the
 * same peak still needs to reach the adapter. A continuous scene is re-sent when its amplitude moves past
 * the epsilon, its signature changes, or half its TTL has elapsed (which refreshes the adapter's TTL so a
 * long steady effect never lapses). A discrete scene is sent once per instance (keyed on id and creation
 * time, since ids can repeat across events).
 *
 * <p>The sink reports whether the frame was actually accepted for delivery. Forwarding state is recorded
 * only on a successful send, so a frame dropped while the adapter is disconnected or output is latched off
 * is not mistaken for delivered: it is retried on the next cycle once the link returns. The backend also
 * calls {@link #reset} on a fresh connection so an in-flight continuous effect resynchronizes immediately
 * rather than waiting for its next re-arm.
 *
 * <p>Confined to the worker thread, alongside {@link SceneGovernor#govern}; {@link #reset} is called
 * under the worker monitor on stop so the next scene is never wrongly suppressed after a stop frame.
 */
public final class GovernedSceneForwarder {

    /** Amplitude change below this is treated as steady (absorbs per-cycle fatigue drift). */
    private static final float LEVEL_EPSILON = 0.02f;

    /** Returns true when the frame was accepted for delivery, false when it was dropped (link down). */
    private final Predicate<HapticScene> sink;
    private final Map<String, Forwarded> continuous = new HashMap<>();
    private final Set<String> discreteSent = new HashSet<>();

    public GovernedSceneForwarder(Predicate<HapticScene> sink) {
        this.sink = sink;
    }

    /** Forward any scene whose content changed or whose TTL needs refreshing; suppress steady re-sends. */
    public void forward(List<HapticScene> governed, long nowNs) {
        Set<String> seenContinuous = new HashSet<>();
        Set<String> seenDiscrete = new HashSet<>();
        for (HapticScene scene : governed) {
            if (scene.isContinuous()) {
                String key = scene.continuousKey();
                seenContinuous.add(key);
                float amplitude = amplitude(scene);
                String signature = signature(scene);
                Forwarded prev = continuous.get(key);
                boolean send = prev == null
                        || !signature.equals(prev.signature)
                        || Math.abs(amplitude - prev.amplitude) > LEVEL_EPSILON
                        || nowNs >= prev.rearmAtNs;
                // Record only on a successful send: a drop must be retried next cycle, not remembered.
                if (send && sink.test(scene)) {
                    continuous.put(key, new Forwarded(amplitude, signature,
                            nowNs + scene.remainingNs(nowNs) / 2));
                }
            } else {
                String instance = scene.sceneId() + "@" + scene.createdAtNs();
                seenDiscrete.add(instance);
                if (!discreteSent.contains(instance) && sink.test(scene)) {
                    discreteSent.add(instance);
                }
            }
        }
        // Forget scenes no longer present so a later re-appearance forwards afresh and state stays bounded.
        continuous.keySet().retainAll(seenContinuous);
        discreteSent.retainAll(seenDiscrete);
    }

    /** Forget all forwarding state (called on stop and on a fresh connection, under the worker monitor). */
    public void reset() {
        continuous.clear();
        discreteSent.clear();
    }

    private static float amplitude(HapticScene scene) {
        float max = 0f;
        for (HapticLayer layer : scene.layers()) {
            max = Math.max(max, layer.primitive().level());
        }
        return max;
    }

    /**
     * A cheap fingerprint of everything wire-relevant except the fatigue-attenuated amplitude: layer
     * membership, role, coupling, priority, primitive kind, and nominal duration. Amplitude is compared
     * separately with an epsilon so per-cycle fatigue drift does not force a re-send, but a real change of
     * shape or routing at the same peak does. (Non-level shape params like beat timing are not decomposed
     * here; the primitive kind and duration catch the common cases.)
     */
    private static String signature(HapticScene scene) {
        StringBuilder sb = new StringBuilder();
        sb.append(scene.priority()).append('|');
        for (HapticLayer layer : scene.layers()) {
            sb.append(layer.layerId()).append(':')
                    .append(layer.role()).append(':')
                    .append(layer.coupling()).append(':')
                    .append(layer.priority()).append(':')
                    .append(layer.primitive().getClass().getSimpleName()).append(':')
                    .append(layer.primitive().durationMs()).append(';');
        }
        return sb.toString();
    }

    private static final class Forwarded {
        final float amplitude;
        final String signature;
        final long rearmAtNs;

        Forwarded(float amplitude, String signature, long rearmAtNs) {
            this.amplitude = amplitude;
            this.signature = signature;
            this.rearmAtNs = rearmAtNs;
        }
    }
}
