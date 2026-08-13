package net.minegasm.runtime;

import net.minegasm.time.Clock;

/**
 * Lightweight safety watchdog (brief §12.4). If the worker's last healthy cycle is older than the
 * threshold, it requests a best-effort out-of-band emergency stop, so a stalled worker or provider fails
 * toward stopped output rather than leaving a device running. It uses the worker's out-of-band path
 * rather than the synchronized stop precisely because a stall may be a backend hung inside a cycle, which
 * would hold the cycle monitor a synchronized stop needs.
 */
public final class Watchdog {

    private final HapticWorker worker;
    private final Clock clock;
    private final long thresholdNs;
    private long lastFiredNs = Long.MIN_VALUE / 2; // avoids overflow; first fire is never suppressed

    public Watchdog(HapticWorker worker, Clock clock, long thresholdMs) {
        this.worker = worker;
        this.clock = clock;
        this.thresholdNs = thresholdMs * 1_000_000L;
    }

    /**
     * Check once; returns true if a stop was triggered. Intended to be polled periodically (the
     * client tick polls it, an observer independent of the worker thread it watches). Refires at
     * most once per threshold window so a sustained stall does not spam {@code StopCmd}.
     */
    public boolean check() {
        long last = worker.lastHealthyCycleNs();
        if (last == 0) {
            return false; // worker not yet running
        }
        long now = clock.nanoTime();
        if (now - last > thresholdNs) {
            if (now - lastFiredNs > thresholdNs) {
                lastFiredNs = now;
                worker.emergencyStop(StopReason.WATCHDOG);
                return true;
            }
            return false;
        }
        // Healthy again: if a watchdog stop is latched, let it recover now that cycles are flowing.
        worker.recoverFromWatchdogStop();
        return false;
    }
}
