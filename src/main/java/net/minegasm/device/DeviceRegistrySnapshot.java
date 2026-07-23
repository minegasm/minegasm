package net.minegasm.device;

import net.minegasm.core.OutputKind;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Immutable snapshot of all known devices at a single registry generation. Every accepted
 * {@code DeviceList} produces a new snapshot with an incremented generation; scheduled commands
 * capture the generation and are dropped if it no longer matches (brief §5.3, §9.5).
 */
public record DeviceRegistrySnapshot(long generation, Map<Integer, HapticDevice> devices) {

    private static final DeviceRegistrySnapshot EMPTY =
            new DeviceRegistrySnapshot(0L, Map.of());

    public DeviceRegistrySnapshot {
        devices = devices == null
                ? Map.of()
                : Collections.unmodifiableMap(new TreeMap<>(devices));
    }

    public static DeviceRegistrySnapshot empty() {
        return EMPTY;
    }

    public Optional<HapticDevice> device(int deviceIndex) {
        return Optional.ofNullable(devices.get(deviceIndex));
    }

    public boolean isEmpty() {
        return devices.isEmpty();
    }

    public List<HapticDevice> all() {
        return new ArrayList<>(devices.values());
    }

    /**
     * Resolve a {@link FeatureRef} against this snapshot, verifying generation, device presence,
     * feature presence, and that the requested output kind is still advertised. Returns empty if any
     * check fails; this is the single gate the scheduler consults before every dispatch (brief §10.5).
     */
    public Optional<HapticFeature> resolve(FeatureRef ref, OutputKind kind) {
        if (ref.registryGeneration() != generation) {
            return Optional.empty();
        }
        return device(ref.deviceIndex())
                .flatMap(d -> d.feature(ref.featureIndex()))
                .filter(f -> f.supports(kind));
    }
}

