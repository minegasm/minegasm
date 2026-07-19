package net.minegasm.config;

/**
 * Per-output-kind policy (brief §9.6). {@code experimental} outputs require explicit opt-in;
 * {@code permanentlyUnsupported} (Spray) can never be enabled regardless of the file contents.
 */
public record OutputPolicy(boolean enabled, boolean experimental, boolean permanentlyUnsupported) {

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
}
