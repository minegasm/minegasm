package net.minegasm.runtime;

import net.minegasm.core.LogicalDestination;

import java.util.Map;
import java.util.function.Predicate;

/**
 * Change-driven delivery of the central authoritative destination snapshot. Missing destinations are
 * zero, so replacement and removal are equally meaningful. A steady active state is periodically sent
 * again to refresh the adapter TTL; idle zero is silent.
 */
public final class BridgeDestinationForwarder {

    private static final float LEVEL_EPSILON = 0.02f;
    private static final long REARM_INTERVAL_NS = 2_000_000_000L;

    private final Predicate<ResolvedDestinationSnapshot> sink;
    private ResolvedDestinationSnapshot lastSent = empty(0L, 0L);
    private long rearmAtNs;

    public BridgeDestinationForwarder(Predicate<ResolvedDestinationSnapshot> sink) {
        this.sink = sink;
    }

    public void forward(ResolvedDestinationSnapshot resolved) {
        long nowNs = resolved.sampledAtNs();
        boolean changed = changed(resolved, lastSent);
        boolean heartbeat = !changed && !lastSent.isAllZero() && nowNs >= rearmAtNs;
        if ((changed || heartbeat) && sink.test(resolved)) {
            lastSent = resolved;
            rearmAtNs = nowNs + REARM_INTERVAL_NS;
        }
    }

    public void reset() {
        lastSent = empty(0L, 0L);
        rearmAtNs = 0L;
    }

    private static ResolvedDestinationSnapshot empty(long generation, long nowNs) {
        return new ResolvedDestinationSnapshot(generation, nowNs,
                java.util.Collections.<LogicalDestination, Float>emptyMap());
    }

    private static boolean changed(ResolvedDestinationSnapshot a, ResolvedDestinationSnapshot b) {
        for (Map.Entry<LogicalDestination, Float> entry : a.levels().entrySet()) {
            float previous = b.level(entry.getKey());
            if (previous <= 0f || Math.abs(entry.getValue() - previous) > LEVEL_EPSILON) {
                return true;
            }
        }
        for (LogicalDestination destination : b.levels().keySet()) {
            if (a.level(destination) <= 0f) {
                return true;
            }
        }
        return false;
    }
}
