package net.minegasm.buttplug.b4j;

import io.github.blackspherefollower.buttplug4j.client.ButtplugClientDevice;
import io.github.blackspherefollower.buttplug4j.client.ButtplugClientDeviceFeature;
import io.github.blackspherefollower.buttplug4j.connectors.jetty.websocket.client.ButtplugClientWSClient;

import net.minegasm.buttplug.OutputCommand;
import net.minegasm.device.HapticDevice;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

/** Production facade that delegates to buttplug4j. It is the only place library device types are touched. */
final class LibraryB4jClientFacade implements B4jClientFacade {
    private final ButtplugClientWSClient client;

    LibraryB4jClientFacade(String clientName) {
        this.client = new ButtplugClientWSClient(clientName);
    }

    @Override public void onDeviceChanged(Runnable handler) {
        client.setDeviceAddedHandler(device -> handler.run());
        client.setDeviceRemovedHandler(device -> handler.run());
        client.setDeviceChangedHandler(device -> handler.run());
    }
    @Override public void onScanningFinished(Runnable handler) {
        client.setScanningFinishedHandler(() -> handler.run());
    }
    @Override public void onError(Consumer<String> handler) {
        client.setErrorHandler(error -> handler.accept(error.getErrorMessage()));
    }
    @Override public boolean isConnected() { return client.isConnected(); }
    @Override public void connect(URI uri) throws Exception { client.connect(uri); }
    @Override public void disconnect() { client.disconnect(); }
    @Override public void startScanning() throws Exception { client.startScanning(); }
    @Override public void stopScanning() throws Exception { client.stopScanning(); }
    @Override public void requestDeviceList() throws Exception { client.requestDeviceList(); }
    @Override public void stopAllDevices() throws Exception { client.stopAllDevices(); }

    @Override
    public List<HapticDevice> deviceSnapshots() {
        List<HapticDevice> mapped = new ArrayList<>();
        List<ButtplugClientDevice> devices = client.getDevices();
        if (devices != null) {
            for (ButtplugClientDevice device : devices) {
                mapped.add(B4jDeviceMapper.map(device, 0L)); // generation stamped by registry.accept
            }
        }
        return mapped;
    }

    @Override
    public void run(OutputCommand command) {
        ButtplugClientDeviceFeature feature = findFeature(command.deviceIndex(), command.featureIndex())
                .orElseThrow(() -> new CancellationException("target feature no longer exists"));
        float f = command.value() / (float) B4jDeviceMapper.RESOLUTION;
        int durationMs = command.durationMs() == null ? 0 : command.durationMs();
        try {
            switch (command.kind()) {
                case VIBRATE:
                    feature.runVibrateFloat(f);
                    break;
                case OSCILLATE:
                    feature.runOscillateFloat(f);
                    break;
                case ROTATE:
                    feature.runRotateFloat(f);
                    break;
                case CONSTRICT:
                    feature.runConstrictFloat(f);
                    break;
                case POSITION:
                    feature.runPositionFloat(f);
                    break;
                case TEMPERATURE:
                    feature.runTemperatureFloat(f);
                    break;
                case LED:
                    feature.runLedFloat(f);
                    break;
                case HW_POSITION_WITH_DURATION:
                    feature.runHwPositionWithDurationFloat(f, durationMs);
                    break;
                case UNKNOWN:
                default:
                    break; // never rendered
            }
        } catch (Exception failure) {
            throw new CompletionException(failure);
        }
    }

    @Override
    public void stopDevice(int deviceIndex) {
        findDevice(deviceIndex).ifPresent(device -> {
            try {
                device.sendStopDeviceCmd();
            } catch (Exception ignored) {
                // best effort
            }
        });
    }

    @Override
    public void stopFeature(int deviceIndex, int featureIndex) throws Exception {
        Optional<ButtplugClientDevice> device = findDevice(deviceIndex);
        if (device.isPresent()) {
            device.get().sendStopDeviceCmd(featureIndex);
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
            Map<Integer, ButtplugClientDeviceFeature> features = d.getDeviceFeatures();
            return features == null ? null : features.get(featureIndex);
        });
    }
}
