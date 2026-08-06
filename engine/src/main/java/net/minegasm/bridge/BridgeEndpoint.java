package net.minegasm.bridge;

import java.net.URI;
import java.util.function.Supplier;

/**
 * One resolved bridge endpoint: a stable id, the URI to dial, and a factory for the transport that dials
 * it. The client builds one per enabled, allowed bridge in config; {@code HapticRuntime} turns each into
 * its own {@link BridgeBackend}, so several bridges (e.g. an XToys adapter and a DIY device) run at once.
 *
 * <p>The transport is a <em>factory</em>, not a single instance, so the backend can dial a fresh socket
 * on every (re)connect: it retries until the adapter is up and reconnects if it restarts.
 */
public final class BridgeEndpoint {

    private final String id;
    private final URI uri;
    private final Supplier<BridgeTransport> transportFactory;

    public BridgeEndpoint(String id, URI uri, Supplier<BridgeTransport> transportFactory) {
        this.id = id;
        this.uri = uri;
        this.transportFactory = transportFactory;
    }

    public String id() {
        return id;
    }

    public URI uri() {
        return uri;
    }

    public Supplier<BridgeTransport> transportFactory() {
        return transportFactory;
    }
}
