package net.minegasm.recipe;

import net.minegasm.config.MinegasmMode;
import net.minegasm.config.RecipePackId;
import net.minegasm.config.RuntimeConfig;
import net.minegasm.core.GameEventKind;
import net.minegasm.core.HapticIntent;
import net.minegasm.core.HapticScene;
import net.minegasm.testsupport.Configs;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecipeEngineTest {

    private final RecipeEngine engine = new RecipeEngine();

    private HapticIntent intent(GameEventKind kind, float strength) {
        return new HapticIntent(kind, kind.key(), strength, strength, null, null, Set.of(), 1L, 0L);
    }

    @Test
    void masterDisabledProducesNothing() {
        RuntimeConfig off = Configs.disabled();
        assertTrue(engine.resolve(intent(GameEventKind.HURT, 1f), off).isEmpty());
    }

    @Test
    void hurtDisabledInNormalMode() {
        RuntimeConfig normal = Configs.enabled(MinegasmMode.NORMAL, RecipePackId.BALANCED);
        assertTrue(engine.resolve(intent(GameEventKind.HURT, 1f), normal).isEmpty(),
                "hurt has base 0 in NORMAL and must not fire");
        assertTrue(engine.resolve(intent(GameEventKind.ATTACK, 0.5f), normal).isPresent());
    }

    @Test
    void masochistFeelsHurtNotAttack() {
        RuntimeConfig maso = Configs.enabled(MinegasmMode.MASOCHIST, RecipePackId.BALANCED);
        assertTrue(engine.resolve(intent(GameEventKind.HURT, 1f), maso).isPresent());
        assertTrue(engine.resolve(intent(GameEventKind.ATTACK, 1f), maso).isEmpty());
    }

    @Test
    void balancedHurtScaledByStrength() {
        RuntimeConfig maso = Configs.enabled(MinegasmMode.MASOCHIST, RecipePackId.BALANCED);
        HapticScene weak = engine.resolve(intent(GameEventKind.HURT, 0.1f), maso).orElseThrow();
        HapticScene strong = engine.resolve(intent(GameEventKind.HURT, 1.0f), maso).orElseThrow();
        float weakLevel = weak.layers().get(0).primitive().level();
        float strongLevel = strong.layers().get(0).primitive().level();
        assertTrue(strongLevel > weakLevel, "stronger damage should feel stronger");
    }

    @Test
    void classicUsesFlatPlateauHold() {
        RuntimeConfig classic = Configs.enabled(MinegasmMode.NORMAL, RecipePackId.CLASSIC);
        Optional<HapticScene> scene = engine.resolve(intent(GameEventKind.ATTACK, 0.5f), classic);
        assertTrue(scene.isPresent());
        boolean hasHold = scene.get().layers().stream()
                .anyMatch(l -> l.primitive() instanceof net.minegasm.core.HapticPrimitive.Hold);
        assertTrue(hasHold, "Classic pack should render flat Hold plateaus");
    }

    @Test
    void classicSuppressesContinuousMining() {
        RuntimeConfig classic = Configs.enabled(MinegasmMode.NORMAL, RecipePackId.CLASSIC);
        assertTrue(engine.resolve(intent(GameEventKind.MINING_ACTIVE, 0.5f), classic).isEmpty(),
                "legacy had no continuous mining texture");
    }

    @Test
    void continuousMiningSceneCarriesKey() {
        RuntimeConfig balanced = Configs.enabled(MinegasmMode.HEDONIST, RecipePackId.BALANCED);
        HapticScene mining = engine.resolve(intent(GameEventKind.MINING_ACTIVE, 0.5f), balanced).orElseThrow();
        assertTrue(mining.isContinuous(), "mining should be a continuous, latest-wins scene");
    }

    @Test
    void customModeUsesConfiguredIntensity() {
        RuntimeConfig custom = Configs.enabled(MinegasmMode.CUSTOM, RecipePackId.BALANCED);
        // Legacy CUSTOM defaults enable attack (0.60) but leave harvest at 0.
        assertTrue(engine.resolve(intent(GameEventKind.ATTACK, 0.5f), custom).isPresent());
        assertTrue(engine.resolve(intent(GameEventKind.HARVEST, 0.5f), custom).isEmpty());
    }

    @Test
    void resolvedSceneUsesEventPriority() {
        RuntimeConfig maso = Configs.enabled(MinegasmMode.MASOCHIST, RecipePackId.BALANCED);
        HapticScene hurt = engine.resolve(intent(GameEventKind.HURT, 1f), maso).orElseThrow();
        assertEquals(net.minegasm.core.Priorities.HURT, hurt.priority());
        assertFalse(hurt.layers().isEmpty());
    }
}
