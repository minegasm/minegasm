package net.minegasm.classic;

import net.minegasm.device.HapticDevice;

import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Minecraft-free formatting for the device rows the Classic config screens draw. The capability summary is
 * pure engine logic (it reads {@link HapticDevice} feature/output kinds), so keeping it here lets every
 * Classic version render the same line without copying the stream. The 1.16.5 list widget and the legacy
 * text panels both call this.
 */
public final class ClassicDeviceFormat {

    private ClassicDeviceFormat() {
    }

    /** The device's display name (delegates to the engine so the wording matches everywhere). */
    public static String label(HapticDevice device) {
        return device.label();
    }

    /**
     * A short, comma-separated summary of the device's renderable outputs (e.g. {@code "vibrate x2,
     * rotate"}), or a placeholder when the device exposes none Minegasm can drive.
     */
    public static String capabilities(HapticDevice device) {
        Map<String, Long> counts = device.features().values().stream()
                .flatMap(feature -> feature.outputs().keySet().stream())
                .filter(kind -> kind.renderableWireName().isPresent())
                .collect(Collectors.groupingBy(kind -> kind.wireName(), TreeMap::new,
                        Collectors.counting()));
        String detail = counts.entrySet().stream()
                .map(e -> e.getValue() > 1 ? e.getKey() + " x" + e.getValue() : e.getKey())
                .collect(Collectors.joining(", "));
        return detail.isEmpty() ? "No supported output" : detail;
    }
}
