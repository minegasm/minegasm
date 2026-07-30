package net.minegasm.bridge;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The TCP transport against a real loopback server: connect, line-framed send, inbound, and close. */
class TcpLineBridgeTransportTest {

    private ServerSocket server;
    private TcpLineBridgeTransport transport;

    @AfterEach
    void tearDown() throws IOException {
        if (transport != null) {
            transport.close();
        }
        if (server != null && !server.isClosed()) {
            server.close();
        }
    }

    @Test
    void connectsSendsLinesAndReceivesInbound() throws Exception {
        server = new ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"));
        URI uri = URI.create("tcp://127.0.0.1:" + server.getLocalPort());

        BlockingQueue<String> inbound = new ArrayBlockingQueue<>(8);
        AtomicReference<Socket> accepted = new AtomicReference<>();
        CountDownLatch acceptedLatch = new CountDownLatch(1);
        Thread acceptor = new Thread(() -> {
            try {
                Socket s = server.accept();
                accepted.set(s);
                acceptedLatch.countDown();
            } catch (IOException ignored) {
            }
        });
        acceptor.setDaemon(true);
        acceptor.start();

        transport = new TcpLineBridgeTransport();
        transport.connect(uri, inbound::add, t -> {}).toCompletableFuture().get(3, TimeUnit.SECONDS);
        assertTrue(acceptedLatch.await(3, TimeUnit.SECONDS), "server accepted the connection");
        assertTrue(transport.isOpen());

        Socket serverSide = accepted.get();
        assertNotNull(serverSide);

        // Send two frames; the server should read them as two newline-delimited lines.
        transport.send("{\"type\":\"effect\"}").toCompletableFuture().get(3, TimeUnit.SECONDS);
        transport.send("{\"type\":\"stop\"}").toCompletableFuture().get(3, TimeUnit.SECONDS);
        BufferedReader fromClient = new BufferedReader(
                new InputStreamReader(serverSide.getInputStream(), StandardCharsets.UTF_8));
        assertEquals("{\"type\":\"effect\"}", fromClient.readLine());
        assertEquals("{\"type\":\"stop\"}", fromClient.readLine());

        // Server sends a line back; the transport delivers it to onMessage.
        OutputStream toClient = serverSide.getOutputStream();
        toClient.write("{\"ack\":true}\n".getBytes(StandardCharsets.UTF_8));
        toClient.flush();
        assertEquals("{\"ack\":true}", inbound.poll(3, TimeUnit.SECONDS));
    }

    @Test
    void sendIsANoOpWhenNotConnected() throws Exception {
        transport = new TcpLineBridgeTransport();
        // never connected: send completes without throwing and reports not open
        transport.send("{\"type\":\"effect\"}").toCompletableFuture().get(1, TimeUnit.SECONDS);
        assertFalse(transport.isOpen());
    }

    @Test
    void closingTheServerFiresOnClose() throws Exception {
        server = new ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"));
        URI uri = URI.create("tcp://127.0.0.1:" + server.getLocalPort());
        CountDownLatch closed = new CountDownLatch(1);

        Thread acceptor = new Thread(() -> {
            try {
                Socket s = server.accept();
                Thread.sleep(50);
                s.close(); // hang up on the client
            } catch (IOException | InterruptedException ignored) {
            }
        });
        acceptor.setDaemon(true);
        acceptor.start();

        transport = new TcpLineBridgeTransport();
        transport.connect(uri, m -> {}, t -> closed.countDown())
                .toCompletableFuture().get(3, TimeUnit.SECONDS);

        assertTrue(closed.await(3, TimeUnit.SECONDS), "peer hangup must fire onClose");
        assertFalse(transport.isOpen());
    }
}
