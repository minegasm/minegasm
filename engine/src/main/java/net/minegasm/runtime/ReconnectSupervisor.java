package net.minegasm.runtime;

import net.minegasm.buttplug.ConnectionState;
import net.minegasm.config.ReconnectParams;

import java.util.Random;

/**
 * Drives auto-reconnect (brief §13.2). Polled once per client tick with the current connection state
 * and the reconnect policy; when a connection is wanted but the provider is disconnected, it schedules
 * retries with bounded exponential backoff and jitter. Holds no threads and no clock of its own: the
 * caller passes a monotonic {@code nowNs} (its injected {@link net.minegasm.time.Clock}), which keeps
 * the backoff deterministic under a fake clock in tests.
 *
 * <p>The "wanted" signal is the client's desired-connected latch, not {@code autoConnect}: a manual
 * disconnect clears it so reconnect never fights the user, and a successful connect (auto or manual)
 * sets it. Whether retries happen at all is governed by {@link ReconnectParams#enabled()}.
 */
public final class ReconnectSupervisor {

    private static final long BASE_DELAY_MS = 1_000;
    /** Cap the backoff shift so {@code BASE_DELAY_MS << attempt} can never overflow. */
    private static final int MAX_SHIFT = 20;

    private final Random jitter;

    private boolean scheduled;
    private int attempt;
    private long nextAttemptNs;

    public ReconnectSupervisor() {
        this(new Random());
    }

    ReconnectSupervisor(Random jitter) {
        this.jitter = jitter;
    }

    /**
     * Advance the reconnect state machine one tick. Returns {@code true} if a connect attempt was
     * triggered this tick (the {@code connect} action was run).
     */
    public boolean tick(long nowNs, ConnectionState state, boolean desiredConnected,
                        ReconnectParams params, Runnable connect) {
        if (!desiredConnected || isConnected(state)) {
            // Not wanted, or already connected: clear the backoff so the next drop starts fresh.
            reset();
            return false;
        }
        if (state != ConnectionState.DISCONNECTED) {
            // CONNECTING/NEGOTIATING/STOPPING: an attempt is in flight; wait rather than pile on.
            return false;
        }
        if (!params.enabled()) {
            return false;
        }
        if (!scheduled) {
            scheduled = true;
            nextAttemptNs = nowNs + delayNs(attempt, params);
            return false;
        }
        if (nowNs < nextAttemptNs) {
            return false;
        }
        attempt++;
        scheduled = false; // the next tick reschedules against the grown attempt count
        connect.run();
        return true;
    }

    private static boolean isConnected(ConnectionState state) {
        switch (state) {
            case CONNECTED_NO_DEVICES:
            case SCANNING:
            case READY:
                return true;
            default:
                return false;
        }
    }

    private void reset() {
        scheduled = false;
        attempt = 0;
        nextAttemptNs = 0L;
    }

    /** Full-jitter backoff: a random delay in {@code [base/2, base]} where base doubles per attempt up
     *  to the configured cap. */
    private long delayNs(int attempt, ReconnectParams params) {
        long capMs = params.maxDelaySeconds() * 1_000L;
        long baseMs = Math.min(capMs, BASE_DELAY_MS << Math.min(attempt, MAX_SHIFT));
        long half = baseMs / 2;
        long jitterMs = half + (long) (this.jitter.nextDouble() * half);
        return jitterMs * 1_000_000L;
    }
}
