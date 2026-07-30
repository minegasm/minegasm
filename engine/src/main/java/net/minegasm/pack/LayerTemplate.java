package net.minegasm.pack;

import net.minegasm.core.CouplingMode;
import net.minegasm.core.DeliveryMode;
import net.minegasm.core.HapticLayer;
import net.minegasm.core.HapticPrimitive;
import net.minegasm.core.HapticRole;
import net.minegasm.core.HapticRoute;
import net.minegasm.core.OutputKind;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Authoring form of one {@link HapticLayer} (brief 0003 §2.3): a role, one primitive, a
 * capability-only route (allowed output kinds plus delivery, never device- or feature-specific so it
 * stays shareable), coupling, priority, and timing expressed in milliseconds relative to the scene.
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

    public LayerTemplate(String layerId, HapticRole role, HapticPrimitive primitive,
                         Set<OutputKind> allowedOutputs, DeliveryMode delivery, CouplingMode coupling,
                         int priority, int startOffsetMs, int expiresAfterMs, String coalesceKey) {
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

    /**
     * Build the runtime layer. Timing is converted to nanoseconds with long arithmetic; a
     * multi-second offset overflows a plain {@code int * 1_000_000}, so the {@code L} matters. An empty
     * {@code allowedOutputs} lets {@link HapticRoute}'s constructor pick its buzz default.
     */
    public HapticLayer materialize() {
        HapticRoute route = new HapticRoute(allowedOutputs, Collections.<Integer>emptySet(),
                Collections.emptySet(), Collections.emptySet(), delivery);
        return new HapticLayer(layerId, role, primitive, route, coupling, priority,
                startOffsetMs * 1_000_000L, expiresAfterMs * 1_000_000L, coalesceKey);
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
                && Objects.equals(layerId, other.layerId)
                && role == other.role
                && Objects.equals(primitive, other.primitive)
                && Objects.equals(allowedOutputs, other.allowedOutputs)
                && delivery == other.delivery
                && coupling == other.coupling
                && Objects.equals(coalesceKey, other.coalesceKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(layerId, role, primitive, allowedOutputs, delivery, coupling, priority,
                startOffsetMs, expiresAfterMs, coalesceKey);
    }

    @Override
    public String toString() {
        return "LayerTemplate[layerId=" + layerId + ", role=" + role + ", primitive=" + primitive
                + ", allowedOutputs=" + allowedOutputs + ", delivery=" + delivery + ", coupling="
                + coupling + ", priority=" + priority + ", startOffsetMs=" + startOffsetMs
                + ", expiresAfterMs=" + expiresAfterMs + ", coalesceKey=" + coalesceKey + "]";
    }
}
