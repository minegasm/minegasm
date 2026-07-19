package net.minegasm.buttplug;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minegasm.core.InputKind;
import net.minegasm.core.OutputKind;
import net.minegasm.device.HapticDevice;
import net.minegasm.device.HapticFeature;
import net.minegasm.device.InputCapability;
import net.minegasm.device.IntRange;
import net.minegasm.device.OutputCapability;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Encodes outbound and decodes inbound Buttplug v4 messages (brief §9, appendix C). The wire format
 * is a JSON array of {@code {"<MessageName>": {...}}} objects; every client request carries an
 * unsigned {@code Id}. This class is the single place protocol shapes live. Field names (including the
 * {@code DeviceFeatures}/{@code Output} array shape) were verified against the buttplug4j 4.0.278
 * message classes — the reference v4 client that Intiface speaks (brief §9.2).
 *
 * <p>Parsing is defensive: unknown output kinds are preserved as {@link OutputKind#UNKNOWN},
 * malformed ranges are skipped, and unknown messages become {@link ServerMessage.Unknown} rather
 * than throwing — a hostile or future server can never abort ingestion (brief §12.2).
 */
public final class ButtplugCodec {

    public static final int PROTOCOL_MAJOR = 4;
    public static final int PROTOCOL_MINOR = 0;

    private ButtplugCodec() {}

    // --- outbound --------------------------------------------------------------------------

    public static String requestServerInfo(long id, String clientName) {
        JsonObject b = base(id);
        b.addProperty("ClientName", clientName);
        b.addProperty("ProtocolVersionMajor", PROTOCOL_MAJOR);
        b.addProperty("ProtocolVersionMinor", PROTOCOL_MINOR);
        return envelope("RequestServerInfo", b);
    }

    public static String requestDeviceList(long id) {
        return envelope("RequestDeviceList", base(id));
    }

    public static String startScanning(long id) {
        return envelope("StartScanning", base(id));
    }

    public static String stopScanning(long id) {
        return envelope("StopScanning", base(id));
    }

    public static String ping(long id) {
        return envelope("Ping", base(id));
    }

    public static String disconnect(long id) {
        return envelope("Disconnect", base(id));
    }

    /** Stop-all: {@code StopCmd} with only an Id. Bypasses the server timing gap (brief §9.10). */
    public static String stopAll(long id) {
        return envelope("StopCmd", base(id));
    }

    /**
     * Stop one device: v4 {@code StopCmd} with {@code DeviceIndex}. {@code Inputs}/{@code Outputs}
     * default to true on the server (validated against the Rust {@code StopCmdV4}).
     */
    public static String stopDevice(long id, int deviceIndex) {
        JsonObject b = base(id);
        b.addProperty("DeviceIndex", deviceIndex);
        return envelope("StopCmd", b);
    }

    /** Stop one feature: v4 {@code StopCmd} with {@code DeviceIndex} + {@code FeatureIndex}. */
    public static String stopFeature(long id, int deviceIndex, int featureIndex) {
        JsonObject b = base(id);
        b.addProperty("DeviceIndex", deviceIndex);
        b.addProperty("FeatureIndex", featureIndex);
        return envelope("StopCmd", b);
    }

    /** A single feature-level output command (brief §2.5, one command per message). */
    public static String outputCmd(long id, OutputCommand cmd) {
        JsonObject b = base(id);
        b.addProperty("DeviceIndex", cmd.deviceIndex());
        b.addProperty("FeatureIndex", cmd.featureIndex());
        JsonObject payload = new JsonObject();
        payload.addProperty("Value", cmd.value());
        if (cmd.hasDuration()) {
            payload.addProperty("Duration", cmd.durationMs());
        }
        JsonObject command = new JsonObject();
        command.add(cmd.kind().renderableWireName()
                .orElseThrow(() -> new IllegalArgumentException("cannot render " + cmd.kind())), payload);
        b.add("Command", command);
        return envelope("OutputCmd", b);
    }

    private static JsonObject base(long id) {
        JsonObject o = new JsonObject();
        o.addProperty("Id", id);
        return o;
    }

    private static String envelope(String name, JsonObject body) {
        JsonObject msg = new JsonObject();
        msg.add(name, body);
        JsonArray arr = new JsonArray();
        arr.add(msg);
        return arr.toString();
    }

    // --- inbound ---------------------------------------------------------------------------

    /** Parse a frame (array of messages) into zero or more {@link ServerMessage}s. Never throws. */
    public static List<ServerMessage> parse(String frame) {
        List<ServerMessage> out = new ArrayList<>();
        JsonElement root;
        try {
            root = JsonParser.parseString(frame);
        } catch (RuntimeException malformed) {
            return out;
        }
        if (!root.isJsonArray()) {
            return out;
        }
        for (JsonElement el : root.getAsJsonArray()) {
            if (!el.isJsonObject()) {
                continue;
            }
            JsonObject obj = el.getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                ServerMessage parsed = parseOne(entry.getKey(), asObject(entry.getValue()));
                if (parsed != null) {
                    out.add(parsed);
                }
            }
        }
        return out;
    }

    private static ServerMessage parseOne(String name, JsonObject body) {
        long id = optLong(body, "Id", 0L);
        return switch (name) {
            case "ServerInfo" -> new ServerMessage.ServerInfo(id,
                    optString(body, "ServerName", "buttplug"),
                    (int) optLong(body, "ProtocolVersionMajor", optLong(body, "MessageVersion", 4)),
                    (int) optLong(body, "ProtocolVersionMinor", 0),
                    optLong(body, "MaxPingTime", 0));
            case "Ok" -> new ServerMessage.Ok(id);
            case "Error" -> new ServerMessage.Error(id,
                    optString(body, "ErrorMessage", "unknown error"),
                    (int) optLong(body, "ErrorCode", 0));
            case "DeviceList" -> new ServerMessage.DeviceList(id, parseDevices(body));
            case "ScanningFinished" -> new ServerMessage.ScanningFinished(id);
            default -> new ServerMessage.Unknown(id, name);
        };
    }

    private static List<HapticDevice> parseDevices(JsonObject body) {
        List<HapticDevice> devices = new ArrayList<>();
        // v4: "Devices" is an object keyed by device index ({"0":{...}}); tolerate a legacy array too.
        for (JsonObject d : mapOrArrayObjects(body.get("Devices"))) {
            int index = (int) optLong(d, "DeviceIndex", -1);
            if (index < 0) {
                continue;
            }
            String name = optString(d, "DeviceName", "Device " + index);
            String display = optString(d, "DeviceDisplayName", null);
            int gap = (int) optLong(d, "DeviceMessageTimingGap", 0);
            Map<Integer, HapticFeature> features = parseFeatures(d);
            devices.add(new HapticDevice(index, name, Optional.ofNullable(display), gap, features, 0L));
        }
        return devices;
    }

    private static Map<Integer, HapticFeature> parseFeatures(JsonObject device) {
        Map<Integer, HapticFeature> features = new LinkedHashMap<>();
        // v4: "DeviceFeatures" is an object keyed by feature index ({"0":{...}}); tolerate an array.
        int fallbackIndex = 0;
        for (JsonObject f : mapOrArrayObjects(device.get("DeviceFeatures"))) {
            // Features carry their own FeatureIndex and FeatureDescription; fall back to a counter /
            // "Description" for tolerance against older shapes.
            int featureIndex = (int) optLong(f, "FeatureIndex", fallbackIndex);
            String desc = optString(f, "FeatureDescription", optString(f, "Description", ""));
            Map<OutputKind, OutputCapability> outputs = parseOutputs(f.get("Output"));
            Map<InputKind, InputCapability> inputs = parseInputs(f.get("Input"));
            features.put(featureIndex, new HapticFeature(featureIndex, desc, outputs, inputs));
            fallbackIndex++;
        }
        return features;
    }

    /**
     * Iterate a container that may be either a v4 index-keyed object ({@code {"0":{...}}}) or a JSON
     * array ({@code [{...}]}), returning only its object values. This is how the v4 wire encodes
     * {@code Devices} and {@code DeviceFeatures} (index-keyed maps), verified against both the
     * buttplug Rust source and buttplug4j 4.0.278.
     */
    private static List<JsonObject> mapOrArrayObjects(JsonElement element) {
        List<JsonObject> out = new ArrayList<>();
        if (element == null) {
            return out;
        }
        if (element.isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : element.getAsJsonObject().entrySet()) {
                JsonObject o = asObject(e.getValue());
                if (o != null) {
                    out.add(o);
                }
            }
        } else if (element.isJsonArray()) {
            for (JsonElement item : element.getAsJsonArray()) {
                JsonObject o = asObject(item);
                if (o != null) {
                    out.add(o);
                }
            }
        }
        return out;
    }

    /**
     * Parse the v4 {@code Output} array: {@code [ {"Vibrate": {"Value": [min,max]}}, ... ]}. Each
     * element is a single-key wrapper object whose key is the output verb and whose value is a
     * stepped descriptor carrying {@code Value} (range) and, for {@code HwPositionWithDuration}, a
     * {@code Duration} range. Field names verified against buttplug4j 4.0.278.
     */
    private static Map<OutputKind, OutputCapability> parseOutputs(JsonElement outputElement) {
        Map<OutputKind, OutputCapability> outputs = new LinkedHashMap<>();
        for (Map.Entry<String, JsonObject> wrapped : descriptors(outputElement)) {
            // Unknown/future verbs are represented (so the UI can show them as unsupported) but are
            // never rendered — the mixer's kind preference and SafetyCaps exclude UNKNOWN (brief §9.6).
            OutputKind kind = OutputKind.fromWire(wrapped.getKey());
            IntRange value = parseRange(wrapped.getValue(), "Value");
            if (value == null) {
                continue; // malformed range: skip this capability (brief §12.2)
            }
            Optional<IntRange> duration = kind.carriesDuration()
                    ? Optional.ofNullable(parseRange(wrapped.getValue(), "Duration"))
                    : Optional.empty();
            outputs.put(kind, new OutputCapability(kind, value, duration));
        }
        return outputs;
    }

    private static Map<InputKind, InputCapability> parseInputs(JsonElement inputElement) {
        Map<InputKind, InputCapability> inputs = new LinkedHashMap<>();
        for (Map.Entry<String, JsonObject> wrapped : descriptors(inputElement)) {
            InputKind kind = InputKind.fromWire(wrapped.getKey());
            IntRange value = parseRange(wrapped.getValue(), "Value");
            if (kind != InputKind.UNKNOWN && value != null) {
                inputs.put(kind, new InputCapability(kind, value));
            }
        }
        return inputs;
    }

    /**
     * Flatten a v4 descriptor array ({@code [ {"Vibrate": {...}}, ... ]}) into (verb, descriptor)
     * pairs. Tolerates the legacy object-map shape ({@code {"Vibrate": {...}}}) as well.
     */
    private static List<Map.Entry<String, JsonObject>> descriptors(JsonElement element) {
        List<Map.Entry<String, JsonObject>> out = new ArrayList<>();
        if (element == null) {
            return out;
        }
        if (element.isJsonArray()) {
            for (JsonElement item : element.getAsJsonArray()) {
                JsonObject wrapper = asObject(item);
                if (wrapper != null) {
                    for (Map.Entry<String, JsonElement> e : wrapper.entrySet()) {
                        JsonObject spec = asObject(e.getValue());
                        if (spec != null) {
                            out.add(Map.entry(e.getKey(), spec));
                        }
                    }
                }
            }
        } else if (element.isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : element.getAsJsonObject().entrySet()) {
                JsonObject spec = asObject(e.getValue());
                if (spec != null) {
                    out.add(Map.entry(e.getKey(), spec));
                }
            }
        }
        return out;
    }

    /** Parse a {@code [min,max]} range from {@code key} (an int array), tolerating nesting. */
    private static IntRange parseRange(JsonObject spec, String key) {
        if (spec == null) {
            return null;
        }
        JsonElement el = spec.get(key);
        if (el == null || !el.isJsonArray()) {
            return null;
        }
        JsonArray a = el.getAsJsonArray();
        if (a.size() == 1 && a.get(0).isJsonArray()) {
            a = a.get(0).getAsJsonArray();
        }
        if (a.isEmpty()) {
            return null;
        }
        try {
            int min = a.get(0).getAsInt();
            int max = a.get(a.size() - 1).getAsInt();
            return min <= max ? new IntRange(min, max) : new IntRange(max, min);
        } catch (RuntimeException badNumbers) {
            return null;
        }
    }

    private static JsonObject asObject(JsonElement el) {
        return el != null && el.isJsonObject() ? el.getAsJsonObject() : null;
    }

    private static long optLong(JsonObject o, String key, long fallback) {
        try {
            return o != null && o.has(key) && o.get(key).isJsonPrimitive()
                    ? o.get(key).getAsLong() : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static String optString(JsonObject o, String key, String fallback) {
        return o != null && o.has(key) && o.get(key).isJsonPrimitive()
                ? o.get(key).getAsString() : fallback;
    }
}
