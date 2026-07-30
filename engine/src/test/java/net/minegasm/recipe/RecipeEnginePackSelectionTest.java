package net.minegasm.recipe;

import net.minegasm.config.HapticConfig;
import net.minegasm.config.MinegasmMode;
import net.minegasm.config.RuntimeConfig;
import net.minegasm.core.CouplingMode;
import net.minegasm.core.DeliveryMode;
import net.minegasm.core.GameEventKind;
import net.minegasm.core.HapticIntent;
import net.minegasm.core.HapticPrimitive;
import net.minegasm.core.HapticRole;
import net.minegasm.core.HapticScene;
import net.minegasm.pack.LayerTemplate;
import net.minegasm.pack.PackRegistry;
import net.minegasm.pack.PackTrigger;
import net.minegasm.pack.ScenePack;
import net.minegasm.pack.SceneTemplate;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pack selection routes through the raw name, not the enum. A file-pack name that
 * {@code RecipePackId.fromString} would collapse to BALANCED must select the file pack (ADR-017).
 */
class RecipeEnginePackSelectionTest {

    @Test
    void filePackNameSelectsTheFilePackNotTheBuiltInFallback() {
        PackRegistry registry = new PackRegistry();
        registry.register(filePack("my.pack"));
        RecipeEngine engine = new RecipeEngine(registry);

        HapticScene scene = engine.resolve(intent(GameEventKind.HURT), configWithPack("my.pack"))
                .orElseThrow(AssertionError::new);

        // The file pack namespaces its scene id by pack id; a built-in would not. If selection read the
        // enum, "my.pack" would collapse to BALANCED and this id would differ.
        assertEquals("my.pack:HURT", scene.sceneId());
    }

    @Test
    void unknownPackNameWithNoMatchFallsBackToBuiltIn() {
        RecipeEngine engine = new RecipeEngine(); // empty registry
        HapticScene scene = engine.resolve(intent(GameEventKind.HURT), configWithPack("does.not.exist"))
                .orElseThrow(AssertionError::new);
        assertFalse(scene.sceneId().startsWith("does.not.exist"),
                "no such pack loaded, so it falls back to a built-in");
    }

    @Test
    void strokeDoesNotFireUnderAFilePack() {
        PackRegistry registry = new PackRegistry();
        registry.register(filePack("my.pack"));
        RecipeEngine engine = new RecipeEngine(registry);
        assertFalse(engine.tickStroke(configWithPack("my.pack"), 1_000L).isPresent(),
                "the Balanced-only stroke must not fire under a file pack");
    }

    private static ScenePack filePack(String id) {
        LayerTemplate layer = new LayerTemplate("l", HapticRole.IMPACT,
                new HapticPrimitive.Impulse(0.5f, 200, 10, 40), Collections.emptySet(),
                DeliveryMode.ALL_COMPATIBLE, CouplingMode.MAX, 0, 0, 250, null);
        SceneTemplate scene = new SceneTemplate(100, 250, null, Collections.singletonList(layer));
        return new ScenePack(ScenePack.SCHEMA_VERSION, id, "", "", "",
                Collections.singletonList(new PackTrigger(GameEventKind.HURT, scene)));
    }

    private static HapticIntent intent(GameEventKind kind) {
        return new HapticIntent(kind, kind.key(), 1f, 1f, null, null, Collections.<String>emptySet(),
                1L, 1_000_000_000L);
    }

    private static RuntimeConfig configWithPack(String recipePackName) {
        HapticConfig d = HapticConfig.defaults();
        HapticConfig.Profile profile = new HapticConfig.Profile(recipePackName,
                MinegasmMode.REACTION.name());
        HapticConfig.Global g = new HapticConfig.Global(true, 1.0, 0.0, false, "STOP", true, "",
                50, 2_000, 100, 10_000);
        HapticConfig cfg = new HapticConfig(1, profile, g, d.buttplug(), d.events(), d.outputPolicy(),
                d.devices(), d.positionCalibrations(), d.accumulation(), d.customIntensity(),
                d.bridge());
        return RuntimeConfig.of(cfg);
    }
}
