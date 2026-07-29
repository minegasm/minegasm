package net.minegasm.runtime;

import net.minegasm.core.GameEventKind;
import net.minegasm.core.HapticScene;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SceneIngressQueueTest {

    private HapticScene scene(String id, int priority, long createdNs, long expiresNs) {
        return new HapticScene(id, GameEventKind.ATTACK, priority, List.of(), createdNs, expiresNs, null);
    }

    @Test
    void overflowDropsLowestPriority() {
        SceneIngressQueue q = new SceneIngressQueue(2);
        assertTrue(q.offer(scene("low", 10, 0, 1_000_000_000L), 0));
        assertTrue(q.offer(scene("mid", 50, 0, 1_000_000_000L), 0));
        // Full: incoming high priority evicts the lowest (10).
        assertTrue(q.offer(scene("high", 90, 0, 1_000_000_000L), 0));
        List<HapticScene> drained = q.drain();
        assertEquals(2, drained.size());
        assertTrue(drained.stream().noneMatch(s -> s.sceneId().equals("low")));
        assertTrue(q.droppedCount() >= 1);
    }

    @Test
    void expiredSceneEvictedFirst() {
        SceneIngressQueue q = new SceneIngressQueue(2);
        q.offer(scene("expired", 90, 0, 100), 0);
        q.offer(scene("live", 20, 0, 10_000_000_000L), 0);
        // At now=1s the "expired" scene is gone; even a low-priority incoming is accepted.
        assertTrue(q.offer(scene("new", 5, 1_000_000_000L, 10_000_000_000L), 1_000_000_000L));
        List<HapticScene> drained = q.drain();
        assertTrue(drained.stream().noneMatch(s -> s.sceneId().equals("expired")));
    }

    @Test
    void lowestPriorityIncomingRejectedWhenFullOfHigher() {
        SceneIngressQueue q = new SceneIngressQueue(1);
        assertTrue(q.offer(scene("high", 90, 0, 1_000_000_000L), 0));
        assertFalse(q.offer(scene("low", 5, 0, 1_000_000_000L), 0),
                "cannot displace a higher-priority scene");
    }

    @Test
    void drainClearsQueue() {
        SceneIngressQueue q = new SceneIngressQueue(4);
        q.offer(scene("a", 10, 0, 1_000_000_000L), 0);
        assertEquals(1, q.size());
        q.drain();
        assertEquals(0, q.size());
    }
}
