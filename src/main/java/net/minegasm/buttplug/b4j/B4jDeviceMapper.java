package net.minegasm.buttplug.b4j;

import net.minegasm.core.InputKind;
import net.minegasm.core.OutputKind;
import net.minegasm.device.HapticDevice;
import net.minegasm.device.HapticFeature;
import net.minegasm.device.InputCapability;
import net.minegasm.device.IntRange;
import net.minegasm.device.OutputCapability;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import io.github.blackspherefollower.buttplug4j.client.ButtplugClientDevice;
import io.github.blackspherefollower.buttplug4j.client.ButtplugClientDeviceFeature;
import io.github.blackspherefollower.buttplug4j.client.ButtplugOutput;

/**
 * Maps buttplug4j client devices into our normalized {@link HapticDevice}/{@link HapticFeature}
 * model. Capabilities are advertised at a fixed internal resolution ({@code [0, 1000]}); the actual
 * hardware step range is owned by buttplug4j, which scales our normalized value when we call the
 * feature's {@code run*Float} methods (see {@link Buttplug4jProvider}). This keeps the mapping
 * independent of each device's advertised range.
 *
 * <p>Verified to compile against buttplug4j 4.0.278.
 */
final class B4jDeviceMapper {

    /** Internal quantisation for the engine's integer scaling; converted back to a float on send. */
    static final int RESOLUTION = 1000;

    private static final IntRange RANGE = new IntRange(0, RESOLUTION);

    private B4jDeviceMapper() {}

    static HapticDevice map(ButtplugClientDevice device, long generation) {
        Map<Integer, HapticFeature> features = new LinkedHashMap<>();
        Map<Integer, ButtplugClientDeviceFeature> deviceFeatures = device.getDeviceFeatures();
        if (deviceFeatures != null) {
            deviceFeatures.forEach((index, feature) -> features.put(index, mapFeature(feature)));
        }
        Integer gap = device.getMessageTimingGap();
        return new HapticDevice(
                device.getDeviceIndex(),
                nullToEmpty(device.getName()),
                Optional.ofNullable(emptyToNull(device.getDisplayName())),
                gap == null ? 0 : Math.max(0, gap),
                features,
                generation);
    }

    private static HapticFeature mapFeature(ButtplugClientDeviceFeature feature) {
        Map<OutputKind, OutputCapability> outputs = new LinkedHashMap<>();
        var advertised = feature.getOutput();
        if (advertised != null) {
            for (ButtplugOutput output : advertised.keySet()) {
                OutputKind kind = toOutputKind(output);
                if (kind == OutputKind.UNKNOWN) {
                    continue; // Spray / unknown: represented as unsupported, never routed
                }
                Optional<IntRange> duration = kind.carriesDuration()
                        ? Optional.of(RANGE) : Optional.empty();
                outputs.put(kind, new OutputCapability(kind, RANGE, duration));
            }
        }
        Map<InputKind, InputCapability> inputs = Map.of(); // battery/RSSI unused for output in the MVP
        return new HapticFeature(feature.getFeatureIndex(), nullToEmpty(feature.getDescription()),
                outputs, inputs);
    }

    /** buttplug4j's {@code ButtplugOutput} enum uses the same constant names as our {@link OutputKind}. */
    private static OutputKind toOutputKind(ButtplugOutput output) {
        try {
            return OutputKind.valueOf(output.name());
        } catch (IllegalArgumentException notModelled) {
            return OutputKind.UNKNOWN; // e.g. SPRAY
        }
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String emptyToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
