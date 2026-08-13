package net.minegasm.bridge;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minegasm.core.HapticRole;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The bridge wire format: an authoritative per-role output snapshot, a TTL, and a first-class stop. */
class BridgeCodecTest {

    private final BridgeCodec codec = new BridgeCodec();

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
}
