package net.minegasm.backend;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class BackendOutcomeTrackerTest {

    @Test
    void realFailureIsNotHiddenByALaterLifecycleGeneration() {
        BackendOutcomeTracker tracker = new BackendOutcomeTracker("test", () -> 10L, 1_000L);
        try {
            CompletableFuture<Void> completion = new CompletableFuture<>();
            tracker.observe(BackendOperation.SEND, 7L, completion, () -> true);
            completion.completeExceptionally(new IllegalStateException("late hardware error"));

            assertEquals(BackendOutcomeState.FAILED, tracker.latest().state());
            assertSame(tracker.latest(), tracker.unresolvedFailure());
        } finally {
            tracker.close();
        }
    }

    @Test
    void diagnosticFailureIsActionFeedbackNotPersistentBackendHealth() {
        BackendOutcomeTracker tracker = new BackendOutcomeTracker("test", () -> 10L, 1_000L);
        try {
            CompletableFuture<Void> completion = new CompletableFuture<>();
            tracker.observe(BackendOperation.TEST, 8L, completion, () -> false);
            completion.completeExceptionally(new IllegalStateException("no compatible target"));

            assertEquals(BackendOutcomeState.FAILED, tracker.latest().state());
            assertNull(tracker.unresolvedFailure());
        } finally {
            tracker.close();
        }
    }
}
