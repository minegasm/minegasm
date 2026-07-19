package net.minegasm.buttplug;

import java.util.Optional;

/**
 * Immutable connection status snapshot, published atomically and read on the client thread (brief
 * §6.5, §9.3). Never carries device-identifying details beyond counts.
 */
public record ProviderStatus(
        ConnectionState state,
        Optional<String> negotiatedVersion,
        int deviceCount,
        Optional<String> lastError,
        long registryGeneration) {

    public static ProviderStatus disconnected() {
        return new ProviderStatus(ConnectionState.DISCONNECTED, Optional.empty(), 0,
                Optional.empty(), 0L);
    }

    public boolean isConnected() {
        return state != ConnectionState.DISCONNECTED && state != ConnectionState.CONNECTING;
    }
}
