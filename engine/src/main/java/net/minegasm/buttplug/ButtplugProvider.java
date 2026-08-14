package net.minegasm.buttplug;

import net.minegasm.device.DeviceRegistrySnapshot;

import java.net.URI;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.LongFunction;

/**
 * Buttplug v4 client provider (brief §9). Owns the connection lifecycle, protocol negotiation, ping
 * keepalive, request-id correlation with timeouts, full-{@code DeviceList} registry generations,
 * feature-level output, and stop. All protocol shapes go through {@link ButtplugCodec}; the socket
 * is behind {@link ButtplugTransport}. No Minecraft or engine types appear here.
 *
 * <p>Threading: transport callbacks arrive on the transport thread and only touch atomics/concurrent
 * maps and the immutable registry. Nothing here blocks a caller thread.
 */
public final class ButtplugProvider implements HapticProvider {

    private static final long REQUEST_TIMEOUT_MS = 5_000;
    private static final long MIN_PING_INTERVAL_MS = 250;

    private final ButtplugTransport transport;
    private final String clientName;
    private final DeviceRegistry registry = new DeviceRegistry();
    private final ScheduledExecutorService scheduler;

    private final AtomicLong nextId = new AtomicLong(1);
    private final ConcurrentHashMap<Long, PendingRequest> pending = new ConcurrentHashMap<>();
    private final AtomicReference<ProviderStatus> status =
            new AtomicReference<>(ProviderStatus.disconnected());

    private volatile long maxPingTimeMs;
    private volatile String negotiatedVersion;
    private volatile ScheduledFuture<?> pingTask;
    private volatile Consumer<ProviderStatus> statusListener = s -> {};
    private volatile Consumer<DeviceRegistrySnapshot> registryListener = s -> {};

    public ButtplugProvider(ButtplugTransport transport, String clientName) {
        this(transport, clientName,
                Executors.newSingleThreadScheduledExecutor(r -> {
                    Thread t = new Thread(r, "minegasm-buttplug");
                    t.setDaemon(true);
                    return t;
                }));
    }

    public ButtplugProvider(ButtplugTransport transport, String clientName,
                            ScheduledExecutorService scheduler) {
        this.transport = transport;
        this.clientName = clientName;
        this.scheduler = scheduler;
    }

    private static final class PendingRequest {
        private final CompletableFuture<ServerMessage> future;
        private final ScheduledFuture<?> timeout;

        PendingRequest(CompletableFuture<ServerMessage> future, ScheduledFuture<?> timeout) {
            this.future = future;
            this.timeout = timeout;
        }

        CompletableFuture<ServerMessage> future() {
            return future;
        }

        ScheduledFuture<?> timeout() {
            return timeout;
        }
    }

    public void setStatusListener(Consumer<ProviderStatus> listener) {
        this.statusListener = listener == null ? s -> {} : listener;
    }

    public void setRegistryListener(Consumer<DeviceRegistrySnapshot> listener) {
        this.registryListener = listener == null ? s -> {} : listener;
    }

    public ProviderStatus status() {
        return status.get();
    }

    public DeviceRegistrySnapshot devices() {
        return registry.snapshot();
    }

    // --- lifecycle -------------------------------------------------------------------------

    /**
     * Connect, negotiate v4, start ping if required, and fetch the initial device list. Completes
     * with the ready status; completes exceptionally on negotiation failure or timeout.
     */
    public CompletionStage<ProviderStatus> connect(URI uri) {
        if (status.get().state() != ConnectionState.DISCONNECTED) {
            return CompletableFuture.completedFuture(status.get());
        }
        setState(ConnectionState.CONNECTING);
        return transport.connect(uri, this::onFrame, this::onClose)
                .thenCompose(v -> {
                    setState(ConnectionState.NEGOTIATING);
                    return sendRequest(id -> ButtplugCodec.requestServerInfo(id, clientName));
                })
                .thenCompose(msg -> {
                    if (msg instanceof ServerMessage.ServerInfo) {
                        ServerMessage.ServerInfo si = (ServerMessage.ServerInfo) msg;
                        // Reject a server that negotiates a different major version rather than proceed
                        // and fail on the first v4 OutputCmd. A server that omits the field parses as 4
                        // (our default), so only an explicit mismatch is refused.
                        if (si.majorVersion() != ButtplugCodec.PROTOCOL_MAJOR) {
                            throw new IllegalStateException("server speaks Buttplug v" + si.majorVersion()
                                    + " but this client requires v" + ButtplugCodec.PROTOCOL_MAJOR);
                        }
                        this.negotiatedVersion = si.majorVersion() + "." + si.minorVersion();
                        this.maxPingTimeMs = si.maxPingTimeMs();
                        startPing();
                    }
                    setState(ConnectionState.CONNECTED_NO_DEVICES);
                    return sendRequest(ButtplugCodec::requestDeviceList);
                })
                .thenApply(msg -> status.get())
                .whenComplete((result, failure) -> {
                    if (failure != null) {
                        Throwable cause = failure instanceof CompletionException
                                && failure.getCause() != null ? failure.getCause() : failure;
                        cancelPing();
                        DeviceRegistrySnapshot cleared = registry.clear();
                        registryListener.accept(cleared);
                        setError(cause.getMessage());
                        setState(ConnectionState.DISCONNECTED);
                        transport.close();
                    }
                });
    }

    public CompletionStage<Void> startScanning() {
        if (!canSendMessages()) {
            return notConnected("start scanning");
        }
        setState(ConnectionState.SCANNING);
        return sendRequest(ButtplugCodec::startScanning)
                .thenApply(m -> (Void) null)
                .whenComplete((ignored, error) -> {
                    if (error != null) {
                        setState(connectedState());
                    }
                });
    }

    public CompletionStage<Void> stopScanning() {
        if (status.get().state() != ConnectionState.SCANNING) {
            return CompletableFuture.completedFuture(null);
        }
        return sendRequest(ButtplugCodec::stopScanning)
                .thenApply(m -> (Void) null)
                .whenComplete((ignored, error) -> setState(connectedState()));
    }

    @Override
    public CompletionStage<Void> refreshDevices() {
        if (!canSendMessages()) {
            return notConnected("refresh devices");
        }
        return sendRequest(ButtplugCodec::requestDeviceList).thenApply(m -> null);
    }

    /**
     * Send a feature-level output command. A closed socket or stale registry generation completes as a
     * cancellation. Otherwise the returned stage follows the matching protocol reply, so a late
     * {@code Error} or request timeout reaches backend health instead of being reported as delivered.
     */
    public CompletionStage<Void> send(OutputCommand command) {
        if (!canSendMessages()) {
            return cancelled("output dropped while disconnected");
        }
        if (command.registryGeneration() != registry.snapshot().generation()) {
            return cancelled("output target generation is stale");
        }
        return sendRequest(id -> ButtplugCodec.outputCmd(id, command)).thenApply(reply -> null);
    }

    /**
     * Stop output. All selections use the protocol {@code StopCmd}, which bypasses the timing gap;
     * device/feature selections use the v4 {@code DeviceIndex}/{@code FeatureIndex} fields
     * (validated against the Rust {@code StopCmdV4}).
     */
    public CompletionStage<Void> stop(StopSelection selection) {
        if (!canSendMessages()) {
            return CompletableFuture.completedFuture(null);
        }
        // A plain instanceof chain with explicit casts: the engine also compiles as Java 8 source for
        // the Classic build, so it uses neither switch type patterns nor instanceof pattern bindings.
        if (selection instanceof StopSelection.All) {
            return sendRequest(ButtplugCodec::stopAll).thenApply(reply -> null);
        } else if (selection instanceof StopSelection.Device) {
            StopSelection.Device d = (StopSelection.Device) selection;
            return sendRequest(id -> ButtplugCodec.stopDevice(id, d.deviceIndex()))
                    .thenApply(reply -> null);
        } else if (selection instanceof StopSelection.Feature) {
            StopSelection.Feature f = (StopSelection.Feature) selection;
            return sendRequest(id -> ButtplugCodec.stopFeature(id,
                    f.deviceIndex(), f.featureIndex())).thenApply(reply -> null);
        } else {
            throw new IllegalStateException("Unknown StopSelection: " + selection);
        }
    }

    private boolean canSendMessages() {
        if (!transport.isOpen()) {
            return false;
        }
        switch (status.get().state()) {
            case CONNECTED_NO_DEVICES:
            case SCANNING:
            case READY:
                return true;
            default:
                return false;
        }
    }

    private static CompletionStage<Void> notConnected(String operation) {
        return failed(new IllegalStateException("cannot " + operation + " while disconnected"));
    }

    /** Java 8 stand-in for {@code CompletableFuture.failedFuture} (added in Java 9). */
    private static <T> CompletableFuture<T> failed(Throwable cause) {
        CompletableFuture<T> future = new CompletableFuture<>();
        future.completeExceptionally(cause);
        return future;
    }

    private static <T> CompletableFuture<T> cancelled(String detail) {
        return failed(new java.util.concurrent.CancellationException(detail));
    }

    @Override
    public void disconnect() {
        cancelPing();
        try {
            if (transport.isOpen()) {
                transport.send(ButtplugCodec.disconnect(nextId.getAndIncrement()));
            }
        } catch (RuntimeException ignored) {
            // best effort
        }
        transport.close();
        registry.clear();
        failAllPending(new IllegalStateException("provider closed"));
        setState(ConnectionState.DISCONNECTED);
    }

    @Override
    public void close() {
        disconnect();
        scheduler.shutdownNow();
    }

    // --- inbound ---------------------------------------------------------------------------

    private void onFrame(String frame) {
        for (ServerMessage msg : ButtplugCodec.parse(frame)) {
            handle(msg);
        }
    }

    private void handle(ServerMessage msg) {
        // A plain instanceof chain with explicit casts: the engine also compiles as Java 8 source for
        // the Classic build, so it uses neither switch type patterns nor instanceof pattern bindings.
        // The trailing throw keeps the exhaustiveness a switch would give: a new ServerMessage subtype
        // fails loudly.
        if (msg instanceof ServerMessage.DeviceList) {
            ServerMessage.DeviceList list = (ServerMessage.DeviceList) msg;
            DeviceRegistrySnapshot snap = registry.accept(list.devices());
            registryListener.accept(snap);
            setState(snap.isEmpty() ? ConnectionState.CONNECTED_NO_DEVICES : ConnectionState.READY);
            complete(list);
        } else if (msg instanceof ServerMessage.ServerInfo) {
            ServerMessage.ServerInfo si = (ServerMessage.ServerInfo) msg;
            this.negotiatedVersion = si.majorVersion() + "." + si.minorVersion();
            this.maxPingTimeMs = si.maxPingTimeMs();
            complete(si);
        } else if (msg instanceof ServerMessage.Error) {
            ServerMessage.Error err = (ServerMessage.Error) msg;
            // Surface every server error, not only ones tied to a pending request: a rejected OutputCmd
            // is fire-and-forget (no pending) and system errors carry Id 0, so failPending alone drops
            // them. Recording it in the status/error history keeps them visible (duplicates de-dupe there).
            setError(err.errorMessage());
            failPending(err.id(), new ButtplugException(err.errorMessage(), err.errorCode()));
        } else if (msg instanceof ServerMessage.ScanningFinished) {
            ServerMessage.ScanningFinished sf = (ServerMessage.ScanningFinished) msg;
            if (status.get().state() == ConnectionState.SCANNING) {
                setState(devices().isEmpty()
                        ? ConnectionState.CONNECTED_NO_DEVICES : ConnectionState.READY);
            }
            complete(sf);
        } else if (msg instanceof ServerMessage.DeviceListChanged) {
            // A device connected or dropped (often mid-scan); re-request the authoritative full list so
            // the registry reflects it without waiting for a manual refresh.
            if (transport.isOpen()) {
                transport.send(ButtplugCodec.requestDeviceList(nextId.getAndIncrement()));
            }
        } else if (msg instanceof ServerMessage.Ok) {
            complete(msg);
        } else if (msg instanceof ServerMessage.Unknown) {
            /* logged once by caller; ignore safely */
        } else {
            throw new IllegalStateException("Unknown ServerMessage: " + msg);
        }
    }

    private void onClose(Throwable cause) {
        cancelPing();
        registry.clear();
        failAllPending(cause != null ? cause : new IllegalStateException("connection closed"));
        if (cause != null) {
            setError(cause.getMessage());
        }
        setState(ConnectionState.DISCONNECTED);
    }

    // --- request correlation ---------------------------------------------------------------

    private CompletionStage<ServerMessage> sendRequest(LongFunction<String> frameForId) {
        long id = nextId.getAndIncrement();
        CompletableFuture<ServerMessage> future = new CompletableFuture<>();
        ScheduledFuture<?> timeout = scheduler.schedule(
                () -> failPending(id, new TimeoutException("request " + id + " timed out")),
                REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        pending.put(id, new PendingRequest(future, timeout));
        try {
            transport.send(frameForId.apply(id));
        } catch (RuntimeException sendFailure) {
            failPending(id, sendFailure);
        }
        return future;
    }

    private void complete(ServerMessage msg) {
        PendingRequest req = pending.remove(msg.id());
        if (req != null) {
            req.timeout().cancel(false);
            req.future().complete(msg);
        }
    }

    private void failPending(long id, Throwable cause) {
        PendingRequest req = pending.remove(id);
        if (req != null) {
            req.timeout().cancel(false);
            req.future().completeExceptionally(cause);
        }
    }

    private void failAllPending(Throwable cause) {
        pending.forEach((id, req) -> {
            req.timeout().cancel(false);
            req.future().completeExceptionally(cause);
        });
        pending.clear();
    }

    // --- ping & status ---------------------------------------------------------------------

    private void startPing() {
        cancelPing();
        if (maxPingTimeMs <= 0) {
            return; // ping not required by this server
        }
        long interval = Math.max(MIN_PING_INTERVAL_MS, maxPingTimeMs / 2);
        pingTask = scheduler.scheduleAtFixedRate(() -> {
            if (transport.isOpen()) {
                transport.send(ButtplugCodec.ping(nextId.getAndIncrement()));
            }
        }, interval, interval, TimeUnit.MILLISECONDS);
    }

    private void cancelPing() {
        ScheduledFuture<?> task = pingTask;
        if (task != null) {
            task.cancel(false);
            pingTask = null;
        }
    }

    private void setState(ConnectionState state) {
        ProviderStatus updated = status.updateAndGet(prev -> new ProviderStatus(
                state, Optional.ofNullable(negotiatedVersion), devices().all().size(),
                prev.lastError(), registry.snapshot().generation()));
        statusListener.accept(updated);
    }

    private ConnectionState connectedState() {
        return registry.snapshot().isEmpty()
                ? ConnectionState.CONNECTED_NO_DEVICES : ConnectionState.READY;
    }

    private void setError(String message) {
        ProviderStatus updated = status.updateAndGet(prev -> new ProviderStatus(prev.state(), prev.negotiatedVersion(),
                prev.deviceCount(), Optional.ofNullable(message), prev.registryGeneration()));
        statusListener.accept(updated);
    }

    /** Checked-free protocol error carrying the Buttplug error code. */
    public static final class ButtplugException extends RuntimeException {
        private final int code;

        public ButtplugException(String message, int code) {
            super(message);
            this.code = code;
        }

        public int code() {
            return code;
        }
    }
}
