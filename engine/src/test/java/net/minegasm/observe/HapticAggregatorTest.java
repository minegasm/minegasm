package net.minegasm.observe;

import net.minegasm.config.MinegasmMode;
import net.minegasm.config.RecipePackId;
import net.minegasm.config.RuntimeConfig;
import net.minegasm.core.GameEventKind;
import net.minegasm.core.HapticIntent;
import net.minegasm.core.MaterialFeel;
import net.minegasm.core.RawGameEvent;
import net.minegasm.testsupport.Configs;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HapticAggregatorTest {

    private static final long MS = 1_000_000L;
    private final RuntimeConfig hedonist = Configs.enabled(MinegasmMode.HEDONIST, RecipePackId.BALANCED);

    private static StateTransitions damage(float delta) {
        return new StateTransitions(delta, 0, false, 0, false, false, false, false, false, false, false);
    }

    private static StateTransitions xp(int amount, boolean leveled) {
        return new StateTransitions(0f, amount, leveled, leveled ? 1 : 0, false, false, false,
                false, false, false, false);
    }

    private static StateTransitions none() {
        return new StateTransitions(0f, 0, false, 0, false, false, false, false, false, false, false);
    }

    private static ClientStateSnapshot healthy(long tick) {
        return new ClientStateSnapshot(20f, 0f, 20, 0, 0f, 0, false, Optional.empty(), 0f,
                Optional.empty(), MaterialFeel.UNKNOWN, 0f, false, false, false, false, false, true, tick);
    }

    private static ClientStateSnapshot mining(long tick) {
        return new ClientStateSnapshot(20f, 0f, 20, 0, 0f, 0, true, Optional.of("dim:1,2,3"), 0.5f,
                Optional.of("stone"), MaterialFeel.STONE_ORE, 0.5f, false, false, false, false, false,
                true, tick);
    }

    private Optional<HapticIntent> find(List<HapticIntent> intents, GameEventKind kind) {
        return intents.stream().filter(i -> i.kind() == kind).findFirst();
    }

    @Test
    void hurtMergesWithinWindow() {
        HapticAggregator agg = new HapticAggregator();
        HapticIntent first = find(agg.aggregate(List.of(), damage(-3f), healthy(1), hedonist, 0),
                GameEventKind.HURT).orElseThrow();
        HapticIntent second = find(agg.aggregate(List.of(), damage(-3f), healthy(2), hedonist, 50 * MS),
                GameEventKind.HURT).orElseThrow();
        // raw 0.3 each; merged = max + 0.35*min = 0.3 + 0.105 = 0.405.
        assertEquals(0.30f, first.strength(), 1e-3);
        assertEquals(0.405f, second.strength(), 1e-3);
    }

    @Test
    void hurtOutsideWindowNotMerged() {
        HapticAggregator agg = new HapticAggregator();
        agg.aggregate(List.of(), damage(-3f), healthy(1), hedonist, 0);
        HapticIntent second = find(agg.aggregate(List.of(), damage(-3f), healthy(2), hedonist, 300 * MS),
                GameEventKind.HURT).orElseThrow();
        assertEquals(0.30f, second.strength(), 1e-3);
    }

    @Test
    void respawnDoesNotProduceHurt() {
        HapticAggregator agg = new HapticAggregator();
        StateTransitions respawn = new StateTransitions(-20f, 0, false, 0, false, false, false,
                false, false, false, true);
        assertTrue(find(agg.aggregate(List.of(), respawn, healthy(1), hedonist, 0),
                GameEventKind.HURT).isEmpty());
    }

    @Test
    void xpOrbsCoalesceIntoOneIntent() {
        HapticAggregator agg = new HapticAggregator();
        assertTrue(find(agg.aggregate(List.of(), xp(5, false), healthy(1), hedonist, 0),
                GameEventKind.XP_GAIN).isEmpty(), "still coalescing");
        assertTrue(find(agg.aggregate(List.of(), xp(5, false), healthy(2), hedonist, 50 * MS),
                GameEventKind.XP_GAIN).isEmpty(), "still coalescing");
        assertTrue(find(agg.aggregate(List.of(), none(), healthy(3), hedonist, 200 * MS),
                GameEventKind.XP_GAIN).isPresent(), "window elapsed -> flush once");
    }

    @Test
    void levelUpFlushesXpImmediatelyWithTag() {
        HapticAggregator agg = new HapticAggregator();
        HapticIntent intent = find(agg.aggregate(List.of(), xp(7, true), healthy(1), hedonist, 0),
                GameEventKind.XP_GAIN).orElseThrow();
        assertTrue(intent.hasTag("levelup"));
    }

    @Test
    void miningEmitsContinuousIntentWithMaterial() {
        HapticAggregator agg = new HapticAggregator();
        HapticIntent intent = find(agg.aggregate(List.of(), none(), mining(1), hedonist, 0),
                GameEventKind.MINING_ACTIVE).orElseThrow();
        assertEquals(MaterialFeel.STONE_ORE, intent.material());
        assertTrue(intent.tags().stream().anyMatch(t -> t.startsWith("pos:")));
    }

    @Test
    void fishingBiteRefractorySuppressesRapidSecond() {
        HapticAggregator agg = new HapticAggregator();
        RawGameEvent a = new RawGameEvent(GameEventKind.FISHING_BITE, 1, 0, Map.of("dedupe", 1));
        RawGameEvent b = new RawGameEvent(GameEventKind.FISHING_BITE, 1, 0, Map.of("dedupe", 2));
        long count = agg.aggregate(List.of(a, b), none(), healthy(1), hedonist, 0).stream()
                .filter(i -> i.kind() == GameEventKind.FISHING_BITE).count();
        assertEquals(1, count, "one pulse per bite despite two events in the refractory window");
    }

    @Test
    void vitalityEdgeThenRepeatInterval() {
        HapticAggregator agg = new HapticAggregator();
        RuntimeConfig maso = Configs.enabled(MinegasmMode.MASOCHIST, RecipePackId.BALANCED);
        StateTransitions critical = new StateTransitions(0f, 0, false, 0, false, false, false,
                false, false, true, false);
        assertTrue(find(agg.aggregate(List.of(), critical, healthy(1), maso, 0),
                GameEventKind.VITALITY).isPresent(), "edge trigger");
        assertTrue(find(agg.aggregate(List.of(), critical, healthy(2), maso, 1_000 * MS),
                GameEventKind.VITALITY).isEmpty(), "within repeat interval");
        assertTrue(find(agg.aggregate(List.of(), critical, healthy(3), maso, 3_100 * MS),
                GameEventKind.VITALITY).isPresent(), "repeat interval elapsed");
    }
}
