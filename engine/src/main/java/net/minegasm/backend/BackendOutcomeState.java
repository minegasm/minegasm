package net.minegasm.backend;

/** Observable lifecycle of a non-blocking backend operation. */
public enum BackendOutcomeState {
    ACCEPTED,
    DELIVERED,
    FAILED,
    TIMED_OUT,
    SUPERSEDED
}
