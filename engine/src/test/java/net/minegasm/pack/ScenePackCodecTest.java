package net.minegasm.pack;

import net.minegasm.core.BodyRegion;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    void roundTripPreservesBodyRegionAndOmitsWholeBody() {
        LayerTemplate scoped = new LayerTemplate("g", HapticRole.IMPACT,
                new HapticPrimitive.Hold(0.5f, 200, 0, 0), Collections.<OutputKind>emptySet(),
                DeliveryMode.ALL_COMPATIBLE, CouplingMode.MAX, 100, 0, 200, null, 0f, BodyRegion.GENITAL);
        LayerTemplate wholeBody = layer("wb", new HapticPrimitive.Hold(0.5f, 200, 0, 0),
                Collections.<OutputKind>emptySet());
        SceneTemplate scene = new SceneTemplate(100, 200, "cont", Arrays.asList(scoped, wholeBody));
        ScenePack pack = new ScenePack(ScenePack.SCHEMA_VERSION, "region.pack", "Region", "author",
                "a pack with a region-scoped layer",
                Collections.singletonList(new PackTrigger(GameEventKind.HURT, scene)));

        String json = codec.toJson(pack);
        ScenePack parsed = codec.fromJson(json);

        assertEquals(pack, parsed, "an authored region survives the round trip");
        assertEquals(BodyRegion.GENITAL, parsed.triggers().get(0).scene().layers().get(0).bodyRegion());
        assertEquals(BodyRegion.WHOLE_BODY, parsed.triggers().get(0).scene().layers().get(1).bodyRegion());
        assertTrue(json.contains("GENITAL"), "the explicit region is written");
        assertFalse(json.contains("WHOLE_BODY"), "the whole-body default is omitted, not written out");
    }

    @Test
    void writtenJsonCarriesTheDiscriminators() {
        String json = codec.toJson(allPrimitivesPack());
        for (String type : new String[]{"impulse", "texture", "rumble", "sweep", "beat", "hold",
                "oscillation"}) {
            assertTrue(json.contains("\"" + type + "\""), "expected primitive type " + type + " in output");
        }
    }

    @Test
    void unsupportedDeliveryModeIsRejected() {
        // A destination-selection mode the mixer ignores must fail closed, not silently fan to every
        // device (review P2-1).
        String json = codec.toJson(allPrimitivesPack()).replaceFirst("ALL_COMPATIBLE", "BEST_GLOBAL");
        PackFormatException ex = assertThrows(PackFormatException.class, () -> codec.fromJson(json));
        assertTrue(ex.getMessage().contains("BEST_GLOBAL"), "the error names the unsupported mode");
    }

    @Test
    void anUnknownBodyRegionIsRejected() {
        // A typo'd region must fail closed like every other enum field: silently defaulting to whole-body
        // would route the effect everywhere, the mis-routing the fail-closed codec exists to prevent.
        String json = codec.toJson(allPrimitivesPack())
                .replaceFirst("\"role\": \"IMPACT\"", "\"role\": \"IMPACT\", \"region\": \"LEFT_PINKY\"");
        PackFormatException ex = assertThrows(PackFormatException.class, () -> codec.fromJson(json));
        assertTrue(ex.getMessage().contains("LEFT_PINKY"), "the error names the unknown region");
    }

    @Test
    void tooManyTriggersIsRejected() {
        // Structural cardinality is bounded even when each entry is tiny, so a pack can't exhaust memory
        // with a huge array (review P2-5).
        StringBuilder sb = new StringBuilder();
        sb.append("{\"schemaVersion\":").append(ScenePack.SCHEMA_VERSION)
                .append(",\"packId\":\"big\",\"triggers\":[");
        for (int i = 0; i <= 512; i++) { // 513 entries, over the 512 cap
            if (i > 0) {
                sb.append(',');
            }
            sb.append("{\"event\":\"HURT\",\"scene\":{}}");
        }
        sb.append("]}");
        PackFormatException ex = assertThrows(PackFormatException.class, () -> codec.fromJson(sb.toString()));
        assertTrue(ex.getMessage().contains("triggers"), "the over-limit array is named");
    }

    @Test
    void supplementalDeliveryModeIsRejected() {
        // SUPPLEMENTAL is a no-op like the destination-selection modes, so it fails closed too (P2-2).
        String json = codec.toJson(allPrimitivesPack()).replaceFirst("ALL_COMPATIBLE", "SUPPLEMENTAL");
        PackFormatException ex = assertThrows(PackFormatException.class, () -> codec.fromJson(json));
        assertTrue(ex.getMessage().contains("SUPPLEMENTAL"));
    }

    @Test
    void aQuotedNumberIsRejectedNotCoerced() {
        // A quoted numeric (including "NaN") must be rejected up front rather than coerced (P2-5).
        String json = "{\"schemaVersion\":" + ScenePack.SCHEMA_VERSION + ",\"packId\":\"x\",\"triggers\":["
                + "{\"event\":\"HURT\",\"scene\":{\"layers\":[{\"layerId\":\"l\",\"role\":\"IMPACT\","
                + "\"primitive\":{\"type\":\"impulse\",\"level\":\"0.5\",\"durationMs\":200}}]}}]}";
        PackFormatException ex = assertThrows(PackFormatException.class, () -> codec.fromJson(json));
        assertTrue(ex.getMessage().contains("must be a number"), "the offending field is named");
    }

    @Test
    void aFractionalIntegerFieldIsRejected() {
        // durationMs is an integer field; 1.5 must be rejected, not truncated (follow-up P2-3).
        String json = "{\"schemaVersion\":" + ScenePack.SCHEMA_VERSION + ",\"packId\":\"x\",\"triggers\":["
                + "{\"event\":\"HURT\",\"scene\":{\"layers\":[{\"layerId\":\"l\",\"role\":\"IMPACT\","
                + "\"primitive\":{\"type\":\"impulse\",\"level\":0.5,\"durationMs\":1.5}}]}}]}";
        PackFormatException ex = assertThrows(PackFormatException.class, () -> codec.fromJson(json));
        assertTrue(ex.getMessage().contains("whole number"));
    }

    @Test
    void anOverflowingIntegerFieldIsRejected() {
        String json = "{\"schemaVersion\":" + ScenePack.SCHEMA_VERSION + ",\"packId\":\"x\",\"triggers\":["
                + "{\"event\":\"HURT\",\"scene\":{\"layers\":[{\"layerId\":\"l\",\"role\":\"IMPACT\","
                + "\"primitive\":{\"type\":\"impulse\",\"level\":0.5,\"durationMs\":99999999999}}]}}]}";
        PackFormatException ex = assertThrows(PackFormatException.class, () -> codec.fromJson(json));
        assertTrue(ex.getMessage().contains("out of the integer range"));
    }

    private static ScenePack allPrimitivesPack() {
        List<LayerTemplate> layers = new ArrayList<>();
        // A non-zero strengthWeight so the Tier 2 field is exercised by the round trip.
        layers.add(new LayerTemplate("impulse", HapticRole.IMPACT,
                new HapticPrimitive.Impulse(0.5f, 200, 10, 40),
                EnumSet.of(OutputKind.VIBRATE, OutputKind.OSCILLATE),
                DeliveryMode.ALL_COMPATIBLE, CouplingMode.MAX, 100, 0, 3000, "cont", 0.5f));
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
