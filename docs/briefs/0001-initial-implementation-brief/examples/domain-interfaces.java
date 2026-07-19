// Illustrative package-neutral interfaces; adapt names after repository bootstrap.

interface HapticProvider extends AutoCloseable {
    CompletionStage<ConnectionInfo> connect(URI server);
    CompletionStage<Void> startScanning();
    CompletionStage<Void> stopScanning();
    DeviceRegistrySnapshot devices();
    CompletionStage<Void> send(ScheduledOutput command);
    CompletionStage<Void> stop(StopSelection selection);
    ProviderStatus status();
}

interface RecipeResolver {
    Optional<HapticScene> resolve(HapticIntent intent, RuntimeConfig config);
}

interface FeatureRenderer {
    float score(HapticLayer layer, HapticDevice device,
                HapticFeature feature, OutputCapability output,
                RuntimeConfig config);

    List<RenderedOutput> render(HapticLayer layer, HapticDevice device,
                                HapticFeature feature, OutputCapability output,
                                RuntimeConfig config, long nowNs);
}

interface MonotonicClock {
    long nanoTime();
}
