package net.minegasm.runtime;

import net.minegasm.buttplug.ConnectionState;
import net.minegasm.config.ReconnectParams;
import org.junit.jupiter.api.Test;

import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReconnectSupervisorTest {

    private static final long MS = 1_000_000L;

    private final ReconnectParams enabled = new ReconnectParams(true, 30);
    private final AtomicInteger connects = new AtomicInteger();
    private final Runnable connect = connects::incrementAndGet;

    /** Stub the jitter draw; {@code 1.0} makes each delay exactly its base (2^attempt seconds). */
    private ReconnectSupervisor supervisorWithJitter(double value) {
        return new ReconnectSupervisor(new Random() {
            @Override
            public double nextDouble() {
                return value;
            }
        });
    }

    @Test
    void doesNothingWhenNoConnectionWanted() {
        ReconnectSupervisor s = supervisorWithJitter(0.0);
        for (long t = 0; t < 60 * 1000L; t += 50) {
            s.tick(t * MS, ConnectionState.DISCONNECTED, false, enabled, connect);
        }
        assertEquals(0, connects.get());
    }

    @Test
    void doesNothingWhileConnected() {
        ReconnectSupervisor s = supervisorWithJitter(0.0);
        for (long t = 0; t < 60 * 1000L; t += 50) {
            s.tick(t * MS, ConnectionState.READY, true, enabled, connect);
        }
        assertEquals(0, connects.get());
    }

    @Test
    void doesNothingWhenReconnectDisabled() {
        ReconnectSupervisor s = supervisorWithJitter(0.0);
        ReconnectParams off = new ReconnectParams(false, 30);
        for (long t = 0; t < 60 * 1000L; t += 50) {
            s.tick(t * MS, ConnectionState.DISCONNECTED, true, off, connect);
        }
        assertEquals(0, connects.get());
    }

    @Test
    void firstRetryWaitsTheBaseDelay() {
        ReconnectSupervisor s = supervisorWithJitter(1.0); // base delay = 1000ms exactly
        // First tick schedules; nothing fires before the delay elapses.
        assertFalse(s.tick(0, ConnectionState.DISCONNECTED, true, enabled, connect));
        assertFalse(s.tick(999 * MS, ConnectionState.DISCONNECTED, true, enabled, connect));
        assertEquals(0, connects.get());
        assertTrue(s.tick(1000 * MS, ConnectionState.DISCONNECTED, true, enabled, connect));
        assertEquals(1, connects.get());
    }

    @Test
    void backoffGrowsAcrossFailedAttemptsAndResetsOnConnect() {
        ReconnectSupervisor s = supervisorWithJitter(1.0);
        long now = 0;

        // Attempt 1 after ~1s.
        now = fireNextAttempt(s, now);
        assertEquals(1, connects.get());

        // A failed attempt returns to DISCONNECTED; the next wait is longer (~2s), not another 1s.
        assertFalse(s.tick(now + 1000 * MS, ConnectionState.DISCONNECTED, true, enabled, connect));
        assertEquals(1, connects.get(), "second retry must wait longer than the first");
        now = fireNextAttempt(s, now);
        assertEquals(2, connects.get());

        // A successful connect clears the backoff; a fresh drop starts from the base delay again.
        s.tick(now, ConnectionState.READY, true, enabled, connect);
        assertFalse(s.tick(now, ConnectionState.DISCONNECTED, true, enabled, connect));   // reschedule
        assertTrue(s.tick(now + 1000 * MS, ConnectionState.DISCONNECTED, true, enabled, connect));
        assertEquals(3, connects.get());
    }

    @Test
    void doesNotPileAttemptsWhileConnecting() {
        ReconnectSupervisor s = supervisorWithJitter(1.0);
        long now = fireNextAttempt(s, 0);
        assertEquals(1, connects.get());
        // While the attempt is in flight (CONNECTING/NEGOTIATING), no further connects are triggered.
        for (long t = 0; t < 60 * 1000L; t += 50) {
            s.tick(now + t * MS, ConnectionState.CONNECTING, true, enabled, connect);
            s.tick(now + t * MS, ConnectionState.NEGOTIATING, true, enabled, connect);
        }
        assertEquals(1, connects.get());
    }

    /** Advance from {@code startNs} until exactly one connect fires; returns the firing time. */
    private long fireNextAttempt(ReconnectSupervisor s, long startNs) {
        for (long t = startNs; t <= startNs + 120 * 1000L * MS; t += 50 * MS) {
            if (s.tick(t, ConnectionState.DISCONNECTED, true, enabled, connect)) {
                return t;
            }
        }
        throw new AssertionError("no reconnect fired");
    }
}
