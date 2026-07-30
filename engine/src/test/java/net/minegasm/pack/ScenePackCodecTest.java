package net.minegasm.pack;

import net.minegasm.core.CouplingMode;
import net.minegasm.core.DeliveryMode;
import net.minegasm.core.GameEventKind;
import net.minegasm.core.HapticPrimitive;
import net.minegasm.core.HapticRole;
import net.minegasm.core.OutputKind;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Codec fidelity: every primitive type survives a JSON round trip unchanged. */
class ScenePackCodecTest {

    private final ScenePackCodec codec = new ScenePackCodec();

    @Test
    void roundTripPreservesEveryPrimitiveType() {
        ScenePack pack = allPrimitivesPack();
        ScenePack parsed = codec.fromJson(codec.toJson(pack));
        assertEquals(pack, parsed);
    }

    @Test
    void writtenJsonCarriesTheDiscriminators() {
        String json = codec.toJson(allPrimitivesPack());
        for (String type : new String[]{"impulse", "texture", "rumble", "sweep", "beat", "hold",
                "oscillation"}) {
            assertTrue(json.contains("\"" + type + "\""), "expected primitive type " + type + " in output");
        }
    }

    private static ScenePack allPrimitivesPack() {
        List<LayerTemplate> layers = new ArrayList<>();
        layers.add(layer("impulse", new HapticPrimitive.Impulse(0.5f, 200, 10, 40),
                EnumSet.of(OutputKind.VIBRATE, OutputKind.OSCILLATE)));
        layers.add(layer("texture", new HapticPrimitive.Texture(0.75f, 300, 0.25f, 0.5f, 0.125f),
                Collections.<OutputKind>emptySet()));
        layers.add(layer("rumble", new HapticPrimitive.Rumble(0.5f, 500, 0.25f, true),
                Collections.<OutputKind>emptySet()));
        layers.add(layer("sweep", new HapticPrimitive.Sweep(0.0f, 1.0f, 400,
                HapticPrimitive.Easing.EASE_IN_OUT), Collections.<OutputKind>emptySet()));
        layers.add(layer("beat", new HapticPrimitive.BeatPattern(Arrays.asList(
                new HapticPrimitive.Beat(0, 0.5f, 100),
                new HapticPrimitive.Beat(150, 0.75f, 100))), Collections.<OutputKind>emptySet()));
        layers.add(layer("hold", new HapticPrimitive.Hold(0.5f, 500, 20, 60),
                Collections.<OutputKind>emptySet()));
        layers.add(layer("oscillation", new HapticPrimitive.Oscillation(0.75f, 700, 3000),
                Collections.<OutputKind>emptySet()));

        SceneTemplate scene = new SceneTemplate(100, 3000, "cont", layers);
        return new ScenePack(ScenePack.SCHEMA_VERSION, "test.pack", "Test Pack", "author",
                "a pack exercising every primitive",
                Collections.singletonList(new PackTrigger(GameEventKind.HURT, scene)));
    }

    private static LayerTemplate layer(String id, HapticPrimitive primitive, java.util.Set<OutputKind> outputs) {
        return new LayerTemplate(id, HapticRole.IMPACT, primitive, outputs,
                DeliveryMode.ALL_COMPATIBLE, CouplingMode.MAX, 100, 0, 3000, "cont");
    }
}
