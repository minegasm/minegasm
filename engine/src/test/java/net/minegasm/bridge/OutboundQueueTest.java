package net.minegasm.bridge;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The bounded, one-in-flight queue: drop-oldest when full, and stop clears pending so none follows. */
class OutboundQueueTest {

    private final List<String> sent = new ArrayList<>();
    private final List<CompletableFuture<Void>> futures = new ArrayList<>();

    /** Records the frame and returns a future the test completes by hand, to control in-flight timing. */
    private CompletionStage<Void> record(String frame) {
        sent.add(frame);
        CompletableFuture<Void> f = new CompletableFuture<>();
        futures.add(f);
        return f;
    }

    @Test
    void dropsOldestWhenFull() {
        OutboundQueue q = new OutboundQueue(2, this::record);
        q.offer("a"); // sent immediately, now in flight
        q.offer("b"); // queued
        q.offer("c"); // queued (queue is now [b, c], at capacity)
        q.offer("d"); // full: oldest pending (b) dropped, queue is [c, d]

        assertEquals(2, q.size());
        assertEquals(Arrays.asList("a"), sent, "only the first frame is in flight so far");

        futures.get(0).complete(null); // a done: c drains and goes in flight
        assertEquals(Arrays.asList("a", "c"), sent, "b was dropped, d still queued");
        assertEquals(1, q.size());
    }

    @Test
    void stopClearsPendingSoNoEffectFollowsIt() {
        OutboundQueue q = new OutboundQueue(10, this::record);
        q.offer("e1"); // in flight
        q.offer("e2"); // queued
        q.offer("e3"); // queued

        q.clearAndOffer("STOP"); // drops e2 and e3; STOP waits behind the in-flight e1

        futures.get(0).complete(null); // e1 done: STOP drains next
        assertEquals(Arrays.asList("e1", "STOP"), sent, "queued effects are dropped, stop is not overtaken");
    }
}
