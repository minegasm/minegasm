package net.minegasm.config;

import java.util.Objects;

/**
 * Per-output-kind enablement. A kind that is not enabled is never chosen by the mixer
 * ({@code SceneMixer.chooseKind}). This is only an on/off switch; which kinds a layer may drive is
 * further constrained by the layer's route and the device's features, and motion (stroker) output is
 * additionally gated by per-device calibration. Kinds no route targets need no entry here.
 */
public final class OutputPolicy implements ConfigValue {

    private final boolean enabled;

    public OutputPolicy(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean enabled() {
        return enabled;
    }

    public static OutputPolicy on() {
        return new OutputPolicy(true);
    }

    public static OutputPolicy off() {
        return new OutputPolicy(false);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof OutputPolicy)) {
            return false;
        }
        return enabled == ((OutputPolicy) o).enabled;
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled);
    }

    @Override
    public String toString() {
        return "OutputPolicy[enabled=" + enabled + "]";
    }
}
