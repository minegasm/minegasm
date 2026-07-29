package net.minegasm.buttplug;

import net.minegasm.device.DeviceRegistrySnapshot;

import java.net.URI;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

/**
 * The device-provider seam the engine depends on (brief domain interface {@code HapticProvider}).
 * It hides <em>how</em> we talk to Intiface: {@link ButtplugProvider} is the dependency-free
 * JDK-WebSocket implementation, and {@code Buttplug4jProvider} wraps the buttplug4j client library.
 * Either can be swapped in without touching the worker, mixer, scheduler, or recipes.
 *
 * <p>All methods are non-blocking to callers; nothing here runs on the Minecraft client thread.
 */
public interface HapticProvider extends AutoCloseable {

    /** Connect, negotiate v4, and fetch the initial device list. Completes with the ready status. */
    CompletionStage<ProviderStatus> connect(URI uri);

    CompletionStage<Void> startScanning();

    CompletionStage<Void> stopScanning();

    /** Re-request the server's current device list without starting hardware discovery. */
    CompletionStage<Void> refreshDevices();

    /**
     * Send a feature-level output command. Must be dropped (as a completed no-op) if disconnected or
     * if the command's captured registry generation no longer matches (brief §5.3, §9.5).
     */
    CompletionStage<Void> send(OutputCommand command);

    /** Stop output for the given selection; {@code All} must bypass the timing gap. */
    CompletionStage<Void> stop(StopSelection selection);

    /** The current immutable device snapshot (with its generation). */
    DeviceRegistrySnapshot devices();

    /** The current immutable connection status snapshot. */
    ProviderStatus status();

    void setStatusListener(Consumer<ProviderStatus> listener);

    void setRegistryListener(Consumer<DeviceRegistrySnapshot> listener);

    /** Disconnect from the server while keeping this provider reusable for a later connection. */
    void disconnect();

    /** Permanently release this provider's resources. */
    @Override
    void close();
}
