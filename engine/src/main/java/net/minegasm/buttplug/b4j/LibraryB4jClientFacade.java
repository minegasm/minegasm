package net.minegasm.buttplug.b4j;

import io.github.blackspherefollower.buttplug4j.client.ButtplugClientDevice;
import io.github.blackspherefollower.buttplug4j.connectors.jetty.websocket.client.ButtplugClientWSClient;

import java.net.URI;
import java.util.List;
import java.util.function.Consumer;

/** Production facade that delegates directly to buttplug4j. */
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
    @Override public List<ButtplugClientDevice> devices() { return client.getDevices(); }
}
