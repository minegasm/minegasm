package net.minegasm.runtime;

import net.minegasm.buttplug.ConnectionState;

/**
 * Bounds how long the provider sits in {@code SCANNING} (brief §13.1). A Buttplug server only leaves the
 * scanning state when it emits {@code ScanningFinished}, which some servers never send if they scan
 * continuously or find nothing, so the state can stick indefinitely. Polled once per client tick with a
 * monotonic {@code nowNs} (the caller's injected {@link net.minegasm.time.Clock}); after the window it
 * calls {@code stopScanning} once, and both providers force their state back to a connected state when a
 * scan stops, so the stuck state always clears.
 *
 * <p>Single-fire: it arms on entering {@code SCANNING}, stops once at the deadline, and disarms until the
 * state leaves and re-enters {@code SCANNING}, so it never re-issues the stop during the async gap before
 * the state actually changes.
 */
public final class ScanSupervisor {

    /** How long a scan may run before it is stopped automatically. */
    static final long SCAN_WINDOW_MS = 10_000;

    private final long windowNs;

    private boolean armed;
    private boolean fired;
    private long deadlineNs;

    public ScanSupervisor() {
        this(SCAN_WINDOW_MS);
    }

    ScanSupervisor(long windowMs) {
        this.windowNs = windowMs * 1_000_000L;
    }

    /**
     * Advance the scan-timeout state machine one tick. Returns {@code true} if a stop was triggered this
     * tick (the {@code stopScanning} action was run).
     */
    public boolean tick(long nowNs, ConnectionState state, Runnable stopScanning) {
        if (state != ConnectionState.SCANNING) {
            armed = false;
            fired = false;
            return false;
        }
        if (fired) {
            // Already stopped this scan; wait out the async gap until the state actually leaves SCANNING.
            return false;
        }
        if (!armed) {
            armed = true;
            deadlineNs = nowNs + windowNs;
            return false;
        }
        if (nowNs < deadlineNs) {
            return false;
        }
        fired = true;
        stopScanning.run();
        return true;
    }
}
