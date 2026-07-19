package gg.meza.feelcraft.buttplug;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import gg.meza.feelcraft.device.HapticDevice;
import gg.meza.feelcraft.device.HapticFeature;
import gg.meza.feelcraft.device.HapticOutputCapability;
import gg.meza.feelcraft.haptics.OutputKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
public final class ButtplugDeviceMapper {
    public List<HapticDevice> parseDeviceList(JsonObject deviceList) { List<HapticDevice> devices = new ArrayList<>(); JsonObject devicesObj = deviceList.getAsJsonObject("Devices"); if (devicesObj == null) return devices; for (Map.Entry<String, JsonElement> entry : devicesObj.entrySet()) { JsonObject d = entry.getValue().getAsJsonObject(); int deviceIndex = getInt(d, "DeviceIndex", Integer.parseInt(entry.getKey())); String name = getString(d, "DeviceName", "Device " + deviceIndex); String displayName = getString(d, "DeviceDisplayName", name); int gap = getInt(d, "DeviceMessageTimingGap", 50); devices.add(new HapticDevice(deviceIndex, name, displayName, gap, parseFeatures(d.getAsJsonObject("DeviceFeatures")))); } return devices; }
    private List<HapticFeature> parseFeatures(JsonObject featuresObj) { List<HapticFeature> features = new ArrayList<>(); if (featuresObj == null) return features; for (Map.Entry<String, JsonElement> entry : featuresObj.entrySet()) { JsonObject f = entry.getValue().getAsJsonObject(); int featureIndex = getInt(f, "FeatureIndex", Integer.parseInt(entry.getKey())); String description = getString(f, "FeatureDescription", "Feature " + featureIndex); features.add(new HapticFeature(featureIndex, description, parseOutputs(f.getAsJsonObject("Output")))); } return features; }
    private List<HapticOutputCapability> parseOutputs(JsonObject outputObj) { List<HapticOutputCapability> outputs = new ArrayList<>(); if (outputObj == null) return outputs; for (Map.Entry<String, JsonElement> e : outputObj.entrySet()) { OutputKind kind = fromButtplugKind(e.getKey()); if (kind == null) continue; JsonObject info = e.getValue().getAsJsonObject(); int minValue = 0, maxValue = 100; Integer minDuration = null, maxDuration = null; if (info.has("Value") && info.get("Value").isJsonArray()) { var arr = info.getAsJsonArray("Value"); minValue = arr.get(0).getAsInt(); maxValue = arr.get(1).getAsInt(); } if (info.has("Duration") && info.get("Duration").isJsonArray()) { var arr = info.getAsJsonArray("Duration"); minDuration = arr.get(0).getAsInt(); maxDuration = arr.get(1).getAsInt(); } outputs.add(new HapticOutputCapability(kind, minValue, maxValue, minDuration, maxDuration)); } return outputs; }
    private OutputKind fromButtplugKind(String type) { return switch (type) { case "Vibrate" -> OutputKind.VIBRATE; case "Rotate" -> OutputKind.ROTATE; case "Oscillate" -> OutputKind.OSCILLATE; case "Constrict" -> OutputKind.CONSTRICT; case "Temperature" -> OutputKind.TEMPERATURE; case "Led" -> OutputKind.LED; case "Position" -> OutputKind.POSITION; case "HwPositionWithDuration" -> OutputKind.HW_POSITION_WITH_DURATION; default -> null; }; }
    private int getInt(JsonObject obj, String key, int fallback) { return obj != null && obj.has(key) ? obj.get(key).getAsInt() : fallback; }
    private String getString(JsonObject obj, String key, String fallback) { return obj != null && obj.has(key) ? obj.get(key).getAsString() : fallback; }
}
