package net.minegasm.runtime;

/**
 * An independent reason output is held off, tracked as a set rather than a single overwriting state
 * (second follow-up review P1-2). Modelling causes independently is what makes the transitions safe: a
 * user panic and a watchdog stop can both be active, and clearing one never clears the other, so a
 * watchdog can't wipe a user panic and a resume can't clear a watchdog stall.
 *
 * <p>The worker owns the two runtime causes it can enter and clear ({@link #USER_STOP}, {@link #WATCHDOG}).
 * The client folds in {@link #DISABLED} (master output off in config) and {@link #BACKEND_FAULT} (a
 * quarantined backend) when it builds an {@link OutputStatus} for the UI, so every screen reads one truth.
 */
public enum StopCause {
    /** The user pressed panic; only an explicit user resume clears it. */
    USER_STOP,
    /** The watchdog stopped output after a stall; only watchdog recovery on healthy cycles clears it. */
    WATCHDOG,
    /** Master output is disabled in config (the enable toggle is off). */
    DISABLED,
    /** At least one backend is quarantined after a render fault. */
    BACKEND_FAULT
}
