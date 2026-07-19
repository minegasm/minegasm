package net.minegasm.runtime;

import net.minegasm.core.HapticScene;

import java.util.ArrayList;
import java.util.List;

/**
 * Bounded cross-thread scene ingress queue (brief §6.2 structure C). The client thread offers
 * without blocking; the haptic worker drains. On overflow it drops expired and then lowest-priority
 * scenes, never allocating without bound. Capacity defaults to 64 (brief recommendation).
 *
 * <p>Synchronised rather than lock-free: contention is trivial (one producer, one consumer) and a
 * small monitor keeps the overflow policy simple and correct.
 */
public final class SceneIngressQueue {

    public static final int DEFAULT_CAPACITY = 64;

    private final int capacity;
    private final List<HapticScene> scenes;
    private long droppedCount;

    public SceneIngressQueue() {
        this(DEFAULT_CAPACITY);
    }

    public SceneIngressQueue(int capacity) {
        this.capacity = Math.max(1, capacity);
        this.scenes = new ArrayList<>(this.capacity);
    }

    /**
     * Offer a scene without blocking. Returns false if the scene could not be accepted (only when it
     * is itself the lowest-priority candidate and the queue is full). Never grows past capacity.
     */
    public synchronized boolean offer(HapticScene scene, long nowNs) {
        if (scene == null) {
            return false;
        }
        if (scenes.size() >= capacity) {
            evictOne(nowNs, scene.priority());
        }
        if (scenes.size() >= capacity) {
            droppedCount++;
            return false;
        }
        scenes.add(scene);
        return true;
    }

    /** Remove and return all queued scenes, clearing the queue. */
    public synchronized List<HapticScene> drain() {
        if (scenes.isEmpty()) {
            return List.of();
        }
        List<HapticScene> out = new ArrayList<>(scenes);
        scenes.clear();
        return out;
    }

    public synchronized int size() {
        return scenes.size();
    }

    public synchronized long droppedCount() {
        return droppedCount;
    }

    public synchronized void clear() {
        scenes.clear();
    }

    /** Drop one expired scene if any, else the lowest-priority scene below {@code incomingPriority}. */
    private void evictOne(long nowNs, int incomingPriority) {
        int expiredIdx = -1;
        int lowestIdx = -1;
        int lowestPriority = Integer.MAX_VALUE;
        for (int i = 0; i < scenes.size(); i++) {
            HapticScene s = scenes.get(i);
            if (s.isExpired(nowNs)) {
                expiredIdx = i;
                break;
            }
            if (s.priority() < lowestPriority) {
                lowestPriority = s.priority();
                lowestIdx = i;
            }
        }
        if (expiredIdx >= 0) {
            scenes.remove(expiredIdx);
            droppedCount++;
        } else if (lowestIdx >= 0 && lowestPriority <= incomingPriority) {
            scenes.remove(lowestIdx);
            droppedCount++;
        }
    }
}
