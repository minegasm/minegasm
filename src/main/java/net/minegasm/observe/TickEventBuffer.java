package net.minegasm.observe;

import net.minegasm.core.GameEventKind;
import net.minegasm.core.RawGameEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Bounded per-tick discrete event buffer (brief §6.2 structure A). Written and drained on the client
 * thread. On overflow it drops the lowest-value observations (ambient/mining first) rather than
 * allocating without bound, and counts drops for diagnostics. Cleared each tick and on stop.
 */
public final class TickEventBuffer {

    public static final int DEFAULT_CAPACITY = 128;

    private final int capacity;
    private final Deque<RawGameEvent> events;
    private long droppedCount;

    public TickEventBuffer() {
        this(DEFAULT_CAPACITY);
    }

    public TickEventBuffer(int capacity) {
        this.capacity = Math.max(1, capacity);
        this.events = new ArrayDeque<>(this.capacity);
    }

    public void add(RawGameEvent event) {
        if (event == null) {
            return;
        }
        if (events.size() >= capacity && !evictLowValue()) {
            droppedCount++;
            return;
        }
        events.addLast(event);
    }

    public List<RawGameEvent> drain() {
        if (events.isEmpty()) {
            return List.of();
        }
        List<RawGameEvent> out = new ArrayList<>(events);
        events.clear();
        return out;
    }

    public void clear() {
        events.clear();
    }

    public int size() {
        return events.size();
    }

    public long droppedCount() {
        return droppedCount;
    }

    /** Try to drop one low-value (ambient/mining) event to make room; returns true if space freed. */
    private boolean evictLowValue() {
        for (RawGameEvent e : events) {
            if (e.kind() == GameEventKind.AMBIENT || e.kind() == GameEventKind.MINING_ACTIVE) {
                events.remove(e);
                droppedCount++;
                return true;
            }
        }
        return false;
    }
}
