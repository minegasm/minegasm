package gg.meza.feelcraft.buttplug;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
public final class ButtplugWebSocketClient implements WebSocket.Listener {
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final Consumer<String> messageConsumer;
    private WebSocket socket;
    private final StringBuilder partial = new StringBuilder();
    public ButtplugWebSocketClient(Consumer<String> messageConsumer) { this.messageConsumer = messageConsumer; }
    public CompletableFuture<WebSocket> connect(URI uri) { return http.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(5)).buildAsync(uri, this).thenApply(ws -> { this.socket = ws; return ws; }); }
    public void send(String json) { WebSocket ws = socket; if (ws != null) ws.sendText(json, true); }
    public void close() { WebSocket ws = socket; if (ws != null) ws.sendClose(WebSocket.NORMAL_CLOSURE, "feelcraft disconnect"); socket = null; }
    @Override public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) { partial.append(data); if (last) { String text = partial.toString(); partial.setLength(0); messageConsumer.accept(text); } webSocket.request(1); return CompletableFuture.completedFuture(null); }
    @Override public void onOpen(WebSocket webSocket) { WebSocket.Listener.super.onOpen(webSocket); webSocket.request(1); }
}
