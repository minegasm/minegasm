package net.minegasm.config;

/** Shared policy bounds and defaults for direct test output. */
public final class TestOutputLimits {
    public static final int MIN_PERCENT = 1;
    /** Intensity is normalized to [0,1], so values above 100% have no protocol meaning. */
    public static final int MAX_PERCENT = 100;
    public static final int MIN_DURATION_MS = 100;
    /** Minegasm safety policy, not a Buttplug protocol limit. */
    public static final int MAX_DURATION_MS = 10 * 60 * 1_000;

    public static final int DEFAULT_NORMAL_PERCENT = 50;
    public static final int DEFAULT_NORMAL_DURATION_MS = 2_000;
    public static final int DEFAULT_UNSAFE_PERCENT = 100;
    public static final int DEFAULT_UNSAFE_DURATION_MS = 10_000;

    private TestOutputLimits() {}
}
