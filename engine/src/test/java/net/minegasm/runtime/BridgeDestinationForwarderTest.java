package net.minegasm.runtime;

import net.minegasm.core.BodyRegion;
import net.minegasm.core.HapticRole;
import net.minegasm.core.LogicalDestination;
import net.minegasm.core.OutputClass;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Authoritative destination snapshots are sent on change and heartbeat, including retractions. */
class BridgeDestinationForwarderTest {

    private final List<ResolvedDestinationSnapshot> sent = new ArrayList<>();
    private boolean accept = true;
    private final BridgeDestinationForwarder forwarder = new BridgeDestinationForwarder(snapshot -> {
        if (accept) {
            sent.add(snapshot);
        }
        return accept;
    });

    private static ResolvedDestinationSnapshot snapshot(long generation, long nowNs,
                                                        HapticRole role, float level) {
        java.util.Map<LogicalDestination, Float> levels = new LinkedHashMap<>();
        if (level > 0f) {
            levels.put(new LogicalDestination(role, BodyRegion.WHOLE_BODY, OutputClass.STRENGTH), level);
        }
        return new ResolvedDestinationSnapshot(generation, nowNs, levels);
    }

    @Test
    void sendsTheFirstSnapshotThenSuppressesSteadyRepeats() {
        forwarder.forward(snapshot(1, 0, HapticRole.IMPACT, 0.8f));
        forwarder.forward(snapshot(2, 100, HapticRole.IMPACT, 0.8f));
        assertEquals(1, sent.size(), "a steady snapshot is sent once, not every cycle");
        assertEquals(0.8f, sent.get(0).levels().values().iterator().next(), 1e-6f);
    }

    @Test
    void resendsWhenALevelChangesPastTheEpsilon() {
        forwarder.forward(snapshot(1, 0, HapticRole.IMPACT, 0.8f));
        forwarder.forward(snapshot(2, 100, HapticRole.IMPACT, 0.5f));
        assertEquals(2, sent.size());
        assertEquals(0.5f, sent.get(1).levels().values().iterator().next(), 1e-6f);
    }

    @Test
    void aVanishedDestinationIsSentAsZeroSoItRetracts() {
        forwarder.forward(snapshot(1, 0, HapticRole.IMPACT, 0.8f));
        forwarder.forward(snapshot(2, 100, HapticRole.IMPACT, 0f));
        assertEquals(2, sent.size(), "dropping to nothing still sends the retraction snapshot");
        assertTrue(sent.get(1).isAllZero());
    }

    @Test
    void resendsOnHeartbeatEvenWhenUnchanged() {
        forwarder.forward(snapshot(1, 0, HapticRole.IMPACT, 0.8f));
        forwarder.forward(snapshot(2, 5_000_000_000L, HapticRole.IMPACT, 0.8f));
        assertEquals(2, sent.size());
    }

    @Test
    void aDroppedSnapshotIsRetriedNotRemembered() {
        accept = false;
        forwarder.forward(snapshot(1, 0, HapticRole.IMPACT, 0.8f));
        assertTrue(sent.isEmpty());
        accept = true;
        forwarder.forward(snapshot(2, 100, HapticRole.IMPACT, 0.8f));
        assertEquals(1, sent.size(), "a dropped snapshot is resent once the link is back");
    }
}
