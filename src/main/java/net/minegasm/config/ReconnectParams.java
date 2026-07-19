package net.minegasm.config;

/** Auto-reconnect policy (brief §13.2). Bounded exponential backoff with jitter. */
public record ReconnectParams(boolean enabled, int maxDelaySeconds) {

    public ReconnectParams {
        if (maxDelaySeconds <= 0) {
            maxDelaySeconds = 30;
        }
    }

    public static ReconnectParams defaults() {
        return new ReconnectParams(true, 30);
    }
}
