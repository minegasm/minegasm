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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The bridge coalescing payoff (ADR-018): a steady effect is forwarded once, not once per worker cycle;
 * a real change or a TTL re-arm forwards again; and stop forgets tracking. Uses hand-built governed
 * scenes so it tests the forwarding rule, not fatigue.
 */
class GovernedSceneForwarderTest {

    private static final long MS = 1_000_000L;

    /** A continuous scene with a 500 ms TTL from {@code createdNs} and the given steady amplitude. */
    private static HapticScene continuous(String key, float level, long createdNs) {
        HapticPrimitive.Hold hold = new HapticPrimitive.Hold(level, 600_000, 0, 0);
        HapticLayer layer = new HapticLayer("l", HapticRole.IMPACT, hold, HapticRoute.buzzAll(),
                CouplingMode.MAX, 0, 0, Long.MAX_VALUE / 4, key);
        return new HapticScene(key, GameEventKind.AMBIENT, 0, Collections.singletonList(layer),
                createdNs, createdNs + 500 * MS, key);
    }

    private static HapticScene discrete(String id, long createdNs) {
        HapticLayer layer = new HapticLayer("l", HapticRole.IMPACT,
                new HapticPrimitive.Impulse(0.8f, 250, 8, 40), HapticRoute.buzzAll(),
                CouplingMode.MAX, 0, 0, 300 * MS, null);
        return new HapticScene(id, GameEventKind.HURT, 0, Collections.singletonList(layer),
                createdNs, createdNs + 300 * MS, null);
    }

    @Test
    void steadyContinuousForwardsOnceWithinTheReArmWindow() {
        List<HapticScene> sent = new ArrayList<>();
        GovernedSceneForwarder fwd = new GovernedSceneForwarder(sent::add);
        // Same steady effect re-submitted every 15 ms cycle for 150 ms (< the 250 ms re-arm).
        for (long t = 0; t <= 150 * MS; t += 15 * MS) {
            fwd.forward(Collections.singletonList(continuous("accumulation", 0.5f, t)), t);
        }
        assertEquals(1, sent.size(), "a steady continuous scene is forwarded once, not every cycle");
    }

    @Test
    void steadyContinuousReForwardsPastHalfTheTtl() {
        List<HapticScene> sent = new ArrayList<>();
        GovernedSceneForwarder fwd = new GovernedSceneForwarder(sent::add);
        fwd.forward(Collections.singletonList(continuous("accumulation", 0.5f, 0)), 0);
        fwd.forward(Collections.singletonList(continuous("accumulation", 0.5f, 100 * MS)), 100 * MS);
        fwd.forward(Collections.singletonList(continuous("accumulation", 0.5f, 300 * MS)), 300 * MS);
        assertEquals(2, sent.size(),
                "past half the TTL a steady scene is re-sent so the adapter TTL never lapses");
    }

    @Test
    void amplitudeChangeBeyondEpsilonForwards() {
        List<HapticScene> sent = new ArrayList<>();
        GovernedSceneForwarder fwd = new GovernedSceneForwarder(sent::add);
        fwd.forward(Collections.singletonList(continuous("accumulation", 0.50f, 0)), 0);
        fwd.forward(Collections.singletonList(continuous("accumulation", 0.505f, 15 * MS)), 15 * MS);
        fwd.forward(Collections.singletonList(continuous("accumulation", 0.70f, 30 * MS)), 30 * MS);
        assertEquals(2, sent.size(),
                "a sub-epsilon drift is suppressed, a real level change is forwarded");
    }

    @Test
    void discreteForwardsOncePerInstance() {
        List<HapticScene> sent = new ArrayList<>();
        GovernedSceneForwarder fwd = new GovernedSceneForwarder(sent::add);
        // Same discrete instance seen on several cycles (it lives in the store for its TTL).
        fwd.forward(Collections.singletonList(discrete("hurt", 0)), 0);
        fwd.forward(Collections.singletonList(discrete("hurt", 0)), 15 * MS);
        assertEquals(1, sent.size(), "one discrete instance is forwarded once");
        // A new instance (same id, later creation) is a distinct hit and forwards again.
        fwd.forward(Collections.singletonList(discrete("hurt", 500 * MS)), 500 * MS);
        assertEquals(2, sent.size(), "a new instance of the same id forwards again");
    }

    @Test
    void resetForgetsTrackingSoTheNextSceneForwardsAgain() {
        List<HapticScene> sent = new ArrayList<>();
        GovernedSceneForwarder fwd = new GovernedSceneForwarder(sent::add);
        fwd.forward(Collections.singletonList(continuous("accumulation", 0.5f, 0)), 0);
        assertEquals(1, sent.size());

        fwd.reset();
        fwd.forward(Collections.singletonList(continuous("accumulation", 0.5f, 15 * MS)), 15 * MS);
        assertEquals(2, sent.size(), "after reset the same scene is treated as new and forwarded");
    }
}
