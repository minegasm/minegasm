package net.minegasm.classic;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Loopback exercise of {@link ClassicWebSocketTransport} against a Java-WebSocket echo server. The
 * transport source lives in {@code classic/common}; this is the one module that hosts its test. Covers
 * the runtime path the shading check cannot: handshake, a text round trip, a clean close, and rejection
 * of a frame past the inbound cap.
 */
class ClassicWebSocketTransportTest {

    private EchoServer server;

    @AfterEach
    void tearDown() throws Exception {
        if (server != null) {
            server.stop(1000);
        }
    }

    @Test
    void connectsSendsReceivesAndCloses() throws Exception {
        server = EchoServer.started(null);

        ClassicWebSocketTransport transport = new ClassicWebSocketTransport();
        LinkedBlockingQueue<String> received = new LinkedBlockingQueue<String>();
        CountDownLatch closed = new CountDownLatch(1);

        transport.connect(new URI("ws://127.0.0.1:" + server.getPort()),
                received::offer, cause -> closed.countDown())
                .toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertTrue(transport.isOpen(), "transport should be open after connect");

        transport.send("ping");
        assertEquals("ping", received.poll(5, TimeUnit.SECONDS), "server should echo the frame back");

        transport.close();
        assertTrue(closed.await(5, TimeUnit.SECONDS), "close callback should fire once");
        assertFalse(transport.isOpen(), "transport should be closed");
    }

    @Test
    void rejectsAnOversizeInboundFrame() throws Exception {
        // A single frame past the 1 MiB cap must never reach the message handler; the transport closes
        // instead of buffering it.
        char[] huge = new char[(1 << 20) + 4096];
        java.util.Arrays.fill(huge, 'a');
        server = EchoServer.started(new String(huge));

        ClassicWebSocketTransport transport = new ClassicWebSocketTransport();
        LinkedBlockingQueue<String> received = new LinkedBlockingQueue<String>();
        CountDownLatch closed = new CountDownLatch(1);

        transport.connect(new URI("ws://127.0.0.1:" + server.getPort()),
                received::offer, cause -> closed.countDown())
                .toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertTrue(closed.await(5, TimeUnit.SECONDS), "oversize frame should close the transport");
        assertNull(received.poll(500, TimeUnit.MILLISECONDS), "oversize frame must not be delivered");
    }

    /** Echoes text frames, and optionally pushes one message to a client as soon as it connects. */
    private static final class EchoServer extends WebSocketServer {

        private final String sendOnOpen;
        private final CountDownLatch up = new CountDownLatch(1);

        private EchoServer(String sendOnOpen) {
            super(new InetSocketAddress("127.0.0.1", 0));
            this.sendOnOpen = sendOnOpen;
            setReuseAddr(true);
            setConnectionLostTimeout(0);
        }

        static EchoServer started(String sendOnOpen) throws InterruptedException {
            EchoServer server = new EchoServer(sendOnOpen);
            server.start();
            assertTrue(server.up.await(5, TimeUnit.SECONDS), "server failed to start");
            return server;
        }

        @Override
        public void onStart() {
            up.countDown();
        }

        @Override
        public void onOpen(WebSocket conn, ClientHandshake handshake) {
            if (sendOnOpen != null) {
                conn.send(sendOnOpen);
            }
        }

        @Override
        public void onMessage(WebSocket conn, String message) {
            conn.send(message);
        }

        @Override
        public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        }

        @Override
        public void onError(WebSocket conn, Exception ex) {
        }
    }
}
