package net.minegasm.buttplug.b4j;

import net.minegasm.buttplug.ConnectionState;
import net.minegasm.buttplug.DeviceRegistry;
import net.minegasm.buttplug.HapticProvider;
import net.minegasm.buttplug.OutputCommand;
import net.minegasm.buttplug.ProviderStatus;
import net.minegasm.buttplug.StopCompensation;
import net.minegasm.buttplug.StopSelection;
import net.minegasm.device.DeviceRegistrySnapshot;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * {@link HapticProvider} backed by the buttplug4j client library (v4 feature-based spec). buttplug4j
 * owns the WebSocket, protocol negotiation, ping, and message parsing; this class adapts its client
 * API to the engine's provider seam and maintains our own generation-stamped {@link DeviceRegistry}
 * so the scheduler's staleness gate still applies (brief §5.3, §9.5).
 *
 * <p>Output is sent through the feature's {@code run*Float} methods: the engine's normalized value
 * (quantised to {@link B4jDeviceMapper#RESOLUTION}) is converted back to a {@code 0..1} float and
 * buttplug4j scales it to the hardware's advertised range. Each returned completion represents the
 * blocking library call, including a late error or a write superseded before dispatch.
 *
 * <p>Compiled by the Gradle build only; verified to compile against buttplug4j 4.0.278.
 */
public final class Buttplug4jProvider implements HapticProvider {

    private final B4jClientFacade client;
    private final DeviceRegistry registry = new DeviceRegistry();
    private final AtomicReference<ProviderStatus> status =
            new AtomicReference<>(ProviderStatus.disconnected());
    // Bumped on every connect and disconnect. A blocking connect running on the executor captures the
    // value at its start and refuses to publish its result if a later disconnect or connect changed it,
    // so a connection that completes after the user disconnected can't rebuild live state (review P2-2).
    private final java.util.concurrent.atomic.AtomicLong connectGeneration =
            new java.util.concurrent.atomic.AtomicLong();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "minegasm-buttplug4j");
        t.setDaemon(true);
        return t;
    });
    // A stop must never queue behind a blocking connect or scan on the lifecycle executor (review P1-2),
    // so it runs on its own single thread. An emergency stop then dispatches immediately regardless of
    // what the lifecycle thread is doing.
    private final ExecutorService stopExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "minegasm-buttplug4j-stop");
        t.setDaemon(true);
        return t;
    });
    // Device writes go through buttplug4j's blocking run*Float calls. Running them on the worker thread
    // lets a hung write wedge the whole driver cycle (review P1-1), so they run here instead, one at a
    // time on a bounded queue that drops the oldest pending write under backpressure (the scheduler
    // re-sends current state next cycle, so a drop self-heals). The worker's send() returns immediately.
    private interface SupersedableWrite extends Runnable {
        void supersede(String detail);
    }

    private final ThreadPoolExecutor sendExecutor = new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<Runnable>(256),
            r -> {
                Thread t = new Thread(r, "minegasm-buttplug4j-send");
                t.setDaemon(true);
                return t;
            },
            (rejected, executor) -> {
                if (executor.isShutdown()) {
                    supersede(rejected, "provider is shutting down");
                    return;
                }
                Runnable dropped = executor.getQueue().poll();
                supersede(dropped, "superseded by a newer queued output");
                if (!executor.getQueue().offer(rejected)) {
                    supersede(rejected, "output queue remained full");
                }
            });
    // Bumped on every stop-all. A queued write captures the epoch at submit and skips itself if a stop
    // changed it, so no write can reach a device after the stop that was meant to silence it.
    private final java.util.concurrent.atomic.AtomicLong sendEpoch =
            new java.util.concurrent.atomic.AtomicLong();
    // Bumped on every connect, disconnect, and detected drop. A queued write captures it and refuses to
    // run if the connection session changed, so a backlog from before a drop can't run after reconnect
    // against a new client and device list (review follow-up P1-5).
    private final java.util.concurrent.atomic.AtomicLong connGeneration =
            new java.util.concurrent.atomic.AtomicLong();

    private volatile Consumer<ProviderStatus> statusListener = s -> {};
    private volatile Consumer<DeviceRegistrySnapshot> registryListener = s -> {};

    public Buttplug4jProvider(String clientName) {
        this(new LibraryB4jClientFacade(clientName));
    }

    Buttplug4jProvider(B4jClientFacade client) {
        this.client = client;
        // Any device add/remove/change re-reads the full device list, keeping our registry a faithful
        // snapshot with a fresh generation (mirrors the v4 "full DeviceList is truth" rule).
        client.onDeviceChanged(this::rebuildRegistry);
        client.onScanningFinished(() -> setState(
                registry.snapshot().isEmpty() ? ConnectionState.CONNECTED_NO_DEVICES : ConnectionState.READY));
        client.onError(this::setError);
    }

    @Override
    public void setStatusListener(Consumer<ProviderStatus> listener) {
        this.statusListener = listener == null ? s -> {} : listener;
    }

    @Override
    public void setRegistryListener(Consumer<DeviceRegistrySnapshot> listener) {
        this.registryListener = listener == null ? s -> {} : listener;
    }

    @Override
    public ProviderStatus status() {
        return status.get();
    }

    @Override
    public DeviceRegistrySnapshot devices() {
        return registry.snapshot();
    }

    /**
     * buttplug4j has no disconnect callback and its {@code onClose} only flips the library's own state,
     * so a dropped socket never reaches our status. Poll the client each tick: if we still believe we're
     * connected but the library no longer is, reconcile to {@code DISCONNECTED} so the reconnect
     * supervisor can act. {@link #disconnect()} also cleans up the library's stale WebSocket client,
     * which its {@code onClose} leaves behind, before a later reconnect replaces it.
     */
    @Override
    public void poll() {
        if (isLocallyConnected(status.get().state()) && !client.isConnected()) {
            disconnect();
        }
    }

    private static boolean isLocallyConnected(ConnectionState state) {
        switch (state) {
            case CONNECTED_NO_DEVICES:
            case SCANNING:
            case READY:
                return true;
            default:
                return false;
        }
    }

    @Override
    public CompletionStage<ProviderStatus> connect(URI uri) {
        if (status.get().state() != ConnectionState.DISCONNECTED) {
            return CompletableFuture.completedFuture(status.get());
        }
        final long gen = connectGeneration.incrementAndGet();
        setState(ConnectionState.CONNECTING);
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (gen != connectGeneration.get()) {
                    return status.get(); // superseded before we even started
                }
                setState(ConnectionState.NEGOTIATING);
                client.connect(uri);       // blocking; negotiates v4 and handshakes
                if (gen != connectGeneration.get()) {
                    // A disconnect (or newer connect) happened while this one was connecting. Don't publish
                    // this stale connection's registry or status; tear the socket we just opened back down.
                    try {
                        client.disconnect();
                    } catch (RuntimeException ignored) {
                        // best effort
                    }
                    return status.get();
                }
                connGeneration.incrementAndGet(); // a fresh session; older queued writes are now stale
                // connect() already requests and processes the initial DeviceList.
                rebuildRegistry();
                return status.get();
            } catch (Exception e) {
                setError(e.getMessage());
                setState(ConnectionState.DISCONNECTED);
                throw new CompletionException(e);
            }
        }, executor);
    }

    @Override
    public CompletionStage<Void> startScanning() {
        if (!canSendMessages()) {
            return notConnected("start scanning");
        }
        setState(ConnectionState.SCANNING);
        return CompletableFuture.runAsync(() -> {
            try {
                client.startScanning();
            } catch (Exception e) {
                setError(e.getMessage());
                setState(connectedState());
                throw new CompletionException(e);
            }
        }, executor);
    }

    @Override
    public CompletionStage<Void> stopScanning() {
        if (status.get().state() != ConnectionState.SCANNING) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.runAsync(() -> {
            try {
                client.stopScanning();
            } catch (Exception e) {
                setError(e.getMessage());
                throw new CompletionException(e);
            } finally {
                setState(connectedState());
            }
        }, executor);
    }

    @Override
    public CompletionStage<Void> refreshDevices() {
        if (!canSendMessages()) {
            return notConnected("refresh devices");
        }
        return CompletableFuture.runAsync(() -> {
            try {
                client.requestDeviceList();
                rebuildRegistry();
            } catch (Exception e) {
                setError(e.getMessage());
            }
        }, executor);
    }

    @Override
    public CompletionStage<Void> send(OutputCommand command) {
        if (!canSendMessages()
                || command.registryGeneration() != registry.snapshot().generation()) {
            return failed(new CancellationException("output target is disconnected or stale"));
        }
        final long epoch = sendEpoch.get();
        final long conn = connGeneration.get();
        final long registryGen = command.registryGeneration();
        // Run the blocking library call off the worker thread so a hung device write can never wedge the
        // driver cycle (review P1-1). Re-check the session immediately before dispatch: skip if a stop-all
        // (epoch), a reconnect (conn generation), or a device-list change (registry) happened after this was
        // queued, so no stale write runs against a new session (P1-5). If a stop lands during the write,
        // compensate so a device is never left non-zero (P1-2).
        CompletableFuture<Void> completion = new CompletableFuture<>();
        try {
            sendExecutor.execute(new PendingWrite(command, epoch, conn, registryGen, completion));
        } catch (RuntimeException rejected) {
            completion.completeExceptionally(rejected);
        }
        return completion;
    }

    private final class PendingWrite implements SupersedableWrite {
        private final OutputCommand command;
        private final long epoch;
        private final long connection;
        private final long registryGeneration;
        private final CompletableFuture<Void> completion;

        private PendingWrite(OutputCommand command, long epoch, long connection,
                             long registryGeneration, CompletableFuture<Void> completion) {
            this.command = command;
            this.epoch = epoch;
            this.connection = connection;
            this.registryGeneration = registryGeneration;
            this.completion = completion;
        }

        @Override
        public void supersede(String detail) {
            completion.completeExceptionally(new CancellationException(detail));
        }

        @Override
        public void run() {
            if (epoch != sendEpoch.get() || connection != connGeneration.get()
                    || registryGeneration != registry.snapshot().generation()) {
                supersede("output superseded before dispatch");
                return;
            }
            try {
                // If a stop races this blocking write, make the compensating zero a real, observed
                // library boundary. Scheduling another fire-and-forget stop here could report the write
                // superseded while silently losing the only zero ordered after it.
                StopCompensation.writeThenMaybeStop(epoch, sendEpoch::get,
                        () -> client.run(command), Buttplug4jProvider.this::compensatingStop);
                if (epoch == sendEpoch.get()) {
                    completion.complete(null);
                } else {
                    supersede("output superseded by stop");
                }
            } catch (RuntimeException failure) {
                if (!(failure instanceof CancellationException)) {
                    Throwable cause = failure instanceof CompletionException && failure.getCause() != null
                            ? failure.getCause() : failure;
                    setError(cause.getMessage());
                }
                completion.completeExceptionally(failure);
            }
        }
    }

    private static void supersede(Runnable task, String detail) {
        if (task instanceof SupersedableWrite) {
            ((SupersedableWrite) task).supersede(detail);
        }
    }

    private void compensatingStop() {
        try {
            client.stopAllDevices();
        } catch (Exception failure) {
            throw new CompletionException(failure);
        }
    }

    @Override
    public CompletionStage<Void> stop(StopSelection selection) {
        if (!canSendMessages()) {
            return CompletableFuture.completedFuture(null);
        }
        if (selection instanceof StopSelection.All) {
            sendEpoch.incrementAndGet(); // invalidate any queued device writes so none run after the stop
            supersedeQueuedWrites("output superseded by stop");
        }
        return CompletableFuture.runAsync(() -> {
            try {
                // A plain instanceof chain with explicit casts: the engine also compiles as Java 8
                // source for the Classic build, so it uses no instanceof pattern bindings.
                if (selection instanceof StopSelection.All) {
                    client.stopAllDevices();
                } else if (selection instanceof StopSelection.Device) {
                    StopSelection.Device d = (StopSelection.Device) selection;
                    client.stopDevice(d.deviceIndex());
                } else if (selection instanceof StopSelection.Feature) {
                    StopSelection.Feature f = (StopSelection.Feature) selection;
                    client.stopFeature(f.deviceIndex(), f.featureIndex());
                } else {
                    throw new IllegalStateException("Unknown StopSelection: " + selection);
                }
            } catch (Exception e) {
                setError(e.getMessage());
                throw new CompletionException(e);
            }
        }, stopExecutor);
    }

    @Override
    public void disconnect() {
        connectGeneration.incrementAndGet(); // invalidate any connect still in flight
        connGeneration.incrementAndGet();    // invalidate any queued device write from this session
        supersedeQueuedWrites("output superseded by disconnect");
        try {
            client.disconnect();
        } catch (RuntimeException ignored) {
            // best effort
        }
        registry.clear();
        setState(ConnectionState.DISCONNECTED);
    }

    @Override
    public void close() {
        disconnect();
        executor.shutdownNow();
        stopExecutor.shutdownNow();
        for (Runnable abandoned : sendExecutor.shutdownNow()) {
            supersede(abandoned, "provider closed before dispatch");
        }
    }

    // --- helpers ---------------------------------------------------------------------------

    private boolean canSendMessages() {
        return isLocallyConnected(status.get().state());
    }

    private void supersedeQueuedWrites(String detail) {
        List<Runnable> queued = new ArrayList<>();
        sendExecutor.getQueue().drainTo(queued);
        for (Runnable task : queued) {
            supersede(task, detail);
        }
    }

    private static <T> CompletableFuture<T> failed(Throwable cause) {
        CompletableFuture<T> future = new CompletableFuture<>();
        future.completeExceptionally(cause);
        return future;
    }

    private static CompletionStage<Void> notConnected(String operation) {
        return failed(
                new IllegalStateException("cannot " + operation + " while disconnected"));
    }

    private void rebuildRegistry() {
        DeviceRegistrySnapshot snapshot = registry.accept(client.deviceSnapshots());
        registryListener.accept(snapshot);
        setState(snapshot.isEmpty() ? ConnectionState.CONNECTED_NO_DEVICES : ConnectionState.READY);
    }

    private void setState(ConnectionState state) {
        ProviderStatus updated = status.updateAndGet(prev -> new ProviderStatus(
                state, prev.negotiatedVersion(), registry.snapshot().all().size(),
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
}
