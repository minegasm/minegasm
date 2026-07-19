package gg.meza.feelcraft.buttplug;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import gg.meza.feelcraft.device.HapticCommand;
import gg.meza.feelcraft.haptics.OutputKind;
public final class ButtplugMessageFactory {
    private ButtplugMessageFactory() {}
    public static String requestServerInfo(int id) { JsonObject m = new JsonObject(); JsonObject body = new JsonObject(); body.addProperty("Id", id); body.addProperty("ClientName", "Feelcraft Haptics MVP"); body.addProperty("ProtocolVersionMajor", 4); body.addProperty("ProtocolVersionMinor", 0); m.add("RequestServerInfo", body); return envelope(m); }
    public static String requestDeviceList(int id) { return simple("RequestDeviceList", id); }
    public static String startScanning(int id) { return simple("StartScanning", id); }
    public static String stopScanning(int id) { return simple("StopScanning", id); }
    public static String ping(int id) { return simple("Ping", id); }
    public static String disconnect(int id) { return simple("Disconnect", id); }
    public static String stopAll(int id) { JsonObject m = new JsonObject(); JsonObject body = new JsonObject(); body.addProperty("Id", id); body.addProperty("Inputs", true); body.addProperty("Outputs", true); m.add("StopCmd", body); return envelope(m); }
    public static String output(int id, HapticCommand command) { JsonObject root = new JsonObject(); JsonObject body = new JsonObject(); body.addProperty("Id", id); body.addProperty("DeviceIndex", command.deviceIndex()); body.addProperty("FeatureIndex", command.featureIndex()); JsonObject outCommand = new JsonObject(); JsonObject payload = new JsonObject(); payload.addProperty("Value", command.value()); if (command.outputKind() == OutputKind.HW_POSITION_WITH_DURATION && command.durationMs() != null) payload.addProperty("Duration", command.durationMs()); outCommand.add(toButtplugKind(command.outputKind()), payload); body.add("Command", outCommand); root.add("OutputCmd", body); return envelope(root); }
    private static String simple(String type, int id) { JsonObject m = new JsonObject(); JsonObject body = new JsonObject(); body.addProperty("Id", id); m.add(type, body); return envelope(m); }
    private static String envelope(JsonObject message) { JsonArray arr = new JsonArray(); arr.add(message); return arr.toString(); }
    private static String toButtplugKind(OutputKind kind) { return switch (kind) { case VIBRATE -> "Vibrate"; case ROTATE -> "Rotate"; case OSCILLATE -> "Oscillate"; case CONSTRICT -> "Constrict"; case TEMPERATURE -> "Temperature"; case LED -> "Led"; case POSITION -> "Position"; case HW_POSITION_WITH_DURATION -> "HwPositionWithDuration"; }; }
}
