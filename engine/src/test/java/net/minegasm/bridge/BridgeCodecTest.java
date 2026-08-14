package net.minegasm.bridge;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minegasm.core.BodyRegion;
import net.minegasm.core.HapticRole;
import net.minegasm.core.LogicalDestination;
import net.minegasm.core.OutputClass;
import net.minegasm.runtime.ResolvedDestinationSnapshot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The bridge wire format: an authoritative destination snapshot, a TTL, and a first-class stop. */
class BridgeCodecTest {

    private final BridgeCodec codec = new BridgeCodec();

    @Test
    void outputCarriesTheAuthoritativePerRoleSnapshot() {
        java.util.Map<LogicalDestination, Float> levels = new java.util.LinkedHashMap<>();
        levels.put(new LogicalDestination(HapticRole.IMPACT, BodyRegion.GENITAL,
                OutputClass.STRENGTH), 0.8f);
        levels.put(new LogicalDestination(HapticRole.REWARD, BodyRegion.NIPPLE,
                OutputClass.MOTION), 1.5f);
        ResolvedDestinationSnapshot snapshot = new ResolvedDestinationSnapshot(42L, 100L, levels);

        JsonObject o = JsonParser.parseString(codec.encodeOutput(snapshot, 6_000L)).getAsJsonObject();

        assertEquals("output", o.get("type").getAsString());
        assertEquals(6_000L, o.get("ttlMs").getAsLong());
        assertEquals(42L, o.get("generation").getAsLong());
        JsonArray destinations = o.getAsJsonArray("destinations");
        assertEquals(2, destinations.size());
        JsonObject first = destinations.get(0).getAsJsonObject();
        assertEquals("impact", first.get("role").getAsString());
        assertEquals("genital", first.get("region").getAsString());
        assertEquals("strength", first.get("outputClass").getAsString());
        assertEquals(0.8f, first.get("level").getAsFloat(), 1e-6f);
        assertEquals(1f, destinations.get(1).getAsJsonObject().get("level").getAsFloat(), 1e-6f,
                "levels clamp to the unit range");
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
