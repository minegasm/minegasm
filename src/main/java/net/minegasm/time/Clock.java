package net.minegasm.time;

/**
 * Monotonic clock abstraction. Every duration, cooldown, expiry, pattern phase, and reconnect
 * backoff in the engine is measured against this, never against Minecraft tick counts (see brief
 * Â§6.1). Tests inject a {@link FakeClock} so no test ever sleeps for timing behaviour.
 */
public interface Clock {

    /**
     * Monotonic time in nanoseconds. Only differences between two readings are meaningful; the
     * absolute value has no relation to wall-clock time.
     */
    long nanoTime();

    /** Convenience: current time in milliseconds derived from {@link #nanoTime()}. */
    default long millis() {
        return nanoTime() / 1_000_000L;
    }
}

