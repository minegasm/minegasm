package net.minegasm.buttplug;

import org.junit.jupiter.api.Test;

import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drives {@link WebSocketTransport}'s per-connect listener directly with a fake {@link WebSocket}, so the
 * reassembly, whole-message overflow rejection, and generation scoping (review P2-2, P2-5) are exercised
 * without standing up a real server. The handshake wiring itself still wants an in-game smoke test.
 */
class WebSocketTransportTest {

    @Test
    void reassemblesPartialFramesIntoOneMessage() {
        WebSocketTransport transport = new WebSocketTransport();
        List<String> messages = new ArrayList<>();
        WebSocketTransport.Conn conn = transport.beginAttempt(messages::add, t -> {});
        FakeWebSocket ws = new FakeWebSocket();

        conn.onOpen(ws);
        conn.onText(ws, "{\"ok\"", false);
        conn.onText(ws, ":1}", true);

        assertEquals(List.of("{\"ok\":1}"), messages, "partial frames are reassembled into one message");
    }

    @Test
    void rejectsAnOversizedMessageWholeThenRecovers() {
        WebSocketTransport transport = new WebSocketTransport();
        List<String> messages = new ArrayList<>();
        WebSocketTransport.Conn conn = transport.beginAttempt(messages::add, t -> {});
        FakeWebSocket ws = new FakeWebSocket();
        conn.onOpen(ws);

        // One fragment at the cap, then more: the message goes over and must be dropped whole, not handed
        // on as a truncated prefix.
        conn.onText(ws, new String(new char[WebSocketTransport.MAX_FRAME_CHARS]), false);
        conn.onText(ws, "xxxx", true);
        assertTrue(messages.isEmpty(), "an oversized message is rejected whole, not delivered truncated");

        // The next normal message is delivered, so overflow state reset with the completed message.
        conn.onText(ws, "{\"ok\":2}", true);
        assertEquals(List.of("{\"ok\":2}"), messages, "a normal message after an overflow still delivers");
    }

    @Test
    void aSupersededAttemptNeitherDeliversNorClearsTheCurrentOne() {
        WebSocketTransport transport = new WebSocketTransport();
        List<String> first = new ArrayList<>();
        List<String> second = new ArrayList<>();
        List<Throwable> firstClosed = new ArrayList<>();

        WebSocketTransport.Conn c1 = transport.beginAttempt(first::add, firstClosed::add);
        WebSocketTransport.Conn c2 = transport.beginAttempt(second::add, t -> {}); // supersedes c1
        FakeWebSocket ws = new FakeWebSocket();

        assertTrue(c1.aborted, "the earlier attempt is aborted when superseded");
        assertTrue(transport.isCurrent(c2), "the newer attempt is current");

        // A late message on the superseded attempt must not be delivered.
        c1.onText(ws, "stale", true);
        assertTrue(first.isEmpty(), "a superseded attempt does not deliver messages");

        // A close callback from the superseded attempt must not clear the newer connection or fire its
        // close handler.
        c1.onClose(ws, WebSocket.NORMAL_CLOSURE, "bye");
        assertTrue(firstClosed.isEmpty(), "a superseded attempt's close does not fire onClose");
        assertTrue(transport.isCurrent(c2), "a superseded attempt's close does not clear the current one");

        // The current attempt still delivers.
        c2.onText(ws, "live", true);
        assertEquals(List.of("live"), second, "the current attempt still delivers");
    }

    @Test
    void theCurrentAttemptCloseFiresOnCloseOnce() {
        WebSocketTransport transport = new WebSocketTransport();
        List<Throwable> closed = new ArrayList<>();
        WebSocketTransport.Conn conn = transport.beginAttempt(m -> {}, closed::add);
        FakeWebSocket ws = new FakeWebSocket();

        conn.onClose(ws, WebSocket.NORMAL_CLOSURE, "bye");
        conn.onError(ws, new RuntimeException("late error"));

        assertEquals(1, closed.size(), "close notifies exactly once even if a later error also arrives");
        assertFalse(transport.isCurrent(conn), "closing the current attempt clears it");
    }

    /** A no-op {@link WebSocket} so the listener callbacks can run without a real connection. */
    private static final class FakeWebSocket implements WebSocket {
        @Override
        public CompletableFuture<WebSocket> sendText(CharSequence data, boolean last) {
            return CompletableFuture.completedFuture(this);
        }

        @Override
        public CompletableFuture<WebSocket> sendBinary(ByteBuffer data, boolean last) {
            return CompletableFuture.completedFuture(this);
        }

        @Override
        public CompletableFuture<WebSocket> sendPing(ByteBuffer message) {
            return CompletableFuture.completedFuture(this);
        }

        @Override
        public CompletableFuture<WebSocket> sendPong(ByteBuffer message) {
            return CompletableFuture.completedFuture(this);
        }

        @Override
        public CompletableFuture<WebSocket> sendClose(int statusCode, String reason) {
            return CompletableFuture.completedFuture(this);
        }

        @Override
        public void request(long n) {
        }

        @Override
        public String getSubprotocol() {
            return "";
        }

        @Override
        public boolean isOutputClosed() {
            return false;
        }

        @Override
        public boolean isInputClosed() {
            return false;
        }

        @Override
        public void abort() {
        }
    }
}
