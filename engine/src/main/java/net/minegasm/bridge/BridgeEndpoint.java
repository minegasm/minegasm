package net.minegasm.bridge;

import java.net.URI;

/**
 * One resolved bridge endpoint: a stable id, the URI to dial, and the transport that dials it. The
 * client builds one per enabled, allowed bridge in config; {@code HapticRuntime} turns each into its own
 * {@link BridgeBackend}, so several bridges (e.g. an XToys adapter and a DIY device) run at once.
 */
public final class BridgeEndpoint {

    private final String id;
    private final URI uri;
    private final BridgeTransport transport;

    public BridgeEndpoint(String id, URI uri, BridgeTransport transport) {
        this.id = id;
        this.uri = uri;
        this.transport = transport;
    }

    public String id() {
        return id;
    }

    public URI uri() {
        return uri;
    }

    public BridgeTransport transport() {
        return transport;
    }
}
