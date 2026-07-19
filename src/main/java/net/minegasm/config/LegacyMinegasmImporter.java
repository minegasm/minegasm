package net.minegasm.config;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Imports a legacy Minegasm client config (TOML) into a {@link HapticConfig} (brief §3.5). It
 * parses without modifying the original, produces a preview of the mapped values, never silently
 * enables experimental output types, and maps the legacy mode/intensities into CUSTOM-mode base
 * intensities plus the Classic recipe pack for faithful replacement behaviour.
 *
 * <p>Only the small, well-known key set is read; a full TOML parser is intentionally avoided.
 */
public final class LegacyMinegasmImporter {

    private LegacyMinegasmImporter() {}

    /** The mapped result plus a human-readable, non-sensitive preview of what changed. */
    public record ImportPreview(HapticConfig result, Map<String, String> summary) {}

    /**
     * Parse legacy TOML text and produce a preview built on top of {@code base} (usually current
     * defaults). Unrecognised keys are ignored; recognised keys populate the summary.
     */
    public static ImportPreview fromToml(String tomlText, HapticConfig base) {
        Map<String, String> flat = parseToml(tomlText);
        Map<String, String> leaves = leafKeys(flat);
        Map<String, String> summary = new LinkedHashMap<>();

        HapticConfig start = base == null ? HapticConfig.defaults() : base;

        // Connection URL.
        String serverUrl = firstNonNull(flat.get("buttplug.serverUrl"), leaves.get("serverurl"),
                start.buttplug().serverUrl());
        summary.put("serverUrl", serverUrl);

        // Master enable (vibration is non-experimental, safe to carry over).
        boolean enabled = parseBool(firstNonNull(leaves.get("vibrate"), leaves.get("enabled")),
                start.global().enabled());
        summary.put("enabled", Boolean.toString(enabled));

        // Mode. Imported configs replay through the Classic recipe pack for parity.
        MinegasmMode mode = MinegasmMode.fromString(leaves.get("mode"), MinegasmMode.NORMAL);
        summary.put("mode", mode.name());
        summary.put("recipePack", RecipePackId.CLASSIC.name());

        // Intensities (legacy stored 0..100 ints or 0..1 doubles; normalise either).
        CustomIntensities ci = new CustomIntensities(
                intensity(leaves, "attackintensity", start.customIntensity().attack(), summary, "attack"),
                intensity(leaves, "hurtintensity", start.customIntensity().hurt(), summary, "hurt"),
                intensity(leaves, "mineintensity", start.customIntensity().mine(), summary, "mine"),
                intensity(leaves, "placeintensity", start.customIntensity().place(), summary, "place"),
                intensity(leaves, "xpchangeintensity", start.customIntensity().xpChange(), summary, "xpChange"),
                intensity(leaves, "fishingintensity", start.customIntensity().fishing(), summary, "fishing"),
                intensity(leaves, "harvestintensity", start.customIntensity().harvest(), summary, "harvest"),
                intensity(leaves, "vitalityintensity", start.customIntensity().vitality(), summary, "vitality"),
                intensity(leaves, "advancementintensity", start.customIntensity().advancement(), summary, "advancement"));

        HapticConfig.Buttplug bp = new HapticConfig.Buttplug(
                serverUrl, start.buttplug().autoConnect(), start.buttplug().autoScan(),
                start.buttplug().allowRemoteServer(), start.buttplug().reconnect(),
                start.buttplug().client());
        HapticConfig.Global global = new HapticConfig.Global(
                enabled, start.global().intensity(), start.global().variation(),
                start.global().fatigueProtection(), start.global().pauseBehavior(),
                start.global().stopOnWorldUnload(), start.global().panicKey(),
                start.global().testMaxPercent(), start.global().testMaxDurationMs(),
                start.global().unsafeTestMaxPercent(), start.global().unsafeTestMaxDurationMs());
        HapticConfig.Identity identity = new HapticConfig.Identity(
                RecipePackId.CLASSIC.name().toLowerCase(java.util.Locale.ROOT), mode.name());

        HapticConfig result = new HapticConfig(
                HapticConfig.CURRENT_SCHEMA_VERSION,
                identity, global, bp,
                start.events(), start.outputPolicy(), start.devices(),
                start.positionCalibrations(), start.accumulation(), ci);

        return new ImportPreview(result, summary);
    }

    // --- helpers ---------------------------------------------------------------------------

    private static double intensity(Map<String, String> leaves, String key, double fallback,
                                    Map<String, String> summary, String label) {
        String raw = leaves.get(key);
        if (raw == null) {
            return fallback;
        }
        double v;
        try {
            v = Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
        // Heuristic: legacy 0..100 integer scale vs modern 0..1.
        if (v > 1.0) {
            v = v / 100.0;
        }
        double clamped = v < 0 ? 0 : Math.min(v, 1.0);
        summary.put(label + "Intensity", String.format(java.util.Locale.ROOT, "%.2f", clamped));
        return clamped;
    }

    private static boolean parseBool(String raw, boolean fallback) {
        if (raw == null) {
            return fallback;
        }
        String s = raw.trim().toLowerCase(java.util.Locale.ROOT);
        return switch (s) {
            case "true", "1", "yes", "on" -> true;
            case "false", "0", "no", "off" -> false;
            default -> fallback;
        };
    }

    private static String firstNonNull(String... values) {
        for (String v : values) {
            if (v != null) {
                return v;
            }
        }
        return null;
    }

    /** Map from leaf key name (lower-cased, without section) to value, for tolerant lookups. */
    private static Map<String, String> leafKeys(Map<String, String> flat) {
        Map<String, String> leaves = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : flat.entrySet()) {
            String full = e.getKey();
            int dot = full.lastIndexOf('.');
            String leaf = (dot >= 0 ? full.substring(dot + 1) : full).toLowerCase(java.util.Locale.ROOT);
            leaves.putIfAbsent(leaf, e.getValue());
        }
        return leaves;
    }

    /**
     * Minimal TOML reader: {@code [section]} / {@code [a.b]} headers and {@code key = value} lines,
     * with {@code #} comments and quoted strings. Sufficient for the fixed legacy key set; not a
     * general TOML parser.
     */
    static Map<String, String> parseToml(String text) {
        Map<String, String> out = new LinkedHashMap<>();
        if (text == null) {
            return out;
        }
        String section = "";
        for (String rawLine : text.split("\r?\n")) {
            String line = stripComment(rawLine).trim();
            if (line.isEmpty()) {
                continue;
            }
            if (line.startsWith("[") && line.endsWith("]")) {
                section = line.substring(1, line.length() - 1).trim();
                continue;
            }
            int eq = line.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = line.substring(0, eq).trim();
            String value = unquote(line.substring(eq + 1).trim());
            String fullKey = section.isEmpty() ? key : section + "." + key;
            out.put(fullKey, value);
        }
        return out;
    }

    private static String stripComment(String line) {
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == '#' && !inQuotes) {
                return line.substring(0, i);
            }
        }
        return line;
    }

    private static String unquote(String v) {
        if (v.length() >= 2 && v.startsWith("\"") && v.endsWith("\"")) {
            return v.substring(1, v.length() - 1);
        }
        return v;
    }
}
