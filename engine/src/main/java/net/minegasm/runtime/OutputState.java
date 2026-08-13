package net.minegasm.runtime;

/**
 * Why the driver's master output is or isn't flowing, as one explicit state instead of an overloaded
 * boolean (review UX #1). The banner, the resume affordance, and the watchdog's recovery all key off this
 * so they can never disagree about whether output is stopped or why.
 *
 * <p>This is the runtime stop reason only; whether the user has master output enabled at all is a separate
 * config setting. {@link #RUNNING} means the runtime is not itself holding output off.
 */
public enum OutputState {
    /** Output is not held off by the runtime (it may still be off because the user disabled it in config). */
    RUNNING,
    /** The user pressed panic; output stays off until they explicitly resume. */
    USER_STOPPED,
    /** The watchdog stopped output after a stall; it auto-recovers once healthy cycles resume. */
    WATCHDOG_STOPPED
}
