package net.minegasm.core;

/**
 * One renderable layer of a scene: a primitive, its role, its route, coupling, priority, and its
 * real-time offset/expiry relative to the scene (brief §5.2). Layers, not scenes, are what the
 * renderer turns into per-feature commands.
 */
public record HapticLayer(
        String layerId,
        HapticRole role,
        HapticPrimitive primitive,
        HapticRoute route,
        CouplingMode coupling,
        int priority,
        long startOffsetNs,
        long expiresAfterNs,
        String coalesceKey) {

    public HapticLayer {
        if (layerId == null || layerId.isBlank()) {
            throw new IllegalArgumentException("layerId required");
        }
        if (primitive == null) {
            throw new IllegalArgumentException("primitive required");
        }
        role = role == null ? HapticRole.IMPACT : role;
        route = route == null ? HapticRoute.vibrateAll() : route;
        coupling = coupling == null ? CouplingMode.MAX : coupling;
        if (expiresAfterNs < 0) {
            throw new IllegalArgumentException("expiresAfterNs must be >= 0");
        }
    }

    public boolean isContinuous() {
        return coalesceKey != null && !coalesceKey.isBlank();
    }
}

