package net.minegasm.bridge;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Locale;

import net.minegasm.core.HapticLayer;
import net.minegasm.core.HapticScene;
import net.minegasm.pack.PrimitiveJson;

/**
 * Serializes the bridge wire protocol (brief 0002 §4.3): a small, versioned JSON message per outbound
 * event. Two message types, {@code effect} (a scene fired) and {@code stop} (a first-class stop-all).
 *
 * <p>An {@code effect} carries the scene's device-neutral content (id, event kind, priority, TTL, and
 * layers via the shared {@link PrimitiveJson}). It omits the layer route and output kinds, which are
 * Buttplug's verb set, so an adapter is never handed Buttplug-specific routing. Every effect carries
 * {@code ttlMs} so an adapter self-expires if the connection drops; output never depends on a later stop
 * arriving.
 */
public final class BridgeCodec {

    /** Wire protocol version. An adapter that does not recognize it should refuse rather than guess. */
    public static final int PROTOCOL_VERSION = 1;

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    /** Encode a scene as an {@code effect} message, with a TTL relative to {@code nowNs}. */
    public String encodeEffect(HapticScene scene, long nowNs) {
        JsonObject o = new JsonObject();
        o.addProperty("v", PROTOCOL_VERSION);
        o.addProperty("type", "effect");
        o.addProperty("sceneId", scene.sceneId());
        if (scene.kind() != null) {
            o.addProperty("event", scene.kind().name());
        }
        o.addProperty("priority", scene.priority());
        o.addProperty("ttlMs", Math.max(0L, (scene.expiresAtNs() - nowNs) / 1_000_000L));
        if (scene.isContinuous()) {
            o.addProperty("continuousKey", scene.continuousKey());
        }
        JsonArray layers = new JsonArray();
        for (HapticLayer layer : scene.layers()) {
            layers.add(encodeLayer(layer));
        }
        o.add("layers", layers);
        return GSON.toJson(o);
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
            if (!o.has("type") || !o.has("downstream")) {
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

    private static JsonObject encodeLayer(HapticLayer l) {
        JsonObject o = new JsonObject();
        o.addProperty("layerId", l.layerId());
        if (l.role() != null) {
            o.addProperty("role", l.role().name());
        }
        if (l.coupling() != null) {
            o.addProperty("coupling", l.coupling().name());
        }
        o.addProperty("priority", l.priority());
        // Timing on the wire is milliseconds (device-neutral), converted from the engine's monotonic ns.
        o.addProperty("startOffsetMs", l.startOffsetNs() / 1_000_000L);
        o.addProperty("expiresAfterMs", l.expiresAfterNs() / 1_000_000L);
        if (l.coalesceKey() != null) {
            o.addProperty("coalesceKey", l.coalesceKey());
        }
        o.add("primitive", PrimitiveJson.toJson(l.primitive()));
        return o;
    }
}
