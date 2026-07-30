package net.minegasm.pack;

import net.minegasm.core.CouplingMode;
import net.minegasm.core.DeliveryMode;
import net.minegasm.core.GameEventKind;
import net.minegasm.core.HapticLayer;
import net.minegasm.core.HapticPrimitive;
import net.minegasm.core.HapticRole;
import net.minegasm.core.HapticScene;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Template to scene materialization, timing conversion, import clamping, and fail-closed parsing. */
class ScenePackMaterializeTest {

    private final ScenePackCodec codec = new ScenePackCodec();

    @Test
    void resolveConvertsTimingWithLongArithmetic() {
        // 3000 ms is 3_000_000_000 ns, which overflows a plain int multiply; this asserts the long path.
        ScenePack pack = codec.fromJson(codec.toJson(
                packWith(new HapticPrimitive.Impulse(0.5f, 200, 10, 40), 3000, 3000, "coal")));
        long now = 1_000_000_000L;

        HapticScene scene = pack.resolve(GameEventKind.HURT, now).orElseThrow(AssertionError::new);

        assertEquals(now + 3_000_000_000L, scene.expiresAtNs(), "scene duration must convert via long ns");
        assertEquals(GameEventKind.HURT, scene.kind());
        assertEquals("cont", scene.continuousKey());
        assertEquals(1, scene.layers().size());
        HapticLayer layer = scene.layers().get(0);
        assertEquals(3_000_000_000L, layer.expiresAfterNs(), "layer expiry must convert via long ns");
        assertEquals("coal", layer.coalesceKey());
    }

    @Test
    void importClampsLevelAndBoundsDuration() {
        ScenePack pack = codec.fromJson(codec.toJson(
                packWith(new HapticPrimitive.Impulse(5.0f, 999_999_999, 0, 0), 250, 100, null)));

        HapticScene scene = pack.resolve(GameEventKind.HURT, 0L).orElseThrow(AssertionError::new);
        HapticPrimitive.Impulse impulse = (HapticPrimitive.Impulse) scene.layers().get(0).primitive();

        assertEquals(1.0f, impulse.level(), 0f, "level above 1 must clamp on import");
        assertEquals(ScenePackCodec.MAX_DURATION_MS, impulse.durationMs(), "duration must bound on import");
    }

    @Test
    void resolveReturnsEmptyForUnmappedEvent() {
        ScenePack pack = codec.fromJson(codec.toJson(
                packWith(new HapticPrimitive.Hold(0.5f, 200, 0, 0), 200, 200, null)));
        assertFalse(pack.resolve(GameEventKind.ATTACK, 0L).isPresent());
    }

    @Test
    void unknownPrimitiveTypeFailsClosed() {
        String json = "{\"schemaVersion\":1,\"packId\":\"p\",\"triggers\":[{\"event\":\"HURT\","
                + "\"scene\":{\"durationMs\":100,\"layers\":[{\"layerId\":\"l\",\"role\":\"IMPACT\","
                + "\"primitive\":{\"type\":\"laser\"}}]}}]}";
        assertThrows(PackFormatException.class, () -> codec.fromJson(json));
    }

    @Test
    void missingPrimitiveTypeFailsClosed() {
        String json = "{\"schemaVersion\":1,\"packId\":\"p\",\"triggers\":[{\"event\":\"HURT\","
                + "\"scene\":{\"durationMs\":100,\"layers\":[{\"layerId\":\"l\",\"role\":\"IMPACT\","
                + "\"primitive\":{}}]}}]}";
        assertThrows(PackFormatException.class, () -> codec.fromJson(json));
    }

    @Test
    void missingPackIdFailsClosed() {
        assertThrows(PackFormatException.class, () -> codec.fromJson("{\"schemaVersion\":1}"));
    }

    @Test
    void newerSchemaVersionFailsClosed() {
        assertThrows(PackFormatException.class,
                () -> codec.fromJson("{\"schemaVersion\":999,\"packId\":\"p\"}"));
    }

    @Test
    void malformedJsonFailsClosed() {
        assertThrows(PackFormatException.class, () -> codec.fromJson("{ not json"));
    }

    private static ScenePack packWith(HapticPrimitive primitive, int sceneDurationMs,
                                      int layerExpiresMs, String layerCoalesceKey) {
        LayerTemplate layer = new LayerTemplate("l", HapticRole.IMPACT, primitive,
                Collections.emptySet(), DeliveryMode.ALL_COMPATIBLE, CouplingMode.MAX,
                0, 0, layerExpiresMs, layerCoalesceKey);
        SceneTemplate scene = new SceneTemplate(50, sceneDurationMs, "cont",
                Collections.singletonList(layer));
        return new ScenePack(ScenePack.SCHEMA_VERSION, "test.pack", "", "", "",
                Collections.singletonList(new PackTrigger(GameEventKind.HURT, scene)));
    }
}
