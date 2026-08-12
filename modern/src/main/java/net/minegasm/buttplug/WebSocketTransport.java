package net.minegasm.buttplug;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * {@link ButtplugTransport} backed by the JDK {@link WebSocket} (brief §6.5). Reassembles partial
 * text frames, requests one message at a time for backpressure, and never invokes Minecraft or
 * engine code directly. It only forwards raw frames to the provider callback.
 *
 * <p>The provider reuses one transport instance across reconnects, so every connect attempt gets its
 * own {@link Conn} listener that owns that attempt's socket and buffer. A completion, message, or close
 * acts only while its {@code Conn} is still the current one, so a slow handshake that finishes after
 * {@link #close}, or a callback from a superseded socket, cannot publish a stale socket or clear a newer
 * connection (review P2-2).
 */
public final class WebSocketTransport implements ButtplugTransport {

    static final int MAX_FRAME_CHARS = 1 << 20; // 1 MiB cap on a single message (brief §12.2)

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final AtomicReference<Conn> current = new AtomicReference<>();
    private volatile Consumer<String> onMessage = m -> {};
    private volatile Consumer<Throwable> onClose = t -> {};

    @Override
    public CompletionStage<Void> connect(URI uri, Consumer<String> onMessage,
                                         Consumer<Throwable> onClose) {
        Conn conn = beginAttempt(onMessage, onClose);
        return http.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .buildAsync(uri, conn)
                .thenAccept(ws -> {
                    // Publish the socket only if this attempt is still the current, un-aborted one;
                    // otherwise a close or a newer connect happened first, so abandon this socket.
                    if (current.get() == conn && !conn.aborted) {
                        conn.socket = ws;
                    } else {
                        ws.abort();
                    }
                });
    }

    /**
     * Register a fresh attempt as the current one, superseding any earlier attempt. Package-private so a
     * test can drive the listener callbacks directly without standing up a real WebSocket server.
     */
    Conn beginAttempt(Consumer<String> onMessage, Consumer<Throwable> onClose) {
        this.onMessage = onMessage == null ? m -> {} : onMessage;
        this.onClose = onClose == null ? t -> {} : onClose;
        Conn conn = new Conn();
        Conn previous = current.getAndSet(conn);
        if (previous != null) {
            previous.abort(); // a new connect supersedes any earlier attempt on this transport
        }
        return conn;
    }

    /** Whether {@code conn} is still the current attempt, for tests to assert generation scoping. */
    boolean isCurrent(Conn conn) {
        return current.get() == conn;
    }

    @Override
    public void send(String frame) {
        Conn c = current.get();
        WebSocket ws = c == null ? null : c.socket;
        if (ws != null && !ws.isOutputClosed()) {
            ws.sendText(frame, true);
        }
    }

    @Override
    public boolean isOpen() {
        Conn c = current.get();
        WebSocket ws = c == null ? null : c.socket;
        return ws != null && !ws.isInputClosed() && !ws.isOutputClosed();
    }

    @Override
    public void close() {
        Conn c = current.getAndSet(null);
        if (c != null) {
            c.abort();
        }
    }

    /**
     * One connection attempt: owns its socket, reassembly buffer, and lifecycle notification. Package-
     * private (not truly private) so the transport's test can create one via {@link #beginAttempt} and
     * exercise the listener callbacks with a fake {@link WebSocket}.
     */
    final class Conn implements WebSocket.Listener {
        private final StringBuilder partial = new StringBuilder();
        private boolean frameOverflowed;
        volatile WebSocket socket;
        volatile boolean aborted;
        private volatile boolean closedNotified;

        void abort() {
            aborted = true;
            WebSocket ws = socket;
            if (ws != null) {
                try {
                    ws.sendClose(WebSocket.NORMAL_CLOSURE, "minegasm disconnect");
                } catch (RuntimeException ignored) {
                    // best effort
                }
            }
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            if (frameOverflowed || partial.length() + data.length() > MAX_FRAME_CHARS) {
                // Reject an oversized message whole: keep consuming its fragments but stop buffering, so
                // it is never handed on as a truncated prefix that parses into a different message (P2-5).
                frameOverflowed = true;
            } else {
                partial.append(data);
            }
            if (last) {
                boolean overflowed = frameOverflowed;
                String text = partial.toString();
                partial.setLength(0);
                frameOverflowed = false;
                if (!overflowed && current.get() == this) {
                    try {
                        onMessage.accept(text);
                    } catch (RuntimeException ignored) {
                        // A handler fault must not kill the read loop.
                    }
                }
            }
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            notifyClosed(null);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            notifyClosed(error);
        }

        private void notifyClosed(Throwable cause) {
            if (closedNotified) {
                return;
            }
            closedNotified = true;
            // Only clear the transport if this attempt is still the current one, so a superseded
            // socket's close callback cannot wipe a newer connection.
            if (!current.compareAndSet(this, null)) {
                return;
            }
            try {
                onClose.accept(cause);
            } catch (RuntimeException ignored) {
                // ignore
            }
        }
    }
}
