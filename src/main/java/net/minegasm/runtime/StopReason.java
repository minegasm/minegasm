package net.minegasm.runtime;

/** Why output was stopped (brief §9.10, §7.11). Drives logging and whether a resume is allowed. */
public enum StopReason {
    PAUSE,
    WORLD_UNLOAD,
    DISCONNECT,
    SHUTDOWN,
    CONFIG_RESET,
    PANIC,
    TRANSPORT_ERROR,
    REGISTRY_INVALIDATED,
    WATCHDOG,
    GAME_INACTIVE,
    CALIBRATION_CANCELLED
}
