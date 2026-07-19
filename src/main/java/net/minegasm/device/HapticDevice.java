package net.minegasm.device;

import net.minegasm.core.OutputKind;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * A device normalized from a Buttplug {@code DeviceList} entry. The {@code registryGeneration}
 * stamps which snapshot this device belongs to; a reused {@code deviceIndex} in a later generation
 * is a different logical device (brief Â§5.3, Â§9.5).
 */
public record HapticDevice(
        int deviceIndex,
        String deviceName,
        Optional<String> displayName,
        int messageTimingGapMs,
        Map<Integer, HapticFeature> features,
        long registryGeneration) {

    public HapticDevice {
        if (deviceIndex < 0) {
            throw new IllegalArgumentException("deviceIndex must be >= 0: " + deviceIndex);
        }
        deviceName = deviceName == null ? "" : deviceName;
        displayName = displayName == null ? Optional.empty() : displayName;
        if (messageTimingGapMs < 0) {
            throw new IllegalArgumentException("timing gap must be >= 0: " + messageTimingGapMs);
        }
        // Sorted, unmodifiable copy for deterministic iteration order.
        features = features == null
                ? Map.of()
                : Collections.unmodifiableMap(new TreeMap<>(features));
    }

    /** Human-friendly name for UI: display name if present, else device name. */
    public String label() {
        return displayName.filter(s -> !s.isBlank()).orElse(deviceName);
    }

    public Optional<HapticFeature> feature(int featureIndex) {
        return Optional.ofNullable(features.get(featureIndex));
    }

    public List<HapticFeature> featuresSupporting(OutputKind kind) {
        return features.values().stream().filter(f -> f.supports(kind)).toList();
    }

    /**
     * Best-effort stable identity key for saved preferences across reconnects. Never a raw device
     * index; combines name + a signature of feature output kinds (brief Â§13.3). Matching remains
     * probabilistic and must be surfaced to users.
     */
    public String identityKey() {
        StringBuilder sig = new StringBuilder(label()).append('|');
        features.values().forEach(f -> {
            sig.append(f.featureIndex()).append(':');
            f.outputs().keySet().stream().sorted().forEach(k -> sig.append(k.wireName()).append(','));
            sig.append(';');
        });
        return sig.toString();
    }
}

