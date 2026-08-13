package net.minegasm.bridge;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minegasm.core.CouplingMode;
import net.minegasm.core.GameEventKind;
import net.minegasm.core.HapticLayer;
import net.minegasm.core.HapticPrimitive;
import net.minegasm.core.HapticRole;
import net.minegasm.core.HapticRoute;
import net.minegasm.core.HapticScene;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The bridge wire format: device-neutral effect content, a TTL, and no Buttplug routing. */
class BridgeCodecTest {

    private static final long NOW = 1_000_000_000L;

    private final BridgeCodec codec = new BridgeCodec();

    @Test
    void effectCarriesDeviceNeutralSceneContent() {
        HapticScene scene = new HapticScene("hurt:HURT", GameEventKind.HURT, 100,
                Collections.singletonList(layer()), NOW, NOW + 300L * 1_000_000L, null);

        JsonObject o = JsonParser.parseString(codec.encodeEffect(scene, NOW)).getAsJsonObject();

        assertEquals(BridgeCodec.PROTOCOL_VERSION, o.get("v").getAsInt());
        assertEquals("effect", o.get("type").getAsString());
        assertEquals("hurt:HURT", o.get("sceneId").getAsString());
        assertEquals("HURT", o.get("event").getAsString());
        assertEquals(100, o.get("priority").getAsInt());
        assertEquals(300L, o.get("ttlMs").getAsLong(), "ttl is the scene's remaining lifetime in ms");

        JsonObject layer = o.getAsJsonArray("layers").get(0).getAsJsonObject();
        assertEquals("IMPACT", layer.get("role").getAsString());
        assertEquals("impulse", layer.getAsJsonObject("primitive").get("type").getAsString());
        assertEquals(0.8f, layer.getAsJsonObject("primitive").get("level").getAsFloat(), 1e-6f);
    }

    @Test
    void effectOmitsButtplugRouting() {
        HapticScene scene = new HapticScene("hurt:HURT", GameEventKind.HURT, 100,
                Collections.singletonList(layer()), NOW, NOW + 300L * 1_000_000L, null);

        JsonObject layer = JsonParser.parseString(codec.encodeEffect(scene, NOW)).getAsJsonObject()
                .getAsJsonArray("layers").get(0).getAsJsonObject();

        assertFalse(layer.has("allowedOutputs"), "output kinds are Buttplug verbs, not device-neutral");
        assertFalse(layer.has("route"), "device/feature routing must not leak to an adapter");
    }

    @Test
    void ttlClampsToZeroForAnAlreadyExpiredScene() {
        HapticScene scene = new HapticScene("x:HURT", GameEventKind.HURT, 0,
                Collections.singletonList(layer()), NOW, NOW + 100L * 1_000_000L, null);
        JsonObject o = JsonParser.parseString(codec.encodeEffect(scene, NOW + 500L * 1_000_000L))
                .getAsJsonObject();
        assertEquals(0L, o.get("ttlMs").getAsLong());
    }

    @Test
    void everyLayerEncodesIncludingBeatPatternPrimitives() {
        HapticLayer beat = new HapticLayer("reward", HapticRole.REWARD,
                new HapticPrimitive.BeatPattern(java.util.Arrays.asList(
                        new HapticPrimitive.Beat(0, 0.5f, 100),
                        new HapticPrimitive.Beat(150, 0.75f, 100))),
                HapticRoute.buzzAll(), CouplingMode.MAX, 50, 0L, 400L * 1_000_000L, null);
        HapticScene scene = new HapticScene("xp:XP_GAIN", GameEventKind.XP_GAIN, 50,
                java.util.Arrays.asList(layer(), beat), NOW, NOW + 400L * 1_000_000L, null);

        JsonObject o = JsonParser.parseString(codec.encodeEffect(scene, NOW)).getAsJsonObject();
        var layers = o.getAsJsonArray("layers");

        assertEquals(2, layers.size(), "every layer must encode, not just the first");
        assertEquals("impulse", layers.get(0).getAsJsonObject().getAsJsonObject("primitive")
                .get("type").getAsString());
        JsonObject beatPrimitive = layers.get(1).getAsJsonObject().getAsJsonObject("primitive");
        assertEquals("beat", beatPrimitive.get("type").getAsString());
        assertEquals(2, beatPrimitive.getAsJsonArray("beats").size(), "beat pattern carries its beats");
    }

    @Test
    void outputCarriesTheAuthoritativePerRoleSnapshot() {
        java.util.EnumMap<HapticRole, Float> levels = new java.util.EnumMap<>(HapticRole.class);
        levels.put(HapticRole.IMPACT, 0.8f);
        levels.put(HapticRole.AMBIENT, 0f);
        levels.put(HapticRole.REWARD, 1.5f); // out of range on purpose: must clamp to 1

        JsonObject o = JsonParser.parseString(codec.encodeOutput(levels, 6_000L)).getAsJsonObject();

        assertEquals("output", o.get("type").getAsString());
        assertEquals(6_000L, o.get("ttlMs").getAsLong());
        JsonObject roles = o.getAsJsonObject("roles");
        assertEquals(0.8f, roles.get("impact").getAsFloat(), 1e-6f);
        assertEquals(0f, roles.get("ambient").getAsFloat(), 1e-6f, "a zeroed role is sent so it retracts");
        assertEquals(1f, roles.get("reward").getAsFloat(), 1e-6f, "levels clamp to the unit range");
    }

    @Test
    void stopIsAFirstClassMessage() {
        JsonObject o = JsonParser.parseString(codec.encodeStop()).getAsJsonObject();
        assertEquals("stop", o.get("type").getAsString());
        assertEquals(BridgeCodec.PROTOCOL_VERSION, o.get("v").getAsInt());
    }

    @Test
    void decodesDownstreamFromHelloAndStatus() {
        assertEquals(DownstreamState.READY,
                codec.decodeDownstream("{\"v\":1,\"type\":\"hello\",\"downstream\":\"ready\"}"));
        assertEquals(DownstreamState.UNAVAILABLE,
                codec.decodeDownstream("{\"v\":1,\"type\":\"status\",\"downstream\":\"unavailable\"}"));
    }

    @Test
    void ignoresFramesWithoutDownstreamInfo() {
        assertEquals(null, codec.decodeDownstream("{\"type\":\"ack\"}"), "an unmodeled frame changes nothing");
        assertEquals(null, codec.decodeDownstream("{\"type\":\"status\"}"), "no downstream field");
        assertEquals(null, codec.decodeDownstream("not json"), "a malformed line is ignored");
    }

    private static HapticLayer layer() {
        return new HapticLayer("hit", HapticRole.IMPACT, new HapticPrimitive.Impulse(0.8f, 250, 10, 40),
                HapticRoute.buzzAll(), CouplingMode.MAX, 100, 0L, 300L * 1_000_000L, null);
    }
}
