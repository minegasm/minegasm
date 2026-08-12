package net.minegasm.bridge;

/**
 * What an adapter reports about its own onward connection (for XToys, the webhook WebSocket; for DIY
 * hardware, the device link). The mod-to-adapter socket being open only means the adapter is running;
 * this is the next link in the chain, reported by the adapter over the bridge protocol's {@code hello}
 * and {@code status} messages (see docs/bridge/PROTOCOL.md).
 *
 * <p>{@link #UNKNOWN} is the honest default: an older adapter that predates these messages sends nothing,
 * so the mod cannot tell and should not claim the downstream is up. It resets to UNKNOWN on a disconnect.
 */
public enum DownstreamState {
    /** The adapter has not reported, so its onward link is unknown (e.g. an older adapter). */
    UNKNOWN,
    /** The adapter reports its onward link is up and ready to drive output. */
    READY,
    /** The adapter reports its onward link is down (e.g. the XToys webhook is not connected). */
    UNAVAILABLE
}
