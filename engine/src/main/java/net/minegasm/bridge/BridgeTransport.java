package net.minegasm.bridge;

import java.net.URI;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

/**
 * Transport abstraction over the local bridge connection (brief 0002 §4.3). Minegasm is an outbound
 * client: it dials {@code uri} (loopback by default) and never opens a listener. The concrete
 * implementation (a JDK WebSocket, or OSC) lives in the loader layer so the engine stays free of
 * {@code java.net.http} and compiles as Java 8 for the Classic build; a {@code FakeBridgeTransport}
 * implements this for tests. Mirrors {@code ButtplugTransport}.
 */
public interface BridgeTransport extends AutoCloseable {

    /**
     * Open the connection. {@code onMessage} receives complete text frames the adapter sends back
     * (acks/health); {@code onClose} fires once when the socket closes (with a cause, or null for a
     * clean close).
     */
    CompletionStage<Void> connect(URI uri, Consumer<String> onMessage, Consumer<Throwable> onClose);

    /** Send a complete text frame. Must be non-blocking; a no-op if the transport is not open. */
    void send(String frame);

    boolean isOpen();

    @Override
    void close();
}
