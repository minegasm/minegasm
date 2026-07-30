package net.minegasm.pack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minegasm.core.CouplingMode;
import net.minegasm.core.DeliveryMode;
import net.minegasm.core.GameEventKind;
import net.minegasm.core.HapticPrimitive;
import net.minegasm.core.HapticRole;
import net.minegasm.core.OutputKind;
import net.minegasm.util.HapticMath;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Reads and writes {@link ScenePack} JSON (brief 0003 §2.2). A shared pack is untrusted input, so
 * import fails closed: malformed JSON, an unknown {@code schemaVersion}, a missing required member, a
 * wrong-typed value, or an unknown primitive {@code type} all raise {@link PackFormatException} rather
 * than loading a partial or degraded pack. Optional metadata and unknown extra members are tolerated
 * for forward compatibility.
 *
 * <p>Values are clamped on the way in, levels to {@code [0, 1]} and durations/offsets to a sane bound,
 * so a file can never smuggle an out-of-range value into a scene. Per-output-kind ceilings are not
 * re-applied here: a materialized pack scene is a plain {@link net.minegasm.core.HapticScene} that
 * renders through the identical mixer/scheduler/{@code SafetyCaps} path as the built-in packs, so it
 * cannot exceed those ceilings.
 *
 * <p>The parsing uses the JSON tree ({@link JsonObject}) and the value model's own constructors rather
 * than reflective field binding, so it is correct on the old Gson bundled with older Minecraft and
 * needs no all-args-constructor adapter.
 */
public final class ScenePackCodec {

    /** Upper bound for any authored duration/offset, in milliseconds. Keeps a file from asking for an
     *  absurd multi-hour effect and bounds the ns conversion. */
    static final int MAX_DURATION_MS = 60_000;

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    public String toJson(ScenePack pack) {
        return GSON.toJson(writePack(pack));
    }

    /** Parse a pack, failing closed with {@link PackFormatException} on anything malformed. */
    public ScenePack fromJson(String json) {
        JsonObject root;
        try {
            root = GSON.fromJson(json, JsonObject.class);
        } catch (RuntimeException syntax) {
            throw new PackFormatException("invalid pack JSON", syntax);
        }
        if (root == null) {
            throw new PackFormatException("empty pack");
        }
        try {
            return readPack(root);
        } catch (PackFormatException known) {
            throw known;
        } catch (RuntimeException malformed) {
            // Wrong-typed values (getAsInt on a string, an array where an object was expected, etc.)
            // surface here; turn them into a fail-closed import error rather than leaking out raw.
            throw new PackFormatException("malformed pack: " + malformed.getMessage(), malformed);
        }
    }

    // --- read ------------------------------------------------------------------------------

    private static ScenePack readPack(JsonObject o) {
        int schema = reqInt(o, "schemaVersion");
        if (schema < 1 || schema > ScenePack.SCHEMA_VERSION) {
            throw new PackFormatException("unsupported schemaVersion " + schema
                    + " (this build reads up to " + ScenePack.SCHEMA_VERSION + ")");
        }
        String packId = reqString(o, "packId");
        String name = optString(o, "name", "");
        String author = optString(o, "author", "");
        String description = optString(o, "description", "");
        List<PackTrigger> triggers = new ArrayList<>();
        if (has(o, "triggers")) {
            for (JsonElement el : o.getAsJsonArray("triggers")) {
                triggers.add(readTrigger(el.getAsJsonObject()));
            }
        }
        return new ScenePack(schema, packId, name, author, description, triggers);
    }

    private static PackTrigger readTrigger(JsonObject o) {
        GameEventKind event = reqEnum(GameEventKind.class, o, "event");
        return new PackTrigger(event, readScene(reqObject(o, "scene")));
    }

    private static SceneTemplate readScene(JsonObject o) {
        int priority = optInt(o, "priority", 0);
        int durationMs = boundMs(optInt(o, "durationMs", 0));
        String continuousKey = optNullableString(o, "continuousKey");
        List<LayerTemplate> layers = new ArrayList<>();
        if (has(o, "layers")) {
            for (JsonElement el : o.getAsJsonArray("layers")) {
                layers.add(readLayer(el.getAsJsonObject()));
            }
        }
        return new SceneTemplate(priority, durationMs, continuousKey, layers);
    }

    private static LayerTemplate readLayer(JsonObject o) {
        String layerId = reqString(o, "layerId");
        HapticRole role = reqEnum(HapticRole.class, o, "role");
        HapticPrimitive primitive = readPrimitive(reqObject(o, "primitive"));
        Set<OutputKind> allowed = readOutputs(o);
        DeliveryMode delivery = optEnum(DeliveryMode.class, o, "delivery", null);
        CouplingMode coupling = optEnum(CouplingMode.class, o, "coupling", null);
        int priority = optInt(o, "priority", 0);
        int startOffsetMs = boundMs(optInt(o, "startOffsetMs", 0));
        int expiresAfterMs = boundMs(optInt(o, "expiresAfterMs", 0));
        String coalesceKey = optNullableString(o, "coalesceKey");
        float strengthWeight = unit(o, "strengthWeight");
        return new LayerTemplate(layerId, role, primitive, allowed, delivery, coupling, priority,
                startOffsetMs, expiresAfterMs, coalesceKey, strengthWeight);
    }

    private static HapticPrimitive readPrimitive(JsonObject o) {
        String type = reqString(o, "type").trim().toLowerCase(Locale.ROOT);
        if (type.equals("impulse")) {
            return new HapticPrimitive.Impulse(level(o), dur(o, "durationMs"),
                    dur(o, "attackMs"), dur(o, "releaseMs"));
        }
        if (type.equals("texture")) {
            return new HapticPrimitive.Texture(level(o), dur(o, "durationMs"),
                    unit(o, "grain"), unit(o, "density"), unit(o, "irregularity"));
        }
        if (type.equals("rumble")) {
            return new HapticPrimitive.Rumble(level(o), dur(o, "durationMs"),
                    unit(o, "roughness"), boolAt(o, "decay"));
        }
        if (type.equals("sweep")) {
            return new HapticPrimitive.Sweep(unit(o, "from"), unit(o, "to"), dur(o, "durationMs"),
                    optEnum(HapticPrimitive.Easing.class, o, "easing", HapticPrimitive.Easing.LINEAR));
        }
        if (type.equals("beat")) {
            return new HapticPrimitive.BeatPattern(readBeats(o));
        }
        if (type.equals("hold")) {
            return new HapticPrimitive.Hold(level(o), dur(o, "durationMs"),
                    dur(o, "fadeInMs"), dur(o, "fadeOutMs"));
        }
        if (type.equals("oscillation")) {
            return new HapticPrimitive.Oscillation(level(o), dur(o, "periodMs"), dur(o, "durationMs"));
        }
        throw new PackFormatException("unknown primitive type: '" + type + "'");
    }

    private static List<HapticPrimitive.Beat> readBeats(JsonObject o) {
        List<HapticPrimitive.Beat> beats = new ArrayList<>();
        if (has(o, "beats")) {
            for (JsonElement el : o.getAsJsonArray("beats")) {
                JsonObject b = el.getAsJsonObject();
                beats.add(new HapticPrimitive.Beat(dur(b, "atMs"), level(b), dur(b, "durationMs")));
            }
        }
        return beats;
    }

    private static Set<OutputKind> readOutputs(JsonObject o) {
        if (!has(o, "allowedOutputs")) {
            return Collections.emptySet();
        }
        EnumSet<OutputKind> set = EnumSet.noneOf(OutputKind.class);
        for (JsonElement el : o.getAsJsonArray("allowedOutputs")) {
            String raw = el.getAsString();
            try {
                set.add(OutputKind.valueOf(raw.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException unknown) {
                throw new PackFormatException("unknown OutputKind '" + raw + "'");
            }
        }
        return set;
    }

    // --- write -----------------------------------------------------------------------------

    private static JsonObject writePack(ScenePack p) {
        JsonObject o = new JsonObject();
        o.addProperty("schemaVersion", p.schemaVersion());
        o.addProperty("packId", p.packId());
        o.addProperty("name", p.name());
        o.addProperty("author", p.author());
        o.addProperty("description", p.description());
        JsonArray triggers = new JsonArray();
        for (PackTrigger t : p.triggers()) {
            JsonObject to = new JsonObject();
            to.addProperty("event", t.event().name());
            to.add("scene", writeScene(t.scene()));
            triggers.add(to);
        }
        o.add("triggers", triggers);
        return o;
    }

    private static JsonObject writeScene(SceneTemplate s) {
        JsonObject o = new JsonObject();
        o.addProperty("priority", s.priority());
        o.addProperty("durationMs", s.durationMs());
        if (s.continuousKey() != null) {
            o.addProperty("continuousKey", s.continuousKey());
        }
        JsonArray layers = new JsonArray();
        for (LayerTemplate l : s.layers()) {
            layers.add(writeLayer(l));
        }
        o.add("layers", layers);
        return o;
    }

    private static JsonObject writeLayer(LayerTemplate l) {
        JsonObject o = new JsonObject();
        o.addProperty("layerId", l.layerId());
        o.addProperty("role", l.role().name());
        o.add("primitive", PrimitiveJson.toJson(l.primitive()));
        if (!l.allowedOutputs().isEmpty()) {
            JsonArray arr = new JsonArray();
            for (OutputKind k : l.allowedOutputs()) {
                arr.add(k.name());
            }
            o.add("allowedOutputs", arr);
        }
        if (l.delivery() != null) {
            o.addProperty("delivery", l.delivery().name());
        }
        if (l.coupling() != null) {
            o.addProperty("coupling", l.coupling().name());
        }
        o.addProperty("priority", l.priority());
        o.addProperty("startOffsetMs", l.startOffsetMs());
        o.addProperty("expiresAfterMs", l.expiresAfterMs());
        if (l.coalesceKey() != null) {
            o.addProperty("coalesceKey", l.coalesceKey());
        }
        if (l.strengthWeight() > 0f) {
            o.addProperty("strengthWeight", l.strengthWeight());
        }
        return o;
    }

    // --- helpers ---------------------------------------------------------------------------

    private static boolean has(JsonObject o, String field) {
        return o.has(field) && !o.get(field).isJsonNull();
    }

    private static String reqString(JsonObject o, String field) {
        if (!has(o, field)) {
            throw new PackFormatException("missing required '" + field + "'");
        }
        return o.get(field).getAsString();
    }

    private static String optString(JsonObject o, String field, String dflt) {
        return has(o, field) ? o.get(field).getAsString() : dflt;
    }

    private static String optNullableString(JsonObject o, String field) {
        return has(o, field) ? o.get(field).getAsString() : null;
    }

    private static int reqInt(JsonObject o, String field) {
        if (!has(o, field)) {
            throw new PackFormatException("missing required '" + field + "'");
        }
        return o.get(field).getAsInt();
    }

    private static int optInt(JsonObject o, String field, int dflt) {
        return has(o, field) ? o.get(field).getAsInt() : dflt;
    }

    private static JsonObject reqObject(JsonObject o, String field) {
        if (!has(o, field)) {
            throw new PackFormatException("missing required '" + field + "'");
        }
        return o.getAsJsonObject(field);
    }

    private static boolean boolAt(JsonObject o, String field) {
        return has(o, field) && o.get(field).getAsBoolean();
    }

    /** A level in {@code [0, 1]}, clamped. */
    private static float level(JsonObject o) {
        return unit(o, "level");
    }

    /** A unit value in {@code [0, 1]}, clamped; absent reads as 0. */
    private static float unit(JsonObject o, String field) {
        return HapticMath.clamp01(has(o, field) ? o.get(field).getAsFloat() : 0f);
    }

    /** A millisecond value bounded to {@code [0, MAX_DURATION_MS]}; absent reads as 0. */
    private static int dur(JsonObject o, String field) {
        return boundMs(has(o, field) ? o.get(field).getAsInt() : 0);
    }

    private static int boundMs(int ms) {
        return Math.max(0, Math.min(MAX_DURATION_MS, ms));
    }

    private static <E extends Enum<E>> E reqEnum(Class<E> type, JsonObject o, String field) {
        String raw = reqString(o, field);
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            throw new PackFormatException("unknown " + type.getSimpleName() + " '" + raw
                    + "' for '" + field + "'");
        }
    }

    private static <E extends Enum<E>> E optEnum(Class<E> type, JsonObject o, String field, E dflt) {
        return has(o, field) ? reqEnum(type, o, field) : dflt;
    }
}
