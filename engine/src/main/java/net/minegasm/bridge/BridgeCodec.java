package net.minegasm.bridge;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;

import java.util.Locale;

import net.minegasm.core.LogicalDestination;
import net.minegasm.runtime.ResolvedDestinationSnapshot;

/**
 * Serializes the bridge wire protocol (brief 0002 §4.3): a small, versioned JSON message per outbound
 * event. The mod-to-adapter types are {@code output} (the authoritative logical-destination state) and {@code stop}
 * (a first-class stop-all).
 *
 * <p>The output path carries current normalized levels by role, body region, and output class. Every frame
 * is the full state, so a missing destination retracts. A TTL makes a dropped connection self-clear.
 */
public final class BridgeCodec {

    /** Wire protocol version. An adapter that does not recognize it should refuse rather than guess. */
    public static final int PROTOCOL_VERSION = 1;

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    /**
     * Encode the authoritative destination snapshot. Enum names are lower-cased on the wire. {@code ttlMs}
     * is how long the adapter should hold these levels without a fresh snapshot before zeroing, so a
     * dropped link self-clears.
     */
    public String encodeOutput(ResolvedDestinationSnapshot snapshot, long ttlMs) {
        return encodeOutput(snapshot, ttlMs, false);
    }

    public String encodeTestOutput(ResolvedDestinationSnapshot snapshot, long ttlMs) {
        return encodeOutput(snapshot, ttlMs, true);
    }

    private String encodeOutput(ResolvedDestinationSnapshot snapshot, long ttlMs, boolean test) {
        JsonObject o = new JsonObject();
        o.addProperty("v", PROTOCOL_VERSION);
        o.addProperty("type", "output");
        o.addProperty("generation", snapshot.generation());
        o.addProperty("ttlMs", Math.max(0L, ttlMs));
        if (test) {
            o.addProperty("purpose", "test");
        }
        JsonArray destinations = new JsonArray();
        for (java.util.Map.Entry<LogicalDestination, Float> e : snapshot.levels().entrySet()) {
            LogicalDestination destination = e.getKey();
            JsonObject encoded = new JsonObject();
            encoded.addProperty("role", destination.role().name().toLowerCase(Locale.ROOT));
            encoded.addProperty("region", destination.bodyRegion().name().toLowerCase(Locale.ROOT));
            encoded.addProperty("outputClass",
                    destination.outputClass().name().toLowerCase(Locale.ROOT));
            encoded.addProperty("level", clamp01(e.getValue()));
            destinations.add(encoded);
        }
        o.add("destinations", destinations);
        return GSON.toJson(o);
    }

    private static float clamp01(float v) {
        if (v <= 0f) {
            return 0f;
        }
        return v >= 1f ? 1f : v;
    }

    /**
     * Parse an adapter-to-mod message for the downstream state it reports. A {@code hello} (sent once on
     * connect) or a {@code status} update carries a {@code downstream} field of {@code "ready"} or
     * {@code "unavailable"}. Returns null for any other or malformed frame, so an adapter that predates
     * these messages (or sends an ack we don't model) simply leaves the state unchanged.
     */
    public DownstreamState decodeDownstream(String frame) {
        try {
            JsonObject o = JsonParser.parseString(frame).getAsJsonObject();
            if (!o.has("v") || !o.get("v").isJsonPrimitive()
                    || !o.getAsJsonPrimitive("v").isNumber()
                    || o.get("v").getAsDouble() != PROTOCOL_VERSION
                    || !o.has("type") || !o.has("downstream")) {
                return null;
            }
            String type = o.get("type").getAsString();
            if (!"hello".equals(type) && !"status".equals(type)) {
                return null;
            }
            String downstream = o.get("downstream").getAsString().trim().toLowerCase(Locale.ROOT);
            if ("ready".equals(downstream)) {
                return DownstreamState.READY;
            }
            if ("unavailable".equals(downstream)) {
                return DownstreamState.UNAVAILABLE;
            }
            return null;
        } catch (RuntimeException malformed) {
            return null; // never let a bad inbound line disturb the backend
        }
    }

    /** Encode a first-class stop-all message. */
    public String encodeStop() {
        JsonObject o = new JsonObject();
        o.addProperty("v", PROTOCOL_VERSION);
        o.addProperty("type", "stop");
        return GSON.toJson(o);
    }
}
