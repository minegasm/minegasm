package net.minegasm.bridge;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * A plain-TCP, newline-delimited-JSON {@link BridgeTransport}. This is the default bridge transport and
 * the one both loaders share: it is pure JDK and Java 8 (unlike a {@code java.net.http} WebSocket, which
 * is Java 11+ and so modern-only), so it lives in the engine and works on the Classic build too.
 *
 * <p>Each frame is written as one UTF-8 line terminated by {@code '\n'}; the adapter reads a line at a
 * time. Writes run on a single-thread executor so sends are serialized and the returned stage completes
 * after the frame is flushed, which is what {@link OutboundQueue} relies on to keep one send in flight.
 * A dedicated reader thread delivers inbound lines to {@code onMessage}; end-of-stream or an I/O error
 * fires {@code onClose} once.
 */
public final class TcpLineBridgeTransport implements BridgeTransport {

    private static final int CONNECT_TIMEOUT_MS = 3_000;

    /**
     * Cap on a single inbound line, so a peer (an explicitly allowed remote adapter especially) cannot
     * grow memory by never sending a newline (review P2-5). Frames are small JSON; 64k is generous.
     */
    private static final int MAX_LINE_CHARS = 64 * 1024;

    private volatile Socket socket;
    private volatile OutputStream out;
    private volatile boolean open;
    private volatile boolean closed;
    private ExecutorService writer;
    private Thread reader;
    private Consumer<Throwable> onClose = t -> {};

    @Override
    public CompletionStage<Void> connect(URI uri, Consumer<String> onMessage,
                                         Consumer<Throwable> onClose) {
        this.onClose = onClose == null ? t -> {} : onClose;
        final Consumer<String> onMsg = onMessage == null ? s -> {} : onMessage;
        final String host = uri.getHost() == null ? "127.0.0.1" : uri.getHost();
        final int port = uri.getPort() <= 0 ? 12347 : uri.getPort();

        CompletableFuture<Void> connected = new CompletableFuture<>();
        Thread connectThread = new Thread(() -> {
            Socket s = new Socket();
            this.socket = s; // publish before the blocking connect so close() can abort it in flight
            try {
                if (closed) {
                    throw new IOException("closed before connect");
                }
                s.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
                s.setTcpNoDelay(true);
                this.out = s.getOutputStream();
                ExecutorService w = Executors.newSingleThreadExecutor(
                        r -> daemon(r, "minegasm-bridge-writer"));
                this.writer = w;
                if (closed) {
                    // Raced with close() after the socket connected: don't publish a live transport;
                    // tear the just-built resources down instead of leaking the writer executor.
                    w.shutdownNow();
                    throw new IOException("closed during connect");
                }
                this.open = true;
                startReader(s, onMsg);
                connected.complete(null);
            } catch (IOException failed) {
                closeQuietly();
                connected.completeExceptionally(failed);
            }
        }, "minegasm-bridge-connect");
        connectThread.setDaemon(true);
        connectThread.start();
        return connected;
    }

    @Override
    public CompletionStage<Void> send(String frame) {
        if (!open || closed) {
            return CompletableFuture.completedFuture(null); // no-op when not connected
        }
        final OutputStream target = out;
        final ExecutorService exec = writer;
        if (target == null || exec == null) {
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<Void> done = new CompletableFuture<>();
        try {
            exec.execute(() -> {
                try {
                    target.write((frame + "\n").getBytes(StandardCharsets.UTF_8));
                    target.flush();
                    done.complete(null);
                } catch (IOException write) {
                    done.complete(null); // treat a failed write as done so the queue keeps draining
                    fail(write);
                }
            });
        } catch (RuntimeException rejected) {
            done.complete(null); // executor shut down mid-close
        }
        return done;
    }

    @Override
    public boolean isOpen() {
        return open && !closed;
    }

    @Override
    public void close() {
        closed = true;
        open = false;
        if (writer != null) {
            writer.shutdownNow();
        }
        if (reader != null) {
            reader.interrupt();
        }
        closeQuietly();
    }

    private void startReader(Socket s, Consumer<String> onMsg) {
        reader = new Thread(() -> {
            try (BufferedReader in = new BufferedReader(
                    new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8))) {
                // Bounded line read (not BufferedReader.readLine, which is unbounded): a line that grows
                // past the cap without a newline is a protocol violation, so fail closed instead of
                // buffering it forever. The reconnect supervisor will redial.
                StringBuilder line = new StringBuilder();
                int ch;
                while (!closed && (ch = in.read()) != -1) {
                    if (ch == '\n') {
                        if (line.length() > 0 && line.charAt(line.length() - 1) == '\r') {
                            line.setLength(line.length() - 1);
                        }
                        onMsg.accept(line.toString());
                        line.setLength(0);
                    } else {
                        line.append((char) ch);
                        if (line.length() > MAX_LINE_CHARS) {
                            throw new IOException("inbound line exceeded " + MAX_LINE_CHARS + " chars");
                        }
                    }
                }
                fail(null); // clean end of stream
            } catch (IOException read) {
                fail(closed ? null : read);
            }
        }, "minegasm-bridge-reader");
        reader.setDaemon(true);
        reader.start();
    }

    /** Mark the transport closed once and notify, whether the peer hung up or a write/read failed. */
    private void fail(Throwable cause) {
        boolean wasOpen = open;
        open = false;
        closeQuietly();
        if (wasOpen && !closed) {
            onClose.accept(cause);
        }
    }

    private void closeQuietly() {
        Socket s = socket;
        if (s != null) {
            try {
                s.close();
            } catch (IOException ignored) {
                // best effort
            }
        }
    }

    private static Thread daemon(Runnable r, String name) {
        Thread t = new Thread(r, name);
        t.setDaemon(true);
        return t;
    }
}
