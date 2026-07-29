package net.minegasm.buttplug;

import java.util.Objects;
import java.util.Optional;

/**
 * Immutable connection status snapshot, published atomically and read on the client thread (brief
 * §6.5, §9.3). Never carries device-identifying details beyond counts.
 */
public final class ProviderStatus {

    private final ConnectionState state;
    private final Optional<String> negotiatedVersion;
    private final int deviceCount;
    private final Optional<String> lastError;
    private final long registryGeneration;

    public ProviderStatus(ConnectionState state, Optional<String> negotiatedVersion, int deviceCount,
                          Optional<String> lastError, long registryGeneration) {
        this.state = state;
        this.negotiatedVersion = negotiatedVersion;
        this.deviceCount = deviceCount;
        this.lastError = lastError;
        this.registryGeneration = registryGeneration;
    }

    public ConnectionState state() {
        return state;
    }

    public Optional<String> negotiatedVersion() {
        return negotiatedVersion;
    }

    public int deviceCount() {
        return deviceCount;
    }

    public Optional<String> lastError() {
        return lastError;
    }

    public long registryGeneration() {
        return registryGeneration;
    }

    public static ProviderStatus disconnected() {
        return new ProviderStatus(ConnectionState.DISCONNECTED, Optional.empty(), 0,
                Optional.empty(), 0L);
    }

    public boolean isConnected() {
        return state != ConnectionState.DISCONNECTED && state != ConnectionState.CONNECTING;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ProviderStatus)) {
            return false;
        }
        ProviderStatus other = (ProviderStatus) o;
        return deviceCount == other.deviceCount
                && registryGeneration == other.registryGeneration
                && state == other.state
                && Objects.equals(negotiatedVersion, other.negotiatedVersion)
                && Objects.equals(lastError, other.lastError);
    }

    @Override
    public int hashCode() {
        return Objects.hash(state, negotiatedVersion, deviceCount, lastError, registryGeneration);
    }

    @Override
    public String toString() {
        return "ProviderStatus[state=" + state + ", negotiatedVersion=" + negotiatedVersion
                + ", deviceCount=" + deviceCount + ", lastError=" + lastError
                + ", registryGeneration=" + registryGeneration + "]";
    }
}
