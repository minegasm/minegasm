package net.minegasm.runtime;

import net.minegasm.core.CouplingMode;
import net.minegasm.core.GameEventKind;
import net.minegasm.core.HapticLayer;
import net.minegasm.core.HapticPrimitive;
import net.minegasm.core.HapticRole;
import net.minegasm.core.HapticRoute;
import net.minegasm.core.HapticScene;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The authoritative per-role bridge snapshot (second follow-up review P1-3): sent on change, on a
 * heartbeat, and never suppressed when a role drops to zero, so a vanished effect actually retracts.
 */
class BridgeRoleForwarderTest {

    private final List<EnumMap<HapticRole, Float>> sent = new ArrayList<>();
    private boolean accept = true;
    private final BridgeRoleForwarder forwarder = new BridgeRoleForwarder(m -> {
        if (accept) {
            sent.add(m);
        }
        return accept;
    });

    private static HapticScene scene(HapticRole role, float level) {
        HapticLayer layer = new HapticLayer("l", role, new HapticPrimitive.Hold(level, 600_000, 0, 0),
                HapticRoute.buzzAll(), CouplingMode.MAX, 0, 0, Long.MAX_VALUE / 4, "k");
        return new HapticScene("s", GameEventKind.AMBIENT, 0, Collections.singletonList(layer),
                0, Long.MAX_VALUE / 4, "k");
    }

    @Test
    void sendsTheFirstSnapshotThenSuppressesSteadyRepeats() {
        forwarder.forward(Collections.singletonList(scene(HapticRole.IMPACT, 0.8f)), 0);
        forwarder.forward(Collections.singletonList(scene(HapticRole.IMPACT, 0.8f)), 100);
        assertEquals(1, sent.size(), "a steady snapshot is sent once, not every cycle");
        assertEquals(0.8f, sent.get(0).get(HapticRole.IMPACT), 1e-6f);
    }

    @Test
    void resendsWhenALevelChangesPastTheEpsilon() {
        forwarder.forward(Collections.singletonList(scene(HapticRole.IMPACT, 0.8f)), 0);
        forwarder.forward(Collections.singletonList(scene(HapticRole.IMPACT, 0.5f)), 100);
        assertEquals(2, sent.size());
        assertEquals(0.5f, sent.get(1).get(HapticRole.IMPACT), 1e-6f);
    }

    @Test
    void aVanishedRoleIsSentAsZeroSoItRetracts() {
        forwarder.forward(Collections.singletonList(scene(HapticRole.IMPACT, 0.8f)), 0);
        forwarder.forward(Collections.emptyList(), 100); // the effect ended
        assertEquals(2, sent.size(), "dropping to nothing still sends: the retraction snapshot");
        assertEquals(0f, sent.get(1).get(HapticRole.IMPACT), 1e-6f);
    }

    @Test
    void resendsOnHeartbeatEvenWhenUnchanged() {
        forwarder.forward(Collections.singletonList(scene(HapticRole.IMPACT, 0.8f)), 0);
        // Well past the re-send interval with the same level: the snapshot refreshes so the TTL never lapses.
        forwarder.forward(Collections.singletonList(scene(HapticRole.IMPACT, 0.8f)), 5_000_000_000L);
        assertEquals(2, sent.size());
    }

    @Test
    void aDroppedSnapshotIsRetriedNotRemembered() {
        accept = false; // link is down: the sink rejects
        forwarder.forward(Collections.singletonList(scene(HapticRole.IMPACT, 0.8f)), 0);
        assertTrue(sent.isEmpty());
        accept = true; // link returns
        forwarder.forward(Collections.singletonList(scene(HapticRole.IMPACT, 0.8f)), 100);
        assertEquals(1, sent.size(), "a snapshot dropped while down is resent once the link is back");
    }
}
