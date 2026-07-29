package net.minegasm.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A semantic, device-independent unit produced by the recipe resolver and mixed by the engine. One
 * scene may contain several layers (e.g. a vibration impulse plus an experimental motion segment).
 * Timing is monotonic (brief §5.2, §6.1).
 */
public final class HapticScene {

    private final String sceneId;
    private final GameEventKind kind;
    private final int priority;
    private final List<HapticLayer> layers;
    private final long createdAtNs;
    private final long expiresAtNs;
    private final String continuousKey;

    public HapticScene(
            String sceneId,
            GameEventKind kind,
            int priority,
            List<HapticLayer> layers,
            long createdAtNs,
            long expiresAtNs,
            String continuousKey) {
        if (sceneId == null || sceneId.trim().isEmpty()) {
            throw new IllegalArgumentException("sceneId required");
        }
        if (expiresAtNs < createdAtNs) {
            throw new IllegalArgumentException("scene expires before it is created");
        }
        this.sceneId = sceneId;
        this.kind = kind;
        this.priority = priority;
        this.layers = layers == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(layers));
        this.createdAtNs = createdAtNs;
        this.expiresAtNs = expiresAtNs;
        this.continuousKey = continuousKey;
    }

    public String sceneId() {
        return sceneId;
    }

    public GameEventKind kind() {
        return kind;
    }

    public int priority() {
        return priority;
    }

    public List<HapticLayer> layers() {
        return layers;
    }

    public long createdAtNs() {
        return createdAtNs;
    }

    public long expiresAtNs() {
        return expiresAtNs;
    }

    public String continuousKey() {
        return continuousKey;
    }

    public boolean isExpired(long nowNs) {
        return nowNs >= expiresAtNs;
    }

    public boolean isContinuous() {
        return continuousKey != null && !continuousKey.trim().isEmpty();
    }

    public long remainingNs(long nowNs) {
        return Math.max(0L, expiresAtNs - nowNs);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof HapticScene)) {
            return false;
        }
        HapticScene other = (HapticScene) o;
        return priority == other.priority
                && createdAtNs == other.createdAtNs
                && expiresAtNs == other.expiresAtNs
                && Objects.equals(sceneId, other.sceneId)
                && kind == other.kind
                && Objects.equals(layers, other.layers)
                && Objects.equals(continuousKey, other.continuousKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sceneId, kind, priority, layers, createdAtNs, expiresAtNs, continuousKey);
    }

    @Override
    public String toString() {
        return "HapticScene[sceneId=" + sceneId + ", kind=" + kind + ", priority=" + priority
                + ", layers=" + layers + ", createdAtNs=" + createdAtNs + ", expiresAtNs=" + expiresAtNs
                + ", continuousKey=" + continuousKey + "]";
    }
}
