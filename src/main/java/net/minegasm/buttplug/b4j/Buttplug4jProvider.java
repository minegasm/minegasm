package net.minegasm.buttplug.b4j;

import net.minegasm.buttplug.ConnectionState;
import net.minegasm.buttplug.DeviceRegistry;
import net.minegasm.buttplug.HapticProvider;
import net.minegasm.buttplug.OutputCommand;
import net.minegasm.buttplug.ProviderStatus;
import net.minegasm.buttplug.StopSelection;
import net.minegasm.device.DeviceRegistrySnapshot;
import net.minegasm.device.HapticDevice;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import io.github.blackspherefollower.buttplug4j.client.ButtplugClientDevice;
import io.github.blackspherefollower.buttplug4j.client.ButtplugClientDeviceFeature;
import io.github.blackspherefollower.buttplug4j.connectors.jetty.websocket.client.ButtplugClientWSClient;

/**
 * {@link HapticProvider} backed by the buttplug4j client library (v4 feature-based spec). buttplug4j
 * owns the WebSocket, protocol negotiation, ping, and message parsing; this class adapts its client
 * API to the engine's provider seam and maintains our own generation-stamped {@link DeviceRegistry}
 * so the scheduler's staleness gate still applies (brief §5.3, §9.5).
 *
 * <p>Output is sent through the feature's {@code run*Float} methods: the engine's normalized value
 * (quantised to {@link B4jDeviceMapper#RESOLUTION}) is converted back to a {@code 0..1} float and
 * buttplug4j scales it to the hardware's advertised range. Fire-and-forget, like the native provider.
 *
 * <p>Compiled by the Gradle build only; verified to compile against buttplug4j 4.0.278.
 */
public final class Buttplug4jProvider implements HapticProvider {

    private final ButtplugClientWSClient client;
    private final DeviceRegistry registry = new DeviceRegistry();
    private final AtomicReference<ProviderStatus> status =
            new AtomicReference<>(ProviderStatus.disconnected());
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "minegasm-buttplug4j");
        t.setDaemon(true);
        return t;
    });

    private volatile Consumer<ProviderStatus> statusListener = s -> {};
    private volatile Consumer<DeviceRegistrySnapshot> registryListener = s -> {};

    public Buttplug4jProvider(String clientName) {
        this.client = new ButtplugClientWSClient(clientName);
        // Any device add/remove/change re-reads the full device list, keeping our registry a faithful
        // snapshot with a fresh generation (mirrors the v4 "full DeviceList is truth" rule).
        client.setDeviceAddedHandler(device -> rebuildRegistry());
        client.setDeviceRemovedHandler(device -> rebuildRegistry());
        client.setDeviceChangedHandler(device -> rebuildRegistry());
        client.setScanningFinishedHandler(() -> setState(
                registry.snapshot().isEmpty() ? ConnectionState.CONNECTED_NO_DEVICES : ConnectionState.READY));
        client.setErrorHandler(error -> setError(error.getErrorMessage()));
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

    @Override
    public CompletionStage<ProviderStatus> connect(URI uri) {
        if (status.get().state() != ConnectionState.DISCONNECTED) {
            return CompletableFuture.completedFuture(status.get());
        }
        setState(ConnectionState.CONNECTING);
        return CompletableFuture.supplyAsync(() -> {
            try {
                setState(ConnectionState.NEGOTIATING);
                client.connect(uri);       // blocking; negotiates v4 and handshakes
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
            return CompletableFuture.completedFuture(null); // stale target (brief §9.5)
        }
        findFeature(command.deviceIndex(), command.featureIndex()).ifPresent(feature -> {
            float f = command.value() / (float) B4jDeviceMapper.RESOLUTION;
            int durationMs = command.durationMs() == null ? 0 : command.durationMs();
            try {
                switch (command.kind()) {
                    case VIBRATE -> feature.runVibrateFloat(f);
                    case OSCILLATE -> feature.runOscillateFloat(f);
                    case ROTATE -> feature.runRotateFloat(f);
                    case CONSTRICT -> feature.runConstrictFloat(f);
                    case POSITION -> feature.runPositionFloat(f);
                    case TEMPERATURE -> feature.runTemperatureFloat(f);
                    case LED -> feature.runLedFloat(f);
                    case HW_POSITION_WITH_DURATION -> feature.runHwPositionWithDurationFloat(f, durationMs);
                    case UNKNOWN -> { /* never rendered */ }
                }
            } catch (Exception ignored) {
                // A single failed output must not disturb the worker (brief §9.4).
            }
        });
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> stop(StopSelection selection) {
        if (!canSendMessages()) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.runAsync(() -> {
            try {
                // instanceof chain rather than a switch over the sealed StopSelection: switch type
                // patterns are a Java 21 feature, and this core also compiles under Java 17 for 1.20.1.
                if (selection instanceof StopSelection.All) {
                    client.stopAllDevices();
                } else if (selection instanceof StopSelection.Device d) {
                    findDevice(d.deviceIndex()).ifPresent(this::stopDevice);
                } else if (selection instanceof StopSelection.Feature f) {
                    findDevice(f.deviceIndex()).ifPresent(device -> {
                        try {
                            device.sendStopDeviceCmd(f.featureIndex());
                        } catch (Exception ignored) {
                            // best effort
                        }
                    });
                } else {
                    throw new IllegalStateException("Unknown StopSelection: " + selection);
                }
            } catch (Exception e) {
                setError(e.getMessage());
            }
        }, executor);
    }

    @Override
    public void disconnect() {
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
    }

    // --- helpers ---------------------------------------------------------------------------

    private boolean canSendMessages() {
        return switch (status.get().state()) {
            case CONNECTED_NO_DEVICES, SCANNING, READY -> true;
            default -> false;
        };
    }

    private static CompletionStage<Void> notConnected(String operation) {
        return CompletableFuture.failedFuture(
                new IllegalStateException("cannot " + operation + " while disconnected"));
    }

    private void stopDevice(ButtplugClientDevice device) {
        try {
            device.sendStopDeviceCmd();
        } catch (Exception ignored) {
            // best effort
        }
    }

    private Optional<ButtplugClientDevice> findDevice(int deviceIndex) {
        List<ButtplugClientDevice> devices = client.getDevices();
        if (devices == null) {
            return Optional.empty();
        }
        return devices.stream().filter(d -> d.getDeviceIndex() == deviceIndex).findFirst();
    }

    private Optional<ButtplugClientDeviceFeature> findFeature(int deviceIndex, int featureIndex) {
        return findDevice(deviceIndex).map(d -> {
            var features = d.getDeviceFeatures();
            return features == null ? null : features.get(featureIndex);
        });
    }

    private void rebuildRegistry() {
        List<HapticDevice> mapped = new ArrayList<>();
        List<ButtplugClientDevice> devices = client.getDevices();
        if (devices != null) {
            for (ButtplugClientDevice device : devices) {
                mapped.add(B4jDeviceMapper.map(device, 0L)); // generation stamped by registry.accept
            }
        }
        DeviceRegistrySnapshot snapshot = registry.accept(mapped);
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
