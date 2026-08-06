package net.minegasm.runtime;

import net.minegasm.core.HapticScene;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Holds the currently active scenes: continuous scenes are latest-wins by their key, discrete scenes
 * coexist until they expire (brief §10.1). This is the scene-level bookkeeping that ADR-018 lifts out
 * of the per-cycle renderer: coalescing, expiry, and the pause-time shift, with no device or signal
 * logic. {@link SceneMixer} renders a snapshot of this store; it does not own it.
 *
 * <p>Not synchronised on its own. The worker still owns an instance directly during the extraction step;
 * once ownership moves to {@link SceneGovernor}, that class provides the single lock that guards it.
 */
public final class SceneStore {

    private final List<HapticScene> discrete = new ArrayList<>();
    private final Map<String, HapticScene> continuous = new LinkedHashMap<>();

    public void add(HapticScene scene) {
        if (scene == null) {
            return;
        }
        if (scene.isContinuous()) {
            continuous.put(scene.continuousKey(), scene); // latest-wins
        } else {
            discrete.add(scene);
        }
    }

    /** Drop expired scenes. */
    public void update(long nowNs) {
        discrete.removeIf(s -> s.isExpired(nowNs));
        continuous.values().removeIf(s -> s.isExpired(nowNs));
    }

    public void clear() {
        discrete.clear();
        continuous.clear();
    }

    /** Shift every held scene forward after a real-time pause. */
    public void shiftTime(long deltaNs) {
        if (deltaNs <= 0) {
            return;
        }
        for (int i = 0; i < discrete.size(); i++) {
            discrete.set(i, shifted(discrete.get(i), deltaNs));
        }
        continuous.replaceAll((key, scene) -> shifted(scene, deltaNs));
    }

    private static HapticScene shifted(HapticScene scene, long deltaNs) {
        return new HapticScene(scene.sceneId(), scene.kind(), scene.priority(), scene.layers(),
                scene.createdAtNs() + deltaNs, scene.expiresAtNs() + deltaNs,
                scene.continuousKey());
    }

    public boolean isEmpty() {
        return discrete.isEmpty() && continuous.isEmpty();
    }

    public int activeSceneCount() {
        return discrete.size() + continuous.size();
    }

    public int discreteCount() {
        return discrete.size();
    }

    /**
     * Make room among the discrete scenes by dropping one, mirroring the old ingress overflow policy:
     * an expired scene first, otherwise the lowest-priority scene, but only if it is no higher than
     * {@code belowPriority} (an incoming scene never displaces a stronger one). Continuous scenes
     * coalesce by key and are never subject to this bound. Returns whether a scene was removed.
     */
    public boolean evictOneDiscrete(long nowNs, int belowPriority) {
        int lowestIdx = -1;
        int lowestPriority = Integer.MAX_VALUE;
        for (int i = 0; i < discrete.size(); i++) {
            HapticScene s = discrete.get(i);
            if (s.isExpired(nowNs)) {
                discrete.remove(i);
                return true;
            }
            if (s.priority() < lowestPriority) {
                lowestPriority = s.priority();
                lowestIdx = i;
            }
        }
        if (lowestIdx >= 0 && lowestPriority <= belowPriority) {
            discrete.remove(lowestIdx);
            return true;
        }
        return false;
    }

    /** Snapshot of all held scenes, discrete first then continuous, for rendering or fan-out. */
    public List<HapticScene> snapshot() {
        List<HapticScene> all = new ArrayList<>(discrete.size() + continuous.size());
        all.addAll(discrete);
        all.addAll(continuous.values());
        return all;
    }
}
