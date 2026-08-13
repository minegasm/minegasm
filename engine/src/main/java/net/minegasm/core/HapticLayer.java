package net.minegasm.core;

import java.util.Objects;

/**
 * One renderable layer of a scene: a primitive, its role, its route, coupling, priority, and its
 * real-time offset/expiry relative to the scene (brief §5.2). Layers, not scenes, are what the
 * renderer turns into per-feature commands.
 */
public final class HapticLayer {

    private final String layerId;
    private final HapticRole role;
    private final HapticPrimitive primitive;
    private final HapticRoute route;
    private final CouplingMode coupling;
    private final int priority;
    private final long startOffsetNs;
    private final long expiresAfterNs;
    private final String coalesceKey;
    private final BodyRegion bodyRegion;

    /** Convenience for the common case: a whole-body layer that competes across the whole role. */
    public HapticLayer(
            String layerId,
            HapticRole role,
            HapticPrimitive primitive,
            HapticRoute route,
            CouplingMode coupling,
            int priority,
            long startOffsetNs,
            long expiresAfterNs,
            String coalesceKey) {
        this(layerId, role, primitive, route, coupling, priority, startOffsetNs, expiresAfterNs,
                coalesceKey, BodyRegion.WHOLE_BODY);
    }

    public HapticLayer(
            String layerId,
            HapticRole role,
            HapticPrimitive primitive,
            HapticRoute route,
            CouplingMode coupling,
            int priority,
            long startOffsetNs,
            long expiresAfterNs,
            String coalesceKey,
            BodyRegion bodyRegion) {
        if (layerId == null || layerId.trim().isEmpty()) {
            throw new IllegalArgumentException("layerId required");
        }
        if (primitive == null) {
            throw new IllegalArgumentException("primitive required");
        }
        if (expiresAfterNs < 0) {
            throw new IllegalArgumentException("expiresAfterNs must be >= 0");
        }
        this.layerId = layerId;
        this.role = role == null ? HapticRole.IMPACT : role;
        this.primitive = primitive;
        this.route = route == null ? HapticRoute.buzzAll() : route;
        this.coupling = coupling == null ? CouplingMode.MAX : coupling;
        this.priority = priority;
        this.startOffsetNs = startOffsetNs;
        this.expiresAfterNs = expiresAfterNs;
        this.coalesceKey = coalesceKey;
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

    public HapticRoute route() {
        return route;
    }

    public CouplingMode coupling() {
        return coupling;
    }

    public int priority() {
        return priority;
    }

    public long startOffsetNs() {
        return startOffsetNs;
    }

    public long expiresAfterNs() {
        return expiresAfterNs;
    }

    public String coalesceKey() {
        return coalesceKey;
    }

    /** Where this layer is delivered on the body; {@link BodyRegion#WHOLE_BODY} unless authored otherwise. */
    public BodyRegion bodyRegion() {
        return bodyRegion;
    }

    /** A copy of this layer with its primitive replaced (everything else unchanged). */
    public HapticLayer withPrimitive(HapticPrimitive replacement) {
        return new HapticLayer(layerId, role, replacement, route, coupling, priority, startOffsetNs,
                expiresAfterNs, coalesceKey, bodyRegion);
    }

    public boolean isContinuous() {
        return coalesceKey != null && !coalesceKey.trim().isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof HapticLayer)) {
            return false;
        }
        HapticLayer other = (HapticLayer) o;
        return priority == other.priority
                && startOffsetNs == other.startOffsetNs
                && expiresAfterNs == other.expiresAfterNs
                && Objects.equals(layerId, other.layerId)
                && role == other.role
                && Objects.equals(primitive, other.primitive)
                && Objects.equals(route, other.route)
                && coupling == other.coupling
                && Objects.equals(coalesceKey, other.coalesceKey)
                && bodyRegion == other.bodyRegion;
    }

    @Override
    public int hashCode() {
        return Objects.hash(layerId, role, primitive, route, coupling, priority, startOffsetNs,
                expiresAfterNs, coalesceKey, bodyRegion);
    }

    @Override
    public String toString() {
        return "HapticLayer[layerId=" + layerId + ", role=" + role + ", primitive=" + primitive
                + ", route=" + route + ", coupling=" + coupling + ", priority=" + priority
                + ", startOffsetNs=" + startOffsetNs + ", expiresAfterNs=" + expiresAfterNs
                + ", coalesceKey=" + coalesceKey + ", bodyRegion=" + bodyRegion + "]";
    }
}
