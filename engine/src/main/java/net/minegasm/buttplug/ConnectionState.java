package net.minegasm.buttplug;

/**
 * Provider connection state machine (brief §13.1). Every transition is observable in UI and logs;
 * an invalid transition is a bug and should trigger a safe stop.
 */
public enum ConnectionState {
    DISCONNECTED,
    CONNECTING,
    NEGOTIATING,
    CONNECTED_NO_DEVICES,
    SCANNING,
    READY,
    DEGRADED,
    STOPPING
}
