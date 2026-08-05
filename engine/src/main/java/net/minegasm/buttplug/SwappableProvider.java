package net.minegasm.buttplug;

import net.minegasm.device.DeviceRegistrySnapshot;

import java.net.URI;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

/**
 * A {@link HapticProvider} that forwards to a delegate which can be replaced at runtime, so the backend
 * (native vs buttplug4j) can be switched without rebuilding the client, runtime, or worker that hold a
 * reference to it. The delegate is read per call through a {@code volatile} field; the listeners set on
 * this wrapper are remembered and re-applied to each new delegate so status/registry wiring survives a
 * swap.
 *
 * <p>The wrapper does no locking. Callers on other threads (the worker's {@code send}/{@code stop}) may
 * briefly reach the delegate that is being replaced, which is safe: providers gate their own output on
 * connection state, and {@link #swap} installs the new delegate before the caller closes the old one.
 */
public final class SwappableProvider implements HapticProvider {

    private volatile HapticProvider delegate;
    private volatile Consumer<ProviderStatus> statusListener = s -> {};
    private volatile Consumer<DeviceRegistrySnapshot> registryListener = s -> {};

    public SwappableProvider(HapticProvider initial) {
        this.delegate = initial;
    }

    /** The backend currently in use, e.g. to close it after a {@link #swap}. */
    public HapticProvider current() {
        return delegate;
    }

    /** Install a new backend, carrying the current listeners over to it. Does not close the old one. */
    public void swap(HapticProvider next) {
        next.setStatusListener(statusListener);
        next.setRegistryListener(registryListener);
        this.delegate = next;
    }

    @Override
    public void setStatusListener(Consumer<ProviderStatus> listener) {
        this.statusListener = listener == null ? s -> {} : listener;
        delegate.setStatusListener(this.statusListener);
    }

    @Override
    public void setRegistryListener(Consumer<DeviceRegistrySnapshot> listener) {
        this.registryListener = listener == null ? s -> {} : listener;
        delegate.setRegistryListener(this.registryListener);
    }

    @Override
    public CompletionStage<ProviderStatus> connect(URI uri) {
        return delegate.connect(uri);
    }

    @Override
    public CompletionStage<Void> startScanning() {
        return delegate.startScanning();
    }

    @Override
    public CompletionStage<Void> stopScanning() {
        return delegate.stopScanning();
    }

    @Override
    public CompletionStage<Void> refreshDevices() {
        return delegate.refreshDevices();
    }

    @Override
    public CompletionStage<Void> send(OutputCommand command) {
        return delegate.send(command);
    }

    @Override
    public CompletionStage<Void> stop(StopSelection selection) {
        return delegate.stop(selection);
    }

    @Override
    public DeviceRegistrySnapshot devices() {
        return delegate.devices();
    }

    @Override
    public ProviderStatus status() {
        return delegate.status();
    }

    @Override
    public void poll() {
        delegate.poll();
    }

    @Override
    public void disconnect() {
        delegate.disconnect();
    }

    @Override
    public void close() {
        delegate.close();
    }
}
