package net.minegasm.classic;

import net.minegasm.buttplug.ButtplugTransport;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.extensions.IExtension;
import org.java_websocket.handshake.ServerHandshake;

import javax.net.ssl.SSLContext;
import java.net.URI;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * {@link ButtplugTransport} for the classic (Java 8) loaders, backed by the bundled Java-WebSocket
 * client (ADR-019). It matches the modern JDK-WebSocket transport's contract: complete text frames are
 * forwarded to the provider, the close callback fires once, and a handler fault never kills the read
 * loop. The 1 MiB inbound frame cap (SAFETY.md) is enforced by the draft's {@code maxFrameSize}, which
 * rejects an oversized frame at its length header before any payload is allocated.
 */
public final class ClassicWebSocketTransport implements ButtplugTransport {

    private static final int MAX_FRAME_BYTES = 1 << 20; // 1 MiB, matching modern's MAX_FRAME_CHARS

    private final AtomicReference<WebSocketClient> socket = new AtomicReference<WebSocketClient>();
    private volatile Consumer<String> onMessage = m -> { };
    private volatile Consumer<Throwable> onClose = t -> { };
    private volatile boolean closedNotified;

    @Override
    public CompletionStage<Void> connect(URI uri, Consumer<String> onMessage,
                                         Consumer<Throwable> onClose) {
        this.onMessage = onMessage;
        this.onClose = onClose;
        this.closedNotified = false;

        final CompletableFuture<Void> ready = new CompletableFuture<Void>();
        Draft draft = new Draft_6455(Collections.<IExtension>emptyList(), MAX_FRAME_BYTES);
        WebSocketClient client = new WebSocketClient(uri, draft) {
            @Override
            public void onOpen(ServerHandshake handshake) {
                ready.complete(null);
            }

            @Override
            public void onMessage(String message) {
                deliver(message);
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                notifyClosed(null);
            }

            @Override
            public void onError(Exception ex) {
                if (!ready.isDone()) {
                    ready.completeExceptionally(ex);
                }
                notifyClosed(ex);
            }
        };

        if ("wss".equalsIgnoreCase(uri.getScheme())) {
            try {
                client.setSocketFactory(SSLContext.getDefault().getSocketFactory());
            } catch (Exception tlsFailure) {
                ready.completeExceptionally(tlsFailure);
                return ready;
            }
        }

        socket.set(client);
        client.connect();
        return ready;
    }

    @Override
    public void send(String frame) {
        WebSocketClient ws = socket.get();
        if (ws != null && ws.isOpen()) {
            try {
                ws.send(frame);
            } catch (RuntimeException ignored) {
                // Best effort; a send after a race with close must not throw into the caller.
            }
        }
    }

    @Override
    public boolean isOpen() {
        WebSocketClient ws = socket.get();
        return ws != null && ws.isOpen();
    }

    @Override
    public void close() {
        WebSocketClient ws = socket.getAndSet(null);
        if (ws != null) {
            try {
                ws.close();
            } catch (RuntimeException ignored) {
                // Best effort.
            }
        }
    }

    private void deliver(String message) {
        try {
            onMessage.accept(message);
        } catch (RuntimeException ignored) {
            // A handler fault must not kill the read loop.
        }
    }

    private void notifyClosed(Throwable cause) {
        if (closedNotified) {
            return;
        }
        closedNotified = true;
        socket.set(null);
        try {
            onClose.accept(cause);
        } catch (RuntimeException ignored) {
            // ignore
        }
    }
}
