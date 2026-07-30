package net.minegasm.recipe;

import net.minegasm.config.MinegasmMode;
import net.minegasm.config.RuntimeConfig;
import net.minegasm.core.GameEventKind;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A data-driven mode preset: per-event base intensity (0..1) representing "how much this mode wants
 * this event felt" (brief §3.3, §11.4). Base 0 means the event is disabled in this mode, mirroring
 * the legacy short-circuit where a zero base never fires.
 *
 * <p>CUSTOM and MOMENTUM are resolved from config at runtime rather than from a fixed table.
 */
public final class Preset {

    private final MinegasmMode mode;
    private final Map<GameEventKind, Float> baseByEvent;

    public Preset(MinegasmMode mode, Map<GameEventKind, Float> baseByEvent) {
        this.mode = mode;
        this.baseByEvent = baseByEvent == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(baseByEvent));
    }

    public MinegasmMode mode() {
        return mode;
    }

    public Map<GameEventKind, Float> baseByEvent() {
        return baseByEvent;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Preset)) {
            return false;
        }
        Preset other = (Preset) o;
        return mode == other.mode && Objects.equals(baseByEvent, other.baseByEvent);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mode, baseByEvent);
    }

    @Override
    public String toString() {
        return "Preset[mode=" + mode + ", baseByEvent=" + baseByEvent + "]";
    }
}
