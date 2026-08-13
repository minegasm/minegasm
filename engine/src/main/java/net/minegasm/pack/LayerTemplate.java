package net.minegasm.pack;

import net.minegasm.core.BodyRegion;
import net.minegasm.core.CouplingMode;
import net.minegasm.core.DeliveryMode;
import net.minegasm.core.HapticLayer;
import net.minegasm.core.HapticPrimitive;
import net.minegasm.core.HapticRole;
import net.minegasm.core.HapticRoute;
import net.minegasm.core.OutputKind;
import net.minegasm.util.HapticMath;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Authoring form of one {@link HapticLayer} (brief 0003 §2.3): a role, one primitive, a
 * capability-only route (allowed output kinds plus delivery, never device- or feature-specific so it
 * stays shareable), coupling, priority, and timing in milliseconds relative to the scene.
 *
 * <p>{@code strengthWeight} in {@code [0, 1]} is the Tier 2 (brief §2.4) strength response: how much
 * the layer's amplitude follows the triggering event's strength. 0 is a static Tier 1 layer (always
 * full); 1 makes the layer fully proportional to strength; between, weak events are attenuated toward
 * {@code 1 - weight}. The authored primitive level is the full-strength reference.
 */
public final class LayerTemplate {

    private final String layerId;
    private final HapticRole role;
    private final HapticPrimitive primitive;
    private final Set<OutputKind> allowedOutputs;
    private final DeliveryMode delivery;
    private final CouplingMode coupling;
    private final int priority;
    private final int startOffsetMs;
    private final int expiresAfterMs;
    private final String coalesceKey;
    private final float strengthWeight;
    private final BodyRegion bodyRegion;

    public LayerTemplate(String layerId, HapticRole role, HapticPrimitive primitive,
                         Set<OutputKind> allowedOutputs, DeliveryMode delivery, CouplingMode coupling,
                         int priority, int startOffsetMs, int expiresAfterMs, String coalesceKey) {
        this(layerId, role, primitive, allowedOutputs, delivery, coupling, priority, startOffsetMs,
                expiresAfterMs, coalesceKey, 0f);
    }

    public LayerTemplate(String layerId, HapticRole role, HapticPrimitive primitive,
                         Set<OutputKind> allowedOutputs, DeliveryMode delivery, CouplingMode coupling,
                         int priority, int startOffsetMs, int expiresAfterMs, String coalesceKey,
                         float strengthWeight) {
        this(layerId, role, primitive, allowedOutputs, delivery, coupling, priority, startOffsetMs,
                expiresAfterMs, coalesceKey, strengthWeight, BodyRegion.WHOLE_BODY);
    }

    public LayerTemplate(String layerId, HapticRole role, HapticPrimitive primitive,
                         Set<OutputKind> allowedOutputs, DeliveryMode delivery, CouplingMode coupling,
                         int priority, int startOffsetMs, int expiresAfterMs, String coalesceKey,
                         float strengthWeight, BodyRegion bodyRegion) {
        if (layerId == null || layerId.trim().isEmpty()) {
            throw new IllegalArgumentException("layerId required");
        }
        if (primitive == null) {
            throw new IllegalArgumentException("primitive required");
        }
        if (role == null) {
            throw new IllegalArgumentException("role required");
        }
        this.layerId = layerId;
        this.role = role;
        this.primitive = primitive;
        this.allowedOutputs = allowedOutputs == null || allowedOutputs.isEmpty()
                ? Collections.<OutputKind>emptySet()
                : Collections.unmodifiableSet(EnumSet.copyOf(allowedOutputs));
        this.delivery = delivery;
        this.coupling = coupling;
        this.priority = priority;
        this.startOffsetMs = startOffsetMs;
        this.expiresAfterMs = expiresAfterMs;
        this.coalesceKey = coalesceKey;
        this.strengthWeight = HapticMath.clamp01(strengthWeight);
        this.bodyRegion = bodyRegion == null ? BodyRegion.WHOLE_BODY : bodyRegion;
    }

    public String layerId() {
        return layerId;
    }

    public HapticRole role() {
        return role;
    }

    public HapticPrimitive primitive() {
        return primitive;
    }

    public Set<OutputKind> allowedOutputs() {
        return allowedOutputs;
    }

    public DeliveryMode delivery() {
        return delivery;
    }

    public CouplingMode coupling() {
        return coupling;
    }

    public int priority() {
        return priority;
    }

    public int startOffsetMs() {
        return startOffsetMs;
    }

    public int expiresAfterMs() {
        return expiresAfterMs;
    }

    public String coalesceKey() {
        return coalesceKey;
    }

    public float strengthWeight() {
        return strengthWeight;
    }

    /** Where this layer is delivered on the body; {@link BodyRegion#WHOLE_BODY} unless authored otherwise. */
    public BodyRegion bodyRegion() {
        return bodyRegion;
    }

    /** Build the runtime layer at full volume and full strength (the authored reference). */
    public HapticLayer materialize() {
        return materialize(1f, 1f);
    }

    /**
     * Build the runtime layer, scaling amplitude by the user's volume and this layer's strength
     * response. The factor is {@code userGain * ((1 - strengthWeight) + strengthWeight * strength)};
     * only the primitive's level(s) are scaled, never its character or timing. Timing converts to
     * nanoseconds with long arithmetic so a multi-second offset does not overflow, and an empty
     * {@code allowedOutputs} lets {@link HapticRoute} pick its buzz default.
     */
    public HapticLayer materialize(float userGain, float strength) {
        float factor = userGain * ((1f - strengthWeight) + strengthWeight * strength);
        HapticRoute route = new HapticRoute(allowedOutputs, Collections.<Integer>emptySet(),
                Collections.emptySet(), Collections.emptySet(), delivery);
        return new HapticLayer(layerId, role, scale(primitive, factor), route, coupling, priority,
                startOffsetMs * 1_000_000L, expiresAfterMs * 1_000_000L, coalesceKey, bodyRegion);
    }

    /**
     * Scale a primitive's amplitude by {@code factor}, leaving character and timing untouched. The
     * trailing throw keeps this exhaustive so a new core primitive fails loudly rather than passing
     * through unscaled (mirrors PrimitiveEvaluator).
     */
    private static HapticPrimitive scale(HapticPrimitive p, float factor) {
        if (p instanceof HapticPrimitive.Impulse) {
            HapticPrimitive.Impulse i = (HapticPrimitive.Impulse) p;
            return new HapticPrimitive.Impulse(lvl(i.level(), factor), i.durationMs(), i.attackMs(),
                    i.releaseMs());
        } else if (p instanceof HapticPrimitive.Texture) {
            HapticPrimitive.Texture t = (HapticPrimitive.Texture) p;
            return new HapticPrimitive.Texture(lvl(t.level(), factor), t.durationMs(), t.grain(),
                    t.density(), t.irregularity());
        } else if (p instanceof HapticPrimitive.Rumble) {
            HapticPrimitive.Rumble r = (HapticPrimitive.Rumble) p;
            return new HapticPrimitive.Rumble(lvl(r.level(), factor), r.durationMs(), r.roughness(),
                    r.decay());
        } else if (p instanceof HapticPrimitive.Sweep) {
            HapticPrimitive.Sweep s = (HapticPrimitive.Sweep) p;
            return new HapticPrimitive.Sweep(lvl(s.from(), factor), lvl(s.to(), factor), s.durationMs(),
                    s.easing());
        } else if (p instanceof HapticPrimitive.BeatPattern) {
            HapticPrimitive.BeatPattern bp = (HapticPrimitive.BeatPattern) p;
            List<HapticPrimitive.Beat> beats = new ArrayList<>(bp.beats().size());
            for (HapticPrimitive.Beat b : bp.beats()) {
                beats.add(new HapticPrimitive.Beat(b.atMs(), lvl(b.level(), factor), b.durationMs()));
            }
            return new HapticPrimitive.BeatPattern(beats);
        } else if (p instanceof HapticPrimitive.Hold) {
            HapticPrimitive.Hold h = (HapticPrimitive.Hold) p;
            return new HapticPrimitive.Hold(lvl(h.level(), factor), h.durationMs(), h.fadeInMs(),
                    h.fadeOutMs());
        } else if (p instanceof HapticPrimitive.Oscillation) {
            HapticPrimitive.Oscillation o = (HapticPrimitive.Oscillation) p;
            return new HapticPrimitive.Oscillation(lvl(o.level(), factor), o.periodMs(), o.durationMs());
        }
        throw new IllegalStateException("Unknown HapticPrimitive: " + p);
    }

    private static float lvl(float level, float factor) {
        return HapticMath.clamp01(level * factor);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof LayerTemplate)) {
            return false;
        }
        LayerTemplate other = (LayerTemplate) o;
        return priority == other.priority
                && startOffsetMs == other.startOffsetMs
                && expiresAfterMs == other.expiresAfterMs
                && Float.compare(strengthWeight, other.strengthWeight) == 0
                && Objects.equals(layerId, other.layerId)
                && role == other.role
                && Objects.equals(primitive, other.primitive)
                && Objects.equals(allowedOutputs, other.allowedOutputs)
                && delivery == other.delivery
                && coupling == other.coupling
                && Objects.equals(coalesceKey, other.coalesceKey)
                && bodyRegion == other.bodyRegion;
    }

    @Override
    public int hashCode() {
        return Objects.hash(layerId, role, primitive, allowedOutputs, delivery, coupling, priority,
                startOffsetMs, expiresAfterMs, coalesceKey, strengthWeight, bodyRegion);
    }

    @Override
    public String toString() {
        return "LayerTemplate[layerId=" + layerId + ", role=" + role + ", primitive=" + primitive
                + ", allowedOutputs=" + allowedOutputs + ", delivery=" + delivery + ", coupling="
                + coupling + ", priority=" + priority + ", startOffsetMs=" + startOffsetMs
                + ", expiresAfterMs=" + expiresAfterMs + ", coalesceKey=" + coalesceKey
                + ", strengthWeight=" + strengthWeight + ", bodyRegion=" + bodyRegion + "]";
    }
}
