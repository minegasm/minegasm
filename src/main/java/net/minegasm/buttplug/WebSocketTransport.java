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
 * engine code directly — it only forwards raw frames to the provider callback.
 */
public final class WebSocketTransport implements ButtplugTransport, WebSocket.Listener {

    private static final int MAX_FRAME_CHARS = 1 << 20; // 1 MiB cap on a single message (brief §12.2)

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final StringBuilder partial = new StringBuilder();
    private final AtomicReference<WebSocket> socket = new AtomicReference<>();
    private volatile Consumer<String> onMessage = m -> {};
    private volatile Consumer<Throwable> onClose = t -> {};
    private volatile boolean closedNotified;

    @Override
    public CompletionStage<Void> connect(URI uri, Consumer<String> onMessage,
                                         Consumer<Throwable> onClose) {
        this.onMessage = onMessage;
        this.onClose = onClose;
        this.closedNotified = false;
        return http.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .buildAsync(uri, this)
                .thenAccept(socket::set);
    }

    @Override
    public void send(String frame) {
        WebSocket ws = socket.get();
        if (ws != null && !ws.isOutputClosed()) {
            ws.sendText(frame, true);
        }
    }

    @Override
    public boolean isOpen() {
        WebSocket ws = socket.get();
        return ws != null && !ws.isInputClosed() && !ws.isOutputClosed();
    }

    @Override
    public void close() {
        WebSocket ws = socket.getAndSet(null);
        if (ws != null) {
            try {
                ws.sendClose(WebSocket.NORMAL_CLOSURE, "minegasm disconnect");
            } catch (RuntimeException ignored) {
                // Best effort.
            }
        }
    }

    // --- WebSocket.Listener ----------------------------------------------------------------

    @Override
    public void onOpen(WebSocket webSocket) {
        webSocket.request(1);
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        if (partial.length() + data.length() <= MAX_FRAME_CHARS) {
            partial.append(data);
        }
        if (last) {
            String text = partial.toString();
            partial.setLength(0);
            try {
                onMessage.accept(text);
            } catch (RuntimeException ignored) {
                // A handler fault must not kill the read loop.
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
        socket.set(null);
        try {
            onClose.accept(cause);
        } catch (RuntimeException ignored) {
            // ignore
        }
    }
}
