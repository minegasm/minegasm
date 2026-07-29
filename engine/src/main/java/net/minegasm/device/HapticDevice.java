package net.minegasm.device;

import net.minegasm.core.OutputKind;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * A device normalized from a Buttplug {@code DeviceList} entry. The {@code registryGeneration}
 * stamps which snapshot this device belongs to; a reused {@code deviceIndex} in a later generation
 * is a different logical device (brief §5.3, §9.5).
 */
public final class HapticDevice {

    private final int deviceIndex;
    private final String deviceName;
    private final Optional<String> displayName;
    private final int messageTimingGapMs;
    private final Map<Integer, HapticFeature> features;
    private final long registryGeneration;

    public HapticDevice(
            int deviceIndex,
            String deviceName,
            Optional<String> displayName,
            int messageTimingGapMs,
            Map<Integer, HapticFeature> features,
            long registryGeneration) {
        if (deviceIndex < 0) {
            throw new IllegalArgumentException("deviceIndex must be >= 0: " + deviceIndex);
        }
        if (messageTimingGapMs < 0) {
            throw new IllegalArgumentException("timing gap must be >= 0: " + messageTimingGapMs);
        }
        this.deviceIndex = deviceIndex;
        this.deviceName = deviceName == null ? "" : deviceName;
        this.displayName = displayName == null ? Optional.empty() : displayName;
        this.messageTimingGapMs = messageTimingGapMs;
        // Sorted, unmodifiable copy for deterministic iteration order.
        this.features = features == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new TreeMap<>(features));
        this.registryGeneration = registryGeneration;
    }

    public int deviceIndex() {
        return deviceIndex;
    }

    public String deviceName() {
        return deviceName;
    }

    public Optional<String> displayName() {
        return displayName;
    }

    public int messageTimingGapMs() {
        return messageTimingGapMs;
    }

    public Map<Integer, HapticFeature> features() {
        return features;
    }

    public long registryGeneration() {
        return registryGeneration;
    }

    /** Human-friendly name for UI: display name if present, else device name. */
    public String label() {
        return displayName.filter(s -> !s.trim().isEmpty()).orElse(deviceName);
    }

    public Optional<HapticFeature> feature(int featureIndex) {
        return Optional.ofNullable(features.get(featureIndex));
    }

    public List<HapticFeature> featuresSupporting(OutputKind kind) {
        return features.values().stream().filter(f -> f.supports(kind)).collect(Collectors.toList());
    }

    /**
     * Best-effort stable identity key for saved preferences across reconnects. Never a raw device
     * index; combines name + a signature of feature output kinds (brief §13.3). Matching remains
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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof HapticDevice)) {
            return false;
        }
        HapticDevice other = (HapticDevice) o;
        return deviceIndex == other.deviceIndex
                && messageTimingGapMs == other.messageTimingGapMs
                && registryGeneration == other.registryGeneration
                && Objects.equals(deviceName, other.deviceName)
                && Objects.equals(displayName, other.displayName)
                && Objects.equals(features, other.features);
    }

    @Override
    public int hashCode() {
        return Objects.hash(deviceIndex, deviceName, displayName, messageTimingGapMs, features,
                registryGeneration);
    }

    @Override
    public String toString() {
        return "HapticDevice[deviceIndex=" + deviceIndex + ", deviceName=" + deviceName
                + ", displayName=" + displayName + ", messageTimingGapMs=" + messageTimingGapMs
                + ", features=" + features + ", registryGeneration=" + registryGeneration + "]";
    }
}
