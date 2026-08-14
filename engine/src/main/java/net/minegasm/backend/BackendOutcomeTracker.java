package net.minegasm.backend;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/** Turns non-blocking completion stages into bounded, generation-aware backend outcomes. */
public final class BackendOutcomeTracker implements AutoCloseable {

    private final String backendId;
    private final LongSupplier clock;
    private final long timeoutMs;
    private final ScheduledExecutorService timer;
    private final AtomicReference<BackendOutcome> latest = new AtomicReference<>();
    private final AtomicReference<BackendOutcome> unresolvedFailure = new AtomicReference<>();
    private volatile Consumer<BackendOutcome> listener = outcome -> { };

    public BackendOutcomeTracker(String backendId, LongSupplier clock, long timeoutMs) {
        this.backendId = backendId;
        this.clock = clock;
        this.timeoutMs = timeoutMs;
        this.timer = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "minegasm-outcome-" + backendId);
            thread.setDaemon(true);
            return thread;
        });
    }

    public void setListener(Consumer<BackendOutcome> listener) {
        this.listener = listener == null ? outcome -> { } : listener;
    }

    public BackendOutcome latest() {
        return latest.get();
    }

    public BackendOutcome unresolvedFailure() {
        return unresolvedFailure.get();
    }

    public void clearFailure() {
        unresolvedFailure.set(null);
    }

    public void observe(BackendOperation operation, long generation, CompletionStage<Void> stage,
                        BooleanSupplier superseded) {
        emit(new BackendOutcome(backendId, operation, BackendOutcomeState.ACCEPTED, generation,
                clock.getAsLong(), null));
        if (stage == null) {
            emit(new BackendOutcome(backendId, operation, BackendOutcomeState.FAILED, generation,
                    clock.getAsLong(), "provider returned no completion"));
            return;
        }
        AtomicBoolean settled = new AtomicBoolean();
        ScheduledFuture<?> timeout = timer.schedule(() -> {
            if (settled.compareAndSet(false, true)) {
                emit(new BackendOutcome(backendId, operation, BackendOutcomeState.TIMED_OUT, generation,
                        clock.getAsLong(), "no completion within " + timeoutMs + " ms"));
            }
        }, timeoutMs, TimeUnit.MILLISECONDS);
        stage.whenComplete((ignored, error) -> {
            if (!settled.compareAndSet(false, true)) {
                return;
            }
            timeout.cancel(false);
            Throwable cause = unwrap(error);
            BackendOutcomeState state;
            String detail = null;
            if (cause != null && !(cause instanceof CancellationException)) {
                state = BackendOutcomeState.FAILED;
                detail = cause.getClass().getSimpleName()
                        + (cause.getMessage() == null ? "" : ": " + cause.getMessage());
            } else if (superseded.getAsBoolean() || cause instanceof CancellationException) {
                state = BackendOutcomeState.SUPERSEDED;
            } else {
                state = BackendOutcomeState.DELIVERED;
            }
            emit(new BackendOutcome(backendId, operation, state, generation, clock.getAsLong(), detail));
        });
    }

    private void emit(BackendOutcome outcome) {
        latest.set(outcome);
        // A diagnostic test result belongs in latest action feedback, but it is not evidence that the
        // ordinary output path is unsafe. Only live send and stop failures become persistent health
        // faults and backend quarantine candidates.
        if (outcome.unresolvedFault() && outcome.operation() != BackendOperation.TEST) {
            unresolvedFailure.set(outcome);
        }
        listener.accept(outcome);
    }

    private static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while ((current instanceof CompletionException
                || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    @Override
    public void close() {
        timer.shutdownNow();
    }
}
