package net.minegasm.buttplug.b4j;

import net.minegasm.buttplug.OutputCommand;
import net.minegasm.device.HapticDevice;

import java.net.URI;
import java.util.List;
import java.util.function.Consumer;

/**
 * Injectable boundary around the blocking buttplug4j client. It exposes only device-neutral types, so the
 * provider and its tests never touch the library. All library types stay inside
 * {@link LibraryB4jClientFacade}; a test supplies a fake without depending on buttplug4j on the classpath.
 */
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

    /** The current devices as neutral registry entries; the registry stamps the generation on accept. */
    List<HapticDevice> deviceSnapshots();

    /**
     * Execute one output command against its live device feature. Throws {@link java.util.concurrent.CancellationException}
     * if the target feature no longer exists, and wraps a library failure in
     * {@link java.util.concurrent.CompletionException}, so the provider's dispatch handling is unchanged.
     */
    void run(OutputCommand command);

    /** Best-effort stop of one device; a missing device or a library failure is ignored. */
    void stopDevice(int deviceIndex);

    /** Stop one feature; throws if the library call fails. A missing device or feature is a no-op. */
    void stopFeature(int deviceIndex, int featureIndex) throws Exception;
}
