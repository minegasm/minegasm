package net.minegasm.testsupport;

import net.minegasm.buttplug.DeviceRegistry;
import net.minegasm.core.OutputKind;
import net.minegasm.device.DeviceRegistrySnapshot;
import net.minegasm.device.HapticDevice;
import net.minegasm.device.HapticFeature;
import net.minegasm.device.OutputCapability;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Test helpers for building device registry snapshots. */
public final class Devices {

    private Devices() {}

    /** A one-device, one-vibration-feature snapshot at generation 1 (range [0,20]). */
    public static DeviceRegistrySnapshot singleVibrate() {
        return registryWith(vibrate(0, "Test Vibe", 0, 0, 20));
    }

    public static DeviceRegistrySnapshot registryWith(HapticDevice... devices) {
        DeviceRegistry registry = new DeviceRegistry();
        return registry.accept(java.util.List.of(devices));
    }

    public static HapticDevice vibrate(int index, String name, int gap, int min, int max) {
        Map<OutputKind, OutputCapability> outputs = new LinkedHashMap<>();
        outputs.put(OutputKind.VIBRATE, OutputCapability.level(OutputKind.VIBRATE, min, max));
        Map<Integer, HapticFeature> features = Map.of(0,
                new HapticFeature(0, "Main motor", outputs, Map.of()));
        return new HapticDevice(index, name, Optional.empty(), gap, features, 0L);
    }

    public static HapticDevice oscillateOnly(int index, String name) {
        Map<OutputKind, OutputCapability> outputs = new LinkedHashMap<>();
        outputs.put(OutputKind.OSCILLATE, OutputCapability.level(OutputKind.OSCILLATE, 0, 100));
        Map<Integer, HapticFeature> features = Map.of(0,
                new HapticFeature(0, "Osc", outputs, Map.of()));
        return new HapticDevice(index, name, Optional.empty(), 0, features, 0L);
    }

    /** A stroker-style device with one HwPositionWithDuration feature ([0,100], duration [0,5000]). */
    public static HapticDevice hwPosition(int index, String name) {
        Map<OutputKind, OutputCapability> outputs = new LinkedHashMap<>();
        outputs.put(OutputKind.HW_POSITION_WITH_DURATION, OutputCapability.withDuration(
                OutputKind.HW_POSITION_WITH_DURATION,
                new net.minegasm.device.IntRange(0, 100),
                new net.minegasm.device.IntRange(0, 5000)));
        Map<Integer, HapticFeature> features = Map.of(0,
                new HapticFeature(0, "Stroke", outputs, Map.of()));
        return new HapticDevice(index, name, Optional.empty(), 0, features, 0L);
    }
}
