package net.minegasm.recipe;

import net.minegasm.config.MinegasmMode;
import net.minegasm.config.RuntimeConfig;
import net.minegasm.core.GameEventKind;

import java.util.Map;

/**
 * A data-driven mode preset: per-event base intensity (0..1) representing "how much this mode wants
 * this event felt" (brief §3.3, §11.4). Base 0 means the event is disabled in this mode, mirroring
 * the legacy short-circuit where a zero base never fires.
 *
 * <p>CUSTOM and ACCUMULATION are resolved from config at runtime rather than from a fixed table.
 */
public record Preset(MinegasmMode mode, Map<GameEventKind, Float> baseByEvent) {

    public Preset {
        baseByEvent = baseByEvent == null ? Map.of() : Map.copyOf(baseByEvent);
    }

    /**
     * Base intensity for an event in this preset, consulting config for CUSTOM mode. Returns 0 when
     * the event is disabled in this mode.
     */
    public float baseFor(GameEventKind kind, RuntimeConfig config) {
        if (mode == MinegasmMode.CUSTOM) {
            return config.customIntensity(kind);
        }
        return baseByEvent.getOrDefault(kind, 0f);
    }

    public boolean enables(GameEventKind kind, RuntimeConfig config) {
        return baseFor(kind, config) > 0f;
    }
}
