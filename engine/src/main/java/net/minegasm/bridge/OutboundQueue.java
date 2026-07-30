package net.minegasm.bridge;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/**
 * A bounded, FIFO, one-in-flight outbound frame queue for the bridge (brief 0002 §4.3, "queues are
 * bounded and stale messages are dropped"). It hands one frame at a time to the sender and waits for
 * that send to complete before the next, which the JDK WebSocket requires and which keeps ordering
 * deterministic. When full, the oldest pending frame is dropped.
 *
 * <p>Ordering matters for safety: {@link #clearAndOffer} drops every pending frame and enqueues one, so
 * a stop-all clears queued effects and is sent next, and no already-queued effect can be delivered after
 * it. A single effect that was already in flight completes first; nothing is sent after the stop.
 */
final class OutboundQueue {

    private final int capacity;
    private final Function<String, CompletionStage<Void>> sender;
    private final Deque<String> queue = new ArrayDeque<>();
    private boolean inFlight;
    private boolean closed;

    OutboundQueue(int capacity, Function<String, CompletionStage<Void>> sender) {
        this.capacity = Math.max(1, capacity);
        this.sender = sender;
    }

    /** Enqueue a frame, dropping the oldest pending frame if the queue is full. */
    void offer(String frame) {
        synchronized (this) {
            if (closed) {
                return;
            }
            while (queue.size() >= capacity) {
                queue.pollFirst();
            }
            queue.addLast(frame);
        }
        pump();
    }

    /** Drop every pending frame, then enqueue one. Used for stop-all so no queued effect follows it. */
    void clearAndOffer(String frame) {
        synchronized (this) {
            if (closed) {
                return;
            }
            queue.clear();
            queue.addLast(frame);
        }
        pump();
    }

    void close() {
        synchronized (this) {
            closed = true;
            queue.clear();
        }
    }

    int size() {
        synchronized (this) {
            return queue.size();
        }
    }

    private void pump() {
        String frame;
        synchronized (this) {
            if (closed || inFlight || queue.isEmpty()) {
                return;
            }
            frame = queue.pollFirst();
            inFlight = true;
        }
        // Call the sender outside the lock. A failed send is treated as done so the queue keeps draining
        // rather than wedging on one frame; the dropped frame's effect self-expires via its TTL.
        CompletionStage<Void> stage;
        try {
            stage = sender.apply(frame);
        } catch (RuntimeException failed) {
            completed();
            return;
        }
        stage.whenComplete((v, error) -> completed());
    }

    private void completed() {
        synchronized (this) {
            inFlight = false;
        }
        pump();
    }
}
