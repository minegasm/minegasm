package net.minegasm.testsupport;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minegasm.buttplug.ButtplugTransport;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

/**
 * In-process fake Buttplug v4 server implementing {@link ButtplugTransport} for deterministic
 * protocol tests (brief §14.1). Responds synchronously so no test sleeps. Supports the fixtures the
 * acceptance matrix requires: empty list, multi-feature devices, timing gaps, device removal / index
 * reuse, error responses, out-of-order/delayed replies, disconnect, and ping enforcement.
 */
public final class FakeButtplugServer implements ButtplugTransport {

    /** A device the fake will advertise. */
    public record FakeDevice(int index, String name, String displayName, int timingGapMs,
                             List<FakeFeature> features) {}

    /** One feature: description plus output kind -> {@code [min,max]} value ranges. */
    public record FakeFeature(String description, Map<String, int[]> outputs,
                              Map<String, int[]> durations) {
        public FakeFeature(String description, Map<String, int[]> outputs) {
            this(description, outputs, Map.of());
        }
    }

    /** A recorded outbound command parsed from the client. */
    public record Recorded(String type, int deviceIndex, int featureIndex, String kind,
                           Integer value, Integer duration) {}

    private Consumer<String> onMessage = m -> {};
    private Consumer<Throwable> onClose = t -> {};
    private boolean open;

    public long maxPingTimeMs = 0;
    public boolean errorOnOutput = false;
    public boolean withholdOkForOutput = false;
    public boolean scanningFinishes = true;

    private List<FakeDevice> devices = new ArrayList<>();
    public final List<String> sentFrames = new ArrayList<>();
    public final List<Recorded> recorded = new ArrayList<>();
    public int stopAllCount = 0;
    public int pingCount = 0;

    public FakeButtplugServer withDevices(FakeDevice... devs) {
        this.devices = new ArrayList<>(List.of(devs));
        return this;
    }

    // --- ButtplugTransport -----------------------------------------------------------------

    @Override
    public CompletionStage<Void> connect(URI uri, Consumer<String> onMessage, Consumer<Throwable> onClose) {
        this.onMessage = onMessage;
        this.onClose = onClose;
        this.open = true;
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void send(String frame) {
        if (!open) {
            return;
        }
        sentFrames.add(frame);
        JsonElement root = JsonParser.parseString(frame);
        for (JsonElement el : root.getAsJsonArray()) {
            JsonObject obj = el.getAsJsonObject();
            for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
                handle(e.getKey(), e.getValue().getAsJsonObject());
            }
        }
    }

    @Override
    public boolean isOpen() {
        return open;
    }

    @Override
    public void close() {
        open = false;
    }

    // --- test controls ---------------------------------------------------------------------

    /** Push an unsolicited device list (e.g. after scanning or a reconnect with a reused index). */
    public void pushDeviceList(FakeDevice... devs) {
        this.devices = new ArrayList<>(List.of(devs));
        deliver(deviceListFrame(0));
    }

    /** Simulate the server dropping the connection. */
    public void dropConnection(Throwable cause) {
        open = false;
        onClose.accept(cause);
    }

    public void deliverRaw(String frame) {
        deliver(frame);
    }

    // --- request handling ------------------------------------------------------------------

    private void handle(String name, JsonObject body) {
        long id = body.has("Id") ? body.get("Id").getAsLong() : 0;
        switch (name) {
            case "RequestServerInfo" -> deliver(serverInfoFrame(id));
            case "RequestDeviceList" -> deliver(deviceListFrame(id));
            case "StartScanning" -> {
                deliver(okFrame(id));
                if (scanningFinishes) {
                    deliver(scanningFinishedFrame());
                }
            }
            case "StopScanning" -> deliver(okFrame(id));
            case "Ping" -> {
                pingCount++;
                deliver(okFrame(id));
            }
            case "Disconnect" -> deliver(okFrame(id));
            case "StopCmd" -> {
                // v4: optional DeviceIndex/FeatureIndex select the stop scope; absent = stop all.
                int device = body.has("DeviceIndex") ? body.get("DeviceIndex").getAsInt() : -1;
                int feature = body.has("FeatureIndex") ? body.get("FeatureIndex").getAsInt() : -1;
                if (device < 0) {
                    stopAllCount++;
                }
                recorded.add(new Recorded("StopCmd", device, feature, null, null, null));
                deliver(okFrame(id));
            }
            case "OutputCmd" -> {
                recordOutput(body);
                if (errorOnOutput) {
                    deliver(errorFrame(id, "output rejected", 3));
                } else if (!withholdOkForOutput) {
                    deliver(okFrame(id));
                }
            }
            default -> deliver(okFrame(id));
        }
    }

    private void recordOutput(JsonObject body) {
        int device = body.get("DeviceIndex").getAsInt();
        int feature = body.get("FeatureIndex").getAsInt();
        JsonObject command = body.getAsJsonObject("Command");
        for (Map.Entry<String, JsonElement> e : command.entrySet()) {
            JsonObject payload = e.getValue().getAsJsonObject();
            Integer value = payload.has("Value") ? payload.get("Value").getAsInt() : null;
            Integer duration = payload.has("Duration") ? payload.get("Duration").getAsInt() : null;
            recorded.add(new Recorded("OutputCmd", device, feature, e.getKey(), value, duration));
        }
    }

    private void deliver(String frame) {
        onMessage.accept(frame);
    }

    // --- frame builders --------------------------------------------------------------------

    private String serverInfoFrame(long id) {
        JsonObject b = new JsonObject();
        b.addProperty("Id", id);
        b.addProperty("ServerName", "FakeIntiface");
        b.addProperty("ProtocolVersionMajor", 4);
        b.addProperty("ProtocolVersionMinor", 0);
        b.addProperty("MaxPingTime", maxPingTimeMs);
        return wrap("ServerInfo", b);
    }

    private String okFrame(long id) {
        JsonObject b = new JsonObject();
        b.addProperty("Id", id);
        return wrap("Ok", b);
    }

    private String errorFrame(long id, String message, int code) {
        JsonObject b = new JsonObject();
        b.addProperty("Id", id);
        b.addProperty("ErrorMessage", message);
        b.addProperty("ErrorCode", code);
        return wrap("Error", b);
    }

    private String scanningFinishedFrame() {
        JsonObject b = new JsonObject();
        b.addProperty("Id", 0);
        return wrap("ScanningFinished", b);
    }

    private String deviceListFrame(long id) {
        JsonObject b = new JsonObject();
        b.addProperty("Id", id);
        // v4: Devices and DeviceFeatures are index-keyed objects, not arrays (Rust HashMap<u32,_> /
        // buttplug4j HashMap<Integer,_>).
        JsonObject devs = new JsonObject();
        for (FakeDevice d : devices) {
            JsonObject dev = new JsonObject();
            dev.addProperty("DeviceIndex", d.index());
            dev.addProperty("DeviceName", d.name());
            if (d.displayName() != null) {
                dev.addProperty("DeviceDisplayName", d.displayName());
            }
            dev.addProperty("DeviceMessageTimingGap", d.timingGapMs());
            JsonObject feats = new JsonObject();
            int featureIndex = 0;
            for (FakeFeature f : d.features()) {
                JsonObject feat = new JsonObject();
                feat.addProperty("FeatureIndex", featureIndex);
                feat.addProperty("FeatureDescription", f.description());
                // Output is an array of single-key wrapper objects {Kind:{Value:[min,max]}}.
                JsonArray output = new JsonArray();
                f.outputs().forEach((kind, range) -> {
                    JsonObject spec = new JsonObject();
                    spec.add("Value", arr(range));
                    int[] dur = f.durations().get(kind);
                    if (dur != null) {
                        spec.add("Duration", arr(dur));
                    }
                    JsonObject wrapper = new JsonObject();
                    wrapper.add(kind, spec);
                    output.add(wrapper);
                });
                feat.add("Output", output);
                feats.add(String.valueOf(featureIndex), feat);
                featureIndex++;
            }
            dev.add("DeviceFeatures", feats);
            devs.add(String.valueOf(d.index()), dev);
        }
        b.add("Devices", devs);
        return wrap("DeviceList", b);
    }

    private static JsonArray arr(int[] values) {
        JsonArray a = new JsonArray();
        for (int v : values) {
            a.add(v);
        }
        return a;
    }

    private static String wrap(String name, JsonObject body) {
        JsonObject msg = new JsonObject();
        msg.add(name, body);
        JsonArray a = new JsonArray();
        a.add(msg);
        return a.toString();
    }
}
