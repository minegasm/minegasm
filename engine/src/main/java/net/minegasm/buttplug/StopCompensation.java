package net.minegasm.buttplug;

import java.util.function.LongSupplier;

/**
 * Sequencing for a device write that must not survive a stop-all issued while it was in progress (brief
 * §9.10, review follow-up P1-2). A blocking write can only check "has a stop happened" before it starts;
 * a stop that lands during the write would otherwise be overtaken, leaving the device at a non-zero value.
 *
 * <p>This runs the write, then re-checks the caller's monotonic stop generation. If it changed during the
 * write, a stop happened concurrently, so the write is compensated with another stop, making a zero the
 * last command the device sees. Kept dependency-free (no device library) so it is unit-testable.
 */
public final class StopCompensation {

    private StopCompensation() {
    }

    /**
     * @param generationBefore the stop generation captured before the write was scheduled
     * @param currentGeneration reads the live stop generation (bumped by every stop-all)
     * @param write the blocking device write to perform
     * @param compensatingStop issued only if a stop raced the write, to reassert the zero
     */
    public static void writeThenMaybeStop(long generationBefore, LongSupplier currentGeneration,
                                          Runnable write, Runnable compensatingStop) {
        write.run();
        if (generationBefore != currentGeneration.getAsLong()) {
            compensatingStop.run();
        }
    }
}
