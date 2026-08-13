package net.minegasm.runtime;

import net.minegasm.core.CouplingMode;
import net.minegasm.core.HapticLayer;
import net.minegasm.core.HapticPrimitive;
import net.minegasm.core.HapticRole;
import net.minegasm.core.HapticScene;

import java.util.ArrayList;
import java.util.EnumMap;
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
        governed = resolveExclusivity(governed);
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

    /**
     * Make the bridge honor priority and exclusivity per role, which the raw per-role-maximum path
     * ignores (review follow-up P1-6). A bridge output is per role, so the resolution is per role: within
     * a role, a higher-priority exclusive layer suppresses every strictly lower-priority layer. Cross-role
     * and per-physical-feature resolution stays device-specific in the Buttplug mixer, so this does not
     * change the hardware-validated native path. (Full backend-neutral resolution keyed on a logical body
     * region is a larger model, still to come.)
     */
    private static List<HapticScene> resolveExclusivity(List<HapticScene> governed) {
        EnumMap<HapticRole, Integer> exclusiveFloor = new EnumMap<>(HapticRole.class);
        for (HapticScene scene : governed) {
            for (HapticLayer layer : scene.layers()) {
                if (layer.coupling() == CouplingMode.EXCLUSIVE) {
                    exclusiveFloor.merge(layer.role(), layer.priority(), Math::max);
                }
            }
        }
        if (exclusiveFloor.isEmpty()) {
            return governed;
        }
        List<HapticScene> out = new ArrayList<>(governed.size());
        for (HapticScene scene : governed) {
            List<HapticLayer> kept = new ArrayList<>(scene.layers().size());
            for (HapticLayer layer : scene.layers()) {
                Integer floor = exclusiveFloor.get(layer.role());
                if (floor == null || layer.priority() >= floor) {
                    kept.add(layer);
                }
            }
            if (kept.size() == scene.layers().size()) {
                out.add(scene);
            } else if (!kept.isEmpty()) {
                out.add(scene.withLayers(kept));
            } // else: every layer suppressed, so the scene contributes nothing and is dropped
        }
        return out;
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
     * A fingerprint of everything wire-relevant except the fatigue-attenuated amplitude: layer membership,
     * role, coupling, priority, and every non-level shape parameter of the primitive (beat timing,
     * oscillation period, sweep easing, texture grain, and so on). Amplitude is compared separately with an
     * epsilon so per-cycle fatigue drift does not force a re-send, but a change of shape or routing at the
     * same peak does (review follow-up P2-1). The attenuated level fields are the only ones excluded.
     */
    private static String signature(HapticScene scene) {
        StringBuilder sb = new StringBuilder();
        sb.append(scene.priority()).append('|');
        for (HapticLayer layer : scene.layers()) {
            sb.append(layer.layerId()).append(':')
                    .append(layer.role()).append(':')
                    .append(layer.coupling()).append(':')
                    .append(layer.priority()).append(':');
            appendPrimitiveShape(sb, layer.primitive());
            sb.append(';');
        }
        return sb.toString();
    }

    /** Append a primitive's kind and its non-level shape parameters (the level is excluded on purpose). */
    private static void appendPrimitiveShape(StringBuilder sb, HapticPrimitive p) {
        sb.append(p.getClass().getSimpleName()).append('(').append(p.durationMs());
        if (p instanceof HapticPrimitive.Impulse) {
            HapticPrimitive.Impulse i = (HapticPrimitive.Impulse) p;
            sb.append(',').append(i.attackMs()).append(',').append(i.releaseMs());
        } else if (p instanceof HapticPrimitive.Texture) {
            HapticPrimitive.Texture t = (HapticPrimitive.Texture) p;
            sb.append(',').append(t.grain()).append(',').append(t.density())
                    .append(',').append(t.irregularity());
        } else if (p instanceof HapticPrimitive.Rumble) {
            HapticPrimitive.Rumble r = (HapticPrimitive.Rumble) p;
            sb.append(',').append(r.roughness()).append(',').append(r.decay());
        } else if (p instanceof HapticPrimitive.Sweep) {
            // from/to are amplitudes (attenuated), so only the shape (duration + easing) is fingerprinted.
            sb.append(',').append(((HapticPrimitive.Sweep) p).easing());
        } else if (p instanceof HapticPrimitive.Hold) {
            HapticPrimitive.Hold h = (HapticPrimitive.Hold) p;
            sb.append(',').append(h.fadeInMs()).append(',').append(h.fadeOutMs());
        } else if (p instanceof HapticPrimitive.Oscillation) {
            sb.append(',').append(((HapticPrimitive.Oscillation) p).periodMs());
        } else if (p instanceof HapticPrimitive.BeatPattern) {
            for (HapticPrimitive.Beat beat : ((HapticPrimitive.BeatPattern) p).beats()) {
                sb.append(",@").append(beat.atMs()).append('/').append(beat.durationMs());
            }
        }
        sb.append(')');
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
