package net.minegasm.classic;

import net.minegasm.bridge.DownstreamState;
import net.minegasm.client.MinegasmClient;
import net.minegasm.config.HapticConfig;

/**
 * Shared bridge chain-state text for the classic hub screens, so every loader shows the same connection
 * chain: disabled, waiting for the adapter, or the adapter connected plus what it reports about its own
 * onward link (ready, or up but downstream offline). Matches the modern hub and {@code /mg status}.
 */
final class BridgeStatus {

    private BridgeStatus() {
    }

    /** The short state text for one bridge row (e.g. {@code "ready"}, {@code "waiting for adapter"}). */
    static String label(MinegasmClient client, HapticConfig.Bridge bridge) {
        if (!bridge.enabled()) {
            return "off";
        }
        if (client.bridgeFaulted(bridge.name())) {
            return "FAULT";
        }
        if (!client.bridgeConnected(bridge.name())) {
            return "waiting for adapter";
        }
        switch (client.bridgeDownstream(bridge.name())) {
            case READY:
                return "ready";
            case UNAVAILABLE:
                return "downstream offline";
            case UNKNOWN:
            default:
                return "connected";
        }
    }

    /** A rolling hash of every bridge's chain state, so a hub can rebuild when any of it changes. */
    static int hash(MinegasmClient client) {
        int h = 1;
        for (HapticConfig.Bridge b : client.config().raw().bridges()) {
            int state = !b.enabled() ? 0
                    : client.bridgeFaulted(b.name()) ? 1
                    : !client.bridgeConnected(b.name()) ? 2
                    : 3 + downstreamOrdinal(client, b);
            h = h * 31 + state;
        }
        return h;
    }

    private static int downstreamOrdinal(MinegasmClient client, HapticConfig.Bridge b) {
        DownstreamState s = client.bridgeDownstream(b.name());
        return s == null ? 0 : s.ordinal();
    }
}
