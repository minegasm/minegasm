package net.minegasm.time;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Deterministic {@link Clock} for tests. Time only advances when explicitly told to, so timing,
 * expiry, and scheduling behaviour is verified without {@code Thread.sleep} (brief §14.1, §H).
 *
 * <p>Thread-safe: the worker and test thread may read/advance concurrently.
 */
public final class FakeClock implements Clock {

    private final AtomicLong nanos;

    public FakeClock() {
        this(0L);
    }

    public FakeClock(long startNanos) {
        this.nanos = new AtomicLong(startNanos);
    }

    @Override
    public long nanoTime() {
        return nanos.get();
    }

    /** Advance the clock and return the new value. */
    public long advanceNanos(long deltaNanos) {
        if (deltaNanos < 0) {
            throw new IllegalArgumentException("clock must be monotonic; delta=" + deltaNanos);
        }
        return nanos.addAndGet(deltaNanos);
    }

    public long advanceMillis(long deltaMillis) {
        return advanceNanos(deltaMillis * 1_000_000L);
    }

    public long advance(Duration duration) {
        return advanceNanos(duration.toNanos());
    }
}

