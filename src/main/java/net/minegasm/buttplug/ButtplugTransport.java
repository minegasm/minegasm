package net.minegasm.buttplug;

import java.net.URI;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

/**
 * Transport abstraction over the Buttplug WebSocket (brief §9.2). The chosen implementation
 * (JDK WebSocket or a future client library) lives behind this interface; no transport/library
 * classes leak into the provider or engine. A {@code FakeTransport} implements this for tests.
 */
public interface ButtplugTransport extends AutoCloseable {

    /**
     * Open the connection. {@code onMessage} receives complete text frames; {@code onClose} is
     * invoked once when the socket closes for any reason (with a cause, or null for a clean close).
     */
    CompletionStage<Void> connect(URI uri, Consumer<String> onMessage, Consumer<Throwable> onClose);

    /** Send a complete text frame. No-op if the transport is not open. */
    void send(String frame);

    boolean isOpen();

    @Override
    void close();
}
