package net.minegasm.buttplug.b4j;

import io.github.blackspherefollower.buttplug4j.client.ButtplugClientDevice;

import java.net.URI;
import java.util.List;
import java.util.function.Consumer;

/** Injectable boundary around the blocking buttplug4j client. */
interface B4jClientFacade {
    void onDeviceChanged(Runnable handler);
    void onScanningFinished(Runnable handler);
    void onError(Consumer<String> handler);
    boolean isConnected();
    void connect(URI uri) throws Exception;
    void disconnect();
    void startScanning() throws Exception;
    void stopScanning() throws Exception;
    void requestDeviceList() throws Exception;
    void stopAllDevices() throws Exception;
    List<ButtplugClientDevice> devices();
}
