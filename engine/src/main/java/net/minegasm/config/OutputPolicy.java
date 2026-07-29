package net.minegasm.config;

import java.util.Objects;

/**
 * Per-output-kind policy (brief §9.6). {@code experimental} outputs require explicit opt-in;
 * {@code permanentlyUnsupported} (Spray) can never be enabled regardless of the file contents.
 */
public final class OutputPolicy implements ConfigValue {

    private final boolean enabled;
    private final boolean experimental;
    private final boolean permanentlyUnsupported;

    public OutputPolicy(boolean enabled, boolean experimental, boolean permanentlyUnsupported) {
        this.enabled = enabled;
        this.experimental = experimental;
        this.permanentlyUnsupported = permanentlyUnsupported;
    }

    public boolean enabled() {
        return enabled;
    }

    public boolean experimental() {
        return experimental;
    }

    public boolean permanentlyUnsupported() {
        return permanentlyUnsupported;
    }

    public static OutputPolicy on() {
        return new OutputPolicy(true, false, false);
    }

    public static OutputPolicy off() {
        return new OutputPolicy(false, false, false);
    }

    public static OutputPolicy experimentalOff() {
        return new OutputPolicy(false, true, false);
    }

    public static OutputPolicy forbidden() {
        return new OutputPolicy(false, false, true);
    }

    /** Effective enablement: never true for a permanently unsupported output. */
    public boolean effectivelyEnabled() {
        return enabled && !permanentlyUnsupported;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof OutputPolicy)) {
            return false;
        }
        OutputPolicy other = (OutputPolicy) o;
        return enabled == other.enabled && experimental == other.experimental
                && permanentlyUnsupported == other.permanentlyUnsupported;
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, experimental, permanentlyUnsupported);
    }

    @Override
    public String toString() {
        return "OutputPolicy[enabled=" + enabled + ", experimental=" + experimental
                + ", permanentlyUnsupported=" + permanentlyUnsupported + "]";
    }
}
