package net.minegasm.config;

/** Auto-reconnect policy (brief §13.2). Bounded exponential backoff with jitter. */
public final class ReconnectParams implements ConfigValue {

    private final boolean enabled;
    private final int maxDelaySeconds;

    public ReconnectParams(boolean enabled, int maxDelaySeconds) {
        this.enabled = enabled;
        this.maxDelaySeconds = maxDelaySeconds <= 0 ? 30 : maxDelaySeconds;
    }

    public boolean enabled() {
        return enabled;
    }

    public int maxDelaySeconds() {
        return maxDelaySeconds;
    }

    public static ReconnectParams defaults() {
        return new ReconnectParams(true, 30);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ReconnectParams)) {
            return false;
        }
        ReconnectParams other = (ReconnectParams) o;
        return enabled == other.enabled && maxDelaySeconds == other.maxDelaySeconds;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(enabled, maxDelaySeconds);
    }

    @Override
    public String toString() {
        return "ReconnectParams[enabled=" + enabled + ", maxDelaySeconds=" + maxDelaySeconds + "]";
    }
}
