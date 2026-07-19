package net.minegasm.buttplug;

import net.minegasm.device.DeviceRegistrySnapshot;
import net.minegasm.device.HapticDevice;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Holds the current device snapshot and increments a generation on every accepted {@code DeviceList}
 * (brief §5.3, §9.5). A reused {@code DeviceIndex} in a later generation is a different logical
 * device; scheduled commands captured against an older generation are dropped by the provider.
 *
 * <p>The snapshot is swapped atomically so readers on any thread always see a consistent view.
 */
public final class DeviceRegistry {

    private final AtomicReference<DeviceRegistrySnapshot> current =
            new AtomicReference<>(DeviceRegistrySnapshot.empty());

    /** Replace the registry with a full snapshot, returning the new snapshot with its generation. */
    public DeviceRegistrySnapshot accept(List<HapticDevice> devices) {
        return current.updateAndGet(prev -> {
            long generation = prev.generation() + 1;
            Map<Integer, HapticDevice> stamped = new LinkedHashMap<>();
            for (HapticDevice d : devices) {
                // Re-stamp the generation the parser left at 0.
                stamped.put(d.deviceIndex(), new HapticDevice(
                        d.deviceIndex(), d.deviceName(), d.displayName(),
                        d.messageTimingGapMs(), d.features(), generation));
            }
            return new DeviceRegistrySnapshot(generation, stamped);
        });
    }

    public DeviceRegistrySnapshot snapshot() {
        return current.get();
    }

    /** Clear on disconnect/uncertain state so no stale device can be targeted (brief §9.3). */
    public DeviceRegistrySnapshot clear() {
        return current.updateAndGet(prev ->
                new DeviceRegistrySnapshot(prev.generation() + 1, Map.of()));
    }
}
