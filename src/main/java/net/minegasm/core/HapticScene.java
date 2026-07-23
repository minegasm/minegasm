package net.minegasm.core;

import java.util.List;

/**
 * A semantic, device-independent unit produced by the recipe resolver and mixed by the engine. One
 * scene may contain several layers (e.g. a vibration impulse plus an experimental motion segment).
 * Timing is monotonic (brief §5.2, §6.1).
 */
public record HapticScene(
        String sceneId,
        GameEventKind kind,
        int priority,
        List<HapticLayer> layers,
        long createdAtNs,
        long expiresAtNs,
        String continuousKey) {

    public HapticScene {
        if (sceneId == null || sceneId.isBlank()) {
            throw new IllegalArgumentException("sceneId required");
        }
        layers = layers == null ? List.of() : List.copyOf(layers);
        if (expiresAtNs < createdAtNs) {
            throw new IllegalArgumentException("scene expires before it is created");
        }
    }

    public boolean isExpired(long nowNs) {
        return nowNs >= expiresAtNs;
    }

    public boolean isContinuous() {
        return continuousKey != null && !continuousKey.isBlank();
    }

    public long remainingNs(long nowNs) {
        return Math.max(0L, expiresAtNs - nowNs);
    }
}

