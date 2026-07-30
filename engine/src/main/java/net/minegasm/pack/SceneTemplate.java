package net.minegasm.pack;

import net.minegasm.core.GameEventKind;
import net.minegasm.core.HapticLayer;
import net.minegasm.core.HapticScene;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Authoring form of a {@link HapticScene} (brief 0003 §2.3): a priority, a total duration in
 * milliseconds, an optional continuous key, and the layers. It carries no absolute timestamps, those
 * are stamped at {@link #materialize} time so a shared file has no runtime clock baked in.
 */
public final class SceneTemplate {

    private final int priority;
    private final int durationMs;
    private final String continuousKey;
    private final List<LayerTemplate> layers;

    public SceneTemplate(int priority, int durationMs, String continuousKey,
                         List<LayerTemplate> layers) {
        this.priority = priority;
        this.durationMs = durationMs;
        this.continuousKey = continuousKey;
        this.layers = layers == null
                ? Collections.<LayerTemplate>emptyList()
                : Collections.unmodifiableList(new ArrayList<>(layers));
    }

    public int priority() {
        return priority;
    }

    public int durationMs() {
        return durationMs;
    }

    public String continuousKey() {
        return continuousKey;
    }

    public List<LayerTemplate> layers() {
        return layers;
    }

    /**
     * Build the runtime scene for {@code kind} at {@code nowNs}. Duration is converted with long
     * arithmetic so a multi-second scene does not overflow. The scene id is namespaced by the pack so
     * two packs cannot collide.
     */
    public HapticScene materialize(String packId, GameEventKind kind, long nowNs) {
        List<HapticLayer> built = new ArrayList<>(layers.size());
        for (LayerTemplate layer : layers) {
            built.add(layer.materialize());
        }
        long expiresAtNs = nowNs + durationMs * 1_000_000L;
        return new HapticScene(packId + ":" + kind.name(), kind, priority, built, nowNs, expiresAtNs,
                continuousKey);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SceneTemplate)) {
            return false;
        }
        SceneTemplate other = (SceneTemplate) o;
        return priority == other.priority
                && durationMs == other.durationMs
                && Objects.equals(continuousKey, other.continuousKey)
                && Objects.equals(layers, other.layers);
    }

    @Override
    public int hashCode() {
        return Objects.hash(priority, durationMs, continuousKey, layers);
    }

    @Override
    public String toString() {
        return "SceneTemplate[priority=" + priority + ", durationMs=" + durationMs
                + ", continuousKey=" + continuousKey + ", layers=" + layers + "]";
    }
}
