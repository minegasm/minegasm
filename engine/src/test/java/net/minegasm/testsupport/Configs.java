package net.minegasm.testsupport;

import net.minegasm.config.HapticConfig;
import net.minegasm.config.MinegasmMode;
import net.minegasm.config.RecipePackId;
import net.minegasm.config.RuntimeConfig;

import java.util.HashMap;
import java.util.Map;

/** Test helpers for building deterministic {@link RuntimeConfig}s (variation off, full intensity). */
public final class Configs {

    private Configs() {}

    public static RuntimeConfig enabled(MinegasmMode mode, RecipePackId pack) {
        return build(mode, pack, true, Map.of());
    }

    public static RuntimeConfig disabled() {
        HapticConfig d = HapticConfig.defaults();
        var g = new HapticConfig.Global(false, 1.0, 0.0, false, "STOP", true, "",
                50, 2_000, 100, 10_000);
        return RuntimeConfig.of(rebuild(d, d.identity(), g));
    }

    /** Enabled config with experimental Position + HwPositionWithDuration outputs turned on. */
    public static RuntimeConfig withMotion(MinegasmMode mode, RecipePackId pack) {
        return build(mode, pack, true, Map.of(
                "Position", net.minegasm.config.OutputPolicy.on(),
                "HwPositionWithDuration", net.minegasm.config.OutputPolicy.on()));
    }

    private static RuntimeConfig build(MinegasmMode mode, RecipePackId pack, boolean enabled,
                                       Map<String, net.minegasm.config.OutputPolicy> extraPolicy) {
        HapticConfig d = HapticConfig.defaults();
        var id = new HapticConfig.Identity(pack.name().toLowerCase(java.util.Locale.ROOT), mode.name());
        // Variation 0 and fatigue off for deterministic amplitudes in tests.
        var g = new HapticConfig.Global(enabled, 1.0, 0.0, false, "STOP", true, "",
                50, 2_000, 100, 10_000);
        Map<String, net.minegasm.config.OutputPolicy> policy = new HashMap<>(d.outputPolicy());
        policy.putAll(extraPolicy);
        HapticConfig cfg = new HapticConfig(1, id, g, d.buttplug(), d.events(), policy,
                d.devices(), d.positionCalibrations(), d.accumulation(), d.customIntensity());
        return RuntimeConfig.of(cfg);
    }

    private static HapticConfig rebuild(HapticConfig d, HapticConfig.Identity id, HapticConfig.Global g) {
        return new HapticConfig(1, id, g, d.buttplug(), d.events(), d.outputPolicy(),
                d.devices(), d.positionCalibrations(), d.accumulation(), d.customIntensity());
    }
}
