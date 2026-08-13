package net.minegasm.buttplug;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A device write that races a stop-all is compensated with another stop, so a device is never left
 * non-zero (review follow-up P1-2). The write happens first; the stop generation is re-checked after it.
 */
class StopCompensationTest {

    @Test
    void aStopThatLandsDuringTheWriteIsCompensated() {
        AtomicLong generation = new AtomicLong(5);
        AtomicBoolean compensated = new AtomicBoolean(false);

        // The write itself simulates a stop-all landing mid-write by bumping the generation.
        StopCompensation.writeThenMaybeStop(5, generation::get,
                () -> generation.incrementAndGet(),
                () -> compensated.set(true));

        assertTrue(compensated.get(), "a stop during the write triggers a compensating stop");
    }

    @Test
    void aWriteWithNoConcurrentStopIsNotCompensated() {
        AtomicLong generation = new AtomicLong(5);
        AtomicBoolean compensated = new AtomicBoolean(false);

        StopCompensation.writeThenMaybeStop(5, generation::get,
                () -> { /* no stop happens */ },
                () -> compensated.set(true));

        assertFalse(compensated.get(), "no compensating stop when nothing raced the write");
    }
}
