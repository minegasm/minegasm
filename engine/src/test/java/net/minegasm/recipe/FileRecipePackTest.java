package net.minegasm.recipe;

import net.minegasm.config.RuntimeConfig;
import net.minegasm.core.CouplingMode;
import net.minegasm.core.DeliveryMode;
import net.minegasm.core.GameEventKind;
import net.minegasm.core.HapticIntent;
import net.minegasm.core.HapticPrimitive;
import net.minegasm.core.HapticRole;
import net.minegasm.core.HapticScene;
import net.minegasm.pack.LayerTemplate;
import net.minegasm.pack.PackTrigger;
import net.minegasm.pack.ScenePack;
import net.minegasm.pack.SceneTemplate;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** A file pack scales amplitude by the user's volume, leaves feel and timing alone, and clamps. */
class FileRecipePackTest {

    private static final long NOW = 1_000_000_000L;

    @Test
    void scalesLevelByUserGainButLeavesCharacterAndTiming() {
        FileRecipePack pack = packWith(new HapticPrimitive.Texture(0.5f, 300, 0.3f, 0.4f, 0.2f));

        HapticScene scene = pack.resolve(ctx(GameEventKind.HURT, 0.5f)).orElseThrow(AssertionError::new);
        HapticPrimitive.Texture t = (HapticPrimitive.Texture) scene.layers().get(0).primitive();

        assertEquals(0.25f, t.level(), 1e-6f, "level scales by userGain");
        assertEquals(0.3f, t.grain(), 0f, "grain is feel, not amplitude");
        assertEquals(0.4f, t.density(), 0f, "density is feel, not amplitude");
        assertEquals(0.2f, t.irregularity(), 0f, "irregularity is feel, not amplitude");
        assertEquals(300, t.durationMs(), "timing is untouched");
    }

    @Test
    void scaledLevelClampsWhenGainExceedsOne() {
        FileRecipePack pack = packWith(new HapticPrimitive.Rumble(0.8f, 500, 0.6f, true));

        HapticScene scene = pack.resolve(ctx(GameEventKind.HURT, 2.0f)).orElseThrow(AssertionError::new);
        HapticPrimitive.Rumble r = (HapticPrimitive.Rumble) scene.layers().get(0).primitive();

        assertEquals(1.0f, r.level(), 0f, "level clamps to 1 when gain pushes it over");
        assertEquals(0.6f, r.roughness(), 0f, "roughness is feel, not amplitude");
    }

    @Test
    void resolveIsEmptyForAnEventThePackDoesNotCover() {
        FileRecipePack pack = packWith(new HapticPrimitive.Hold(0.5f, 200, 0, 0));
        assertFalse(pack.resolve(ctx(GameEventKind.ATTACK, 1.0f)).isPresent());
    }

    @Test
    void strengthWeightMakesAmplitudeFollowEventStrength() {
        // weight 1 is fully proportional to strength; at half strength, half level.
        FileRecipePack pack = packWith(new HapticPrimitive.Impulse(0.8f, 200, 10, 40), 1.0f);

        HapticScene scene = pack.resolve(ctx(GameEventKind.HURT, 1.0f, 0.5f))
                .orElseThrow(AssertionError::new);
        HapticPrimitive.Impulse i = (HapticPrimitive.Impulse) scene.layers().get(0).primitive();

        assertEquals(0.4f, i.level(), 1e-6f, "level follows strength when weight is 1");
    }

    private static FileRecipePack packWith(HapticPrimitive primitive) {
        return packWith(primitive, 0f);
    }

    private static FileRecipePack packWith(HapticPrimitive primitive, float strengthWeight) {
        LayerTemplate layer = new LayerTemplate("l", HapticRole.TEXTURE, primitive,
                Collections.emptySet(), DeliveryMode.ALL_COMPATIBLE, CouplingMode.MAX, 0, 0, 300, null,
                strengthWeight);
        SceneTemplate scene = new SceneTemplate(100, 300, null, Collections.singletonList(layer));
        return new FileRecipePack(new ScenePack(ScenePack.SCHEMA_VERSION, "my.pack", "", "", "",
                Collections.singletonList(new PackTrigger(GameEventKind.HURT, scene))));
    }

    private static RecipeContext ctx(GameEventKind kind, float userGain) {
        return ctx(kind, userGain, 1f);
    }

    private static RecipeContext ctx(GameEventKind kind, float userGain, float strength) {
        HapticIntent intent = new HapticIntent(kind, kind.key(), strength, strength, null, null,
                Collections.<String>emptySet(), 1L, NOW);
        return new RecipeContext(intent, 1f, userGain, RuntimeConfig.defaults(), NOW);
    }
}
