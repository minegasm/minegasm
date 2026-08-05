package net.minegasm.runtime;

import net.minegasm.buttplug.ConnectionState;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScanSupervisorTest {

    private static final long MS = 1_000_000L;
    private static final long WINDOW_MS = 5_000;

    private final AtomicInteger stops = new AtomicInteger();
    private final Runnable stop = stops::incrementAndGet;

    @Test
    void neverStopsWhenNotScanning() {
        ScanSupervisor s = new ScanSupervisor(WINDOW_MS);
        for (long t = 0; t < 60 * 1000L; t += 50) {
            s.tick(t * MS, ConnectionState.READY, stop);
            s.tick(t * MS, ConnectionState.CONNECTED_NO_DEVICES, stop);
            s.tick(t * MS, ConnectionState.DISCONNECTED, stop);
        }
        assertEquals(0, stops.get());
    }

    @Test
    void stopsOnceAfterTheWindow() {
        ScanSupervisor s = new ScanSupervisor(WINDOW_MS);
        assertFalse(s.tick(0, ConnectionState.SCANNING, stop));         // arm
        assertFalse(s.tick(4_999 * MS, ConnectionState.SCANNING, stop));
        assertEquals(0, stops.get());
        assertTrue(s.tick(5_000 * MS, ConnectionState.SCANNING, stop)); // fire at the deadline
        assertEquals(1, stops.get());
        // Still SCANNING during the async gap before the stop lands: must not re-issue.
        for (long t = 5_050; t < 60_000; t += 50) {
            s.tick(t * MS, ConnectionState.SCANNING, stop);
        }
        assertEquals(1, stops.get());
    }

    @Test
    void reArmsAfterScanLeavesAndReenters() {
        ScanSupervisor s = new ScanSupervisor(WINDOW_MS);
        s.tick(0, ConnectionState.SCANNING, stop);
        assertTrue(s.tick(5_000 * MS, ConnectionState.SCANNING, stop));
        assertEquals(1, stops.get());

        // Scan ends, then the user starts a fresh scan: the window applies again.
        s.tick(6_000 * MS, ConnectionState.READY, stop);
        s.tick(7_000 * MS, ConnectionState.SCANNING, stop);            // re-arm
        assertFalse(s.tick(11_999 * MS, ConnectionState.SCANNING, stop));
        assertTrue(s.tick(12_000 * MS, ConnectionState.SCANNING, stop));
        assertEquals(2, stops.get());
    }
}
