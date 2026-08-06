package net.minegasm.runtime;

import net.minegasm.core.HapticLayer;
import net.minegasm.core.HapticScene;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Forwards the central governed scene set to a semantic sink (the local bridge) change-driven, so a
 * steady effect is sent once instead of on every worker cycle (ADR-018). The worker calls
 * {@link #forward} with the return of {@link SceneGovernor#govern} each cycle; because the governor holds
 * the same authored continuous scene across the ~3 worker cycles between client ticks, the change check
 * naturally caps outgoing frames near the submit rate without a separate rate limiter.
 *
 * <p>Change detection keys on amplitude with a small epsilon, not scene equality: a governed scene's
 * level drifts slightly as fatigue decays, so exact equality would re-send forever. A continuous scene is
 * re-sent when its amplitude moves past the epsilon or when half its TTL has elapsed, which refreshes the
 * adapter's TTL so a long steady effect never lapses. A discrete scene is sent once per instance
 * (keyed on id and creation time, since ids can repeat across events).
 *
 * <p>Confined to the worker thread, alongside {@link SceneGovernor#govern}; {@link #reset} is called
 * under the worker monitor on stop so the next scene is never wrongly suppressed after a stop frame.
 */
public final class GovernedSceneForwarder {

    /** Amplitude change below this is treated as steady (absorbs per-cycle fatigue drift). */
    private static final float LEVEL_EPSILON = 0.02f;

    private final Consumer<HapticScene> sink;
    private final Map<String, Forwarded> continuous = new HashMap<>();
    private final Set<String> discreteSent = new HashSet<>();

    public GovernedSceneForwarder(Consumer<HapticScene> sink) {
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
                Forwarded prev = continuous.get(key);
                boolean send = prev == null
                        || Math.abs(amplitude - prev.amplitude) > LEVEL_EPSILON
                        || nowNs >= prev.rearmAtNs;
                if (send) {
                    sink.accept(scene);
                    continuous.put(key, new Forwarded(amplitude, nowNs + scene.remainingNs(nowNs) / 2));
                }
            } else {
                String instance = scene.sceneId() + "@" + scene.createdAtNs();
                seenDiscrete.add(instance);
                if (discreteSent.add(instance)) {
                    sink.accept(scene);
                }
            }
        }
        // Forget scenes no longer present so a later re-appearance forwards afresh and state stays bounded.
        continuous.keySet().retainAll(seenContinuous);
        discreteSent.retainAll(seenDiscrete);
    }

    /** Forget all forwarding state (called on stop, under the worker monitor). */
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

    private static final class Forwarded {
        final float amplitude;
        final long rearmAtNs;

        Forwarded(float amplitude, long rearmAtNs) {
            this.amplitude = amplitude;
            this.rearmAtNs = rearmAtNs;
        }
    }
}
