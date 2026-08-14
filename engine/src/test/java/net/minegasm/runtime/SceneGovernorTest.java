package net.minegasm.runtime;

import net.minegasm.core.CouplingMode;
import net.minegasm.core.BodyRegion;
import net.minegasm.core.GameEventKind;
import net.minegasm.core.HapticLayer;
import net.minegasm.core.HapticPrimitive;
import net.minegasm.core.HapticRole;
import net.minegasm.core.HapticRoute;
import net.minegasm.core.HapticScene;
import net.minegasm.core.OutputKind;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The central scene holder: coalescing, expiry, and the bounded overflow that moved off the old
 * ingress queue (ADR-018). Continuous scenes coalesce latest-wins and never drop; discrete scenes are
 * bounded with the expired-then-lowest-priority policy.
 */
class SceneGovernorTest {

    private static HapticScene discrete(String id, int priority, long createdNs, long expiresNs) {
        return new HapticScene(id, GameEventKind.ATTACK, priority, List.of(), createdNs, expiresNs, null);
    }

    private static HapticScene continuous(String key, long createdNs, long expiresNs) {
        return new HapticScene(key, GameEventKind.AMBIENT, 0, List.of(), createdNs, expiresNs, key);
    }

    @Test
    void snapshotReturnsHeldScenesAndExpiresStaleOnes() {
        SceneGovernor gov = new SceneGovernor();
        gov.submit(discrete("live", 10, 0, 2_000_000_000L), 0);
        gov.submit(discrete("stale", 10, 0, 100), 0);

        List<HapticScene> at1s = gov.snapshot(1_000_000_000L);

        assertEquals(1, at1s.size());
        assertEquals("live", at1s.get(0).sceneId());
    }

    @Test
    void continuousScenesCoalesceLatestWins() {
        SceneGovernor gov = new SceneGovernor();
        gov.submit(continuous("accumulation", 0, 1_000_000_000L), 0);
        gov.submit(continuous("accumulation", 50, 1_000_000_050L), 50);

        assertEquals(1, gov.activeSceneCount(), "same key coalesces to one held scene");
    }

    @Test
    void overflowDropsLowestPriorityDiscrete() {
        SceneGovernor gov = new SceneGovernor(2);
        gov.submit(discrete("low", 10, 0, 1_000_000_000L), 0);
        gov.submit(discrete("mid", 50, 0, 1_000_000_000L), 0);
        // Full: an incoming higher priority evicts the lowest (10).
        gov.submit(discrete("high", 90, 0, 1_000_000_000L), 0);

        List<HapticScene> held = gov.snapshot(0);
        assertEquals(2, held.size());
        assertTrue(held.stream().noneMatch(s -> s.sceneId().equals("low")));
        assertTrue(gov.droppedCount() >= 1);
    }

    @Test
    void expiredDiscreteEvictedBeforeLivePriority() {
        SceneGovernor gov = new SceneGovernor(2);
        gov.submit(discrete("expired", 90, 0, 100), 0);
        gov.submit(discrete("live", 20, 0, 10_000_000_000L), 0);
        // At now=1s the expired scene is dropped to make room, so even a low-priority incoming is kept.
        gov.submit(discrete("new", 5, 1_000_000_000L, 10_000_000_000L), 1_000_000_000L);

        List<HapticScene> held = gov.snapshot(1_000_000_000L);
        assertTrue(held.stream().noneMatch(s -> s.sceneId().equals("expired")));
        assertTrue(held.stream().anyMatch(s -> s.sceneId().equals("new")));
    }

    @Test
    void lowestPriorityIncomingRejectedWhenFullOfHigher() {
        SceneGovernor gov = new SceneGovernor(1);
        gov.submit(discrete("high", 90, 0, 1_000_000_000L), 0);
        gov.submit(discrete("low", 5, 0, 1_000_000_000L), 0);

        List<HapticScene> held = gov.snapshot(0);
        assertEquals(1, held.size());
        assertEquals("high", held.get(0).sceneId(), "a weaker incoming cannot displace a stronger scene");
        assertTrue(gov.droppedCount() >= 1);
    }

    @Test
    void clearDropsEverything() {
        SceneGovernor gov = new SceneGovernor();
        gov.submit(discrete("a", 10, 0, 1_000_000_000L), 0);
        gov.submit(continuous("accumulation", 0, 1_000_000_000L), 0);
        assertFalse(gov.isEmpty());

        gov.clear();

        assertTrue(gov.isEmpty());
        assertTrue(gov.snapshot(0).isEmpty());
    }

    // --- Central fatigue (ADR-018): decayed, accounted, and baked into the scene before fan-out. ---

    private static final long SECOND = 1_000_000_000L;

    /** A steady continuous scene on the given role, held long enough to accrue fatigue load. */
    private static HapticScene sustained(String key, HapticRole role, float level) {
        HapticPrimitive.Hold hold = new HapticPrimitive.Hold(level, 600_000, 0, 0);
        HapticLayer layer = new HapticLayer("l", role, hold, HapticRoute.buzzAll(),
                CouplingMode.MAX, 0, 0, Long.MAX_VALUE / 4, key);
        return new HapticScene(key, GameEventKind.AMBIENT, 0, Collections.singletonList(layer),
                0, Long.MAX_VALUE / 4, key);
    }

    private static float bakedLevel(List<HapticScene> governed, String key) {
        for (HapticScene s : governed) {
            if (s.sceneId().equals(key)) {
                return s.layers().get(0).primitive().level();
            }
        }
        throw new AssertionError("scene not found: " + key);
    }

    private static float governFor20s(SceneGovernor gov, String key, boolean fatigueOn,
                                      boolean accountLoad) {
        float level = 1f;
        for (long t = 0; t <= 20 * SECOND; t += SECOND) {
            level = bakedLevel(gov.govern(t, fatigueOn, accountLoad), key);
        }
        return level;
    }

    @Test
    void budgetedRoleAttenuatesUnderSustainedLoad() {
        // The governor is device-neutral: it accrues from the authored scene level whenever the caller
        // asks it to (accountLoad=true). The worker is what gates that on a device being connected
        // (ADR-018); here we drive the governor directly to exercise the attenuation itself.
        SceneGovernor gov = new SceneGovernor();
        gov.submit(sustained("tex", HapticRole.TEXTURE, 1.0f), 0);
        assertTrue(governFor20s(gov, "tex", true, true) < 0.9f,
                "a TEXTURE scene sustained past its budget is attenuated in the scene it hands out");
    }

    @Test
    void unbudgetedRoleNeverAttenuates() {
        SceneGovernor gov = new SceneGovernor();
        gov.submit(sustained("imp", HapticRole.IMPACT, 1.0f), 0);
        assertEquals(1.0f, governFor20s(gov, "imp", true, true), 1e-6,
                "IMPACT has no fatigue budget, so it is never attenuated");
    }

    @Test
    void protectionOffPassesLevelsThroughButStillAccounts() {
        SceneGovernor gov = new SceneGovernor();
        gov.submit(sustained("tex", HapticRole.TEXTURE, 1.0f), 0);
        // Protection off: the same sustained load never scales the level down.
        assertEquals(1.0f, governFor20s(gov, "tex", false, true), 1e-6);
        // But the load was accounted, so turning protection on immediately reflects the history.
        assertTrue(bakedLevel(gov.govern(21 * SECOND, true, true), "tex") < 1.0f,
                "load accrued while protection was off attenuates as soon as it is enabled");
    }

    @Test
    void loadNotAccountedWhenOutputInactive() {
        SceneGovernor gov = new SceneGovernor();
        gov.submit(sustained("tex", HapticRole.TEXTURE, 1.0f), 0);
        assertEquals(1.0f, governFor20s(gov, "tex", true, false), 1e-6,
                "with output suppressed nothing is accounted, so nothing is attenuated");
    }

    // --- Central exclusivity resolution (P1-3): resolved once, so every backend sees the same set. ---

    /** A continuous scene carrying one layer with an explicit role, coupling, and priority. */
    private static HapticScene layerScene(String key, HapticRole role, CouplingMode coupling,
                                          int priority, float level) {
        HapticLayer layer = new HapticLayer("l", role, new HapticPrimitive.Hold(level, 600_000, 0, 0),
                HapticRoute.buzzAll(), coupling, priority, 0, Long.MAX_VALUE / 4, key);
        return new HapticScene(key, GameEventKind.AMBIENT, priority, Collections.singletonList(layer),
                0, Long.MAX_VALUE / 4, key);
    }

    private static boolean hasScene(List<HapticScene> governed, String key) {
        return governed.stream().anyMatch(s -> s.sceneId().equals(key));
    }

    @Test
    void higherPriorityExclusiveSuppressesLowerSameRole() {
        SceneGovernor gov = new SceneGovernor();
        gov.submit(layerScene("loud", HapticRole.AMBIENT, CouplingMode.EXCLUSIVE, 100, 0.9f), 0);
        gov.submit(layerScene("quiet", HapticRole.AMBIENT, CouplingMode.MAX, 10, 0.5f), 0);

        List<HapticScene> governed = gov.govern(SECOND, false, false);

        assertTrue(hasScene(governed, "loud"), "the exclusive layer survives");
        assertFalse(hasScene(governed, "quiet"),
                "a strictly lower-priority same-role layer is dropped centrally, for every backend");
    }

    @Test
    void exclusivityDoesNotCrossRoles() {
        SceneGovernor gov = new SceneGovernor();
        gov.submit(layerScene("warn", HapticRole.WARNING, CouplingMode.EXCLUSIVE, 100, 0.9f), 0);
        gov.submit(layerScene("amb", HapticRole.AMBIENT, CouplingMode.MAX, 10, 0.5f), 0);

        List<HapticScene> governed = gov.govern(SECOND, false, false);

        assertTrue(hasScene(governed, "warn"));
        assertTrue(hasScene(governed, "amb"), "a different role is untouched by another role's exclusive");
    }

    @Test
    void inactiveExclusiveCannotSuppressCurrentOutput() {
        SceneGovernor gov = new SceneGovernor();
        HapticLayer delayed = new HapticLayer("delayed", HapticRole.AMBIENT,
                new HapticPrimitive.Hold(0.8f, 1_000, 0, 0), HapticRoute.buzzAll(),
                CouplingMode.EXCLUSIVE, 100, 500 * 1_000_000L, 1_000 * 1_000_000L, "delayed");
        HapticLayer current = new HapticLayer("current", HapticRole.AMBIENT,
                new HapticPrimitive.Hold(0.4f, 1_000, 0, 0), HapticRoute.buzzAll(),
                CouplingMode.MAX, 10, 0, 1_000 * 1_000_000L, "current");
        gov.submit(new HapticScene("delayed", GameEventKind.AMBIENT, 100,
                Collections.singletonList(delayed), 0, 2 * SECOND, "delayed"), 0);
        gov.submit(new HapticScene("current", GameEventKind.AMBIENT, 10,
                Collections.singletonList(current), 0, 2 * SECOND, "current"), 0);

        List<HapticScene> beforeStart = gov.govern(100 * 1_000_000L, false, false);

        assertFalse(hasScene(beforeStart, "delayed"));
        assertTrue(hasScene(beforeStart, "current"));
    }

    @Test
    void exclusiveMotionDoesNotSuppressStrengthAtTheSameRoleAndRegion() {
        SceneGovernor gov = new SceneGovernor();
        HapticRoute motion = new HapticRoute(EnumSet.of(OutputKind.POSITION),
                Collections.emptySet(), Collections.emptySet(), Collections.emptySet(), null);
        HapticLayer exclusiveMotion = new HapticLayer("motion", HapticRole.AMBIENT,
                new HapticPrimitive.Hold(0.8f, 1_000, 0, 0), motion,
                CouplingMode.EXCLUSIVE, 100, 0, SECOND, "motion");
        HapticLayer strength = new HapticLayer("strength", HapticRole.AMBIENT,
                new HapticPrimitive.Hold(0.4f, 1_000, 0, 0), HapticRoute.buzzAll(),
                CouplingMode.MAX, 10, 0, SECOND, "strength");
        gov.submit(new HapticScene("motion", GameEventKind.AMBIENT, 100,
                Collections.singletonList(exclusiveMotion), 0, 2 * SECOND, "motion"), 0);
        gov.submit(new HapticScene("strength", GameEventKind.AMBIENT, 10,
                Collections.singletonList(strength), 0, 2 * SECOND, "strength"), 0);

        GovernedOutput output = gov.resolve(100 * 1_000_000L, false, false);

        assertTrue(hasScene(output.scenes(), "motion"));
        assertTrue(hasScene(output.scenes(), "strength"));
        assertEquals(2, output.destinations().levels().size(),
                "motion and strength remain independent logical destinations");
    }

    @Test
    void fatigueAccountsOnlySurvivingOutputAndKeepsRegionsIndependent() {
        SceneGovernor gov = new SceneGovernor();
        HapticLayer winner = new HapticLayer("winner", HapticRole.AMBIENT,
                new HapticPrimitive.Hold(0.3f, 60_000, 0, 0), HapticRoute.buzzAll(),
                CouplingMode.EXCLUSIVE, 100, 0, 60 * SECOND, "winner", BodyRegion.GENITAL);
        HapticLayer suppressed = new HapticLayer("suppressed", HapticRole.AMBIENT,
                new HapticPrimitive.Hold(0.9f, 60_000, 0, 0), HapticRoute.buzzAll(),
                CouplingMode.MAX, 10, 0, 60 * SECOND, "suppressed", BodyRegion.GENITAL);
        gov.submit(new HapticScene("winner", GameEventKind.AMBIENT, 100,
                Collections.singletonList(winner), 0, 60 * SECOND, "winner"), 0);
        gov.submit(new HapticScene("suppressed", GameEventKind.AMBIENT, 10,
                Collections.singletonList(suppressed), 0, 60 * SECOND, "suppressed"), 0);
        gov.resolve(0, false, true);
        gov.resolve(SECOND, false, true);

        assertEquals(0.3, gov.fatigueLoadFor(HapticRole.AMBIENT, BodyRegion.GENITAL), 1e-6,
                "the suppressed 0.9 layer never enters the fatigue budget");
        assertEquals(0.0, gov.fatigueLoadFor(HapticRole.AMBIENT, BodyRegion.NIPPLE), 1e-6,
                "a separate body region has an independent budget");
    }
}
