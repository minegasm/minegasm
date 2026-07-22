package net.minegasm.client;

import net.minegasm.buttplug.ButtplugProvider;
import net.minegasm.buttplug.ConnectionState;
import net.minegasm.buttplug.HapticProvider;
import net.minegasm.buttplug.ProviderStatus;
import net.minegasm.buttplug.WebSocketTransport;
import net.minegasm.config.ConfigStore;
import net.minegasm.config.HapticConfig;
import net.minegasm.config.LegacyMinegasmImporter;
import net.minegasm.config.RuntimeConfig;
import net.minegasm.config.TestOutputLimits;
import net.minegasm.core.OutputKind;
import net.minegasm.core.RawGameEvent;
import net.minegasm.core.CouplingMode;
import net.minegasm.core.GameEventKind;
import net.minegasm.core.HapticLayer;
import net.minegasm.core.HapticPrimitive;
import net.minegasm.core.HapticRole;
import net.minegasm.core.HapticRoute;
import net.minegasm.core.HapticScene;
import net.minegasm.core.Priorities;
import net.minegasm.observe.ClientStateSnapshot;
import net.minegasm.runtime.HapticRuntime;
import net.minegasm.time.Clock;
import net.minegasm.time.SystemClock;
import net.minegasm.util.HapticMath;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.StandardCopyOption;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Locale;
import java.util.List;
import java.util.ArrayList;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Loader-independent client glue: owns the config, provider, and haptic runtime, and exposes a small
 * surface the Minecraft layer drives (tick, record event, connect, panic). Contains no Minecraft or
 * WebSocket-library types, so it is unit-testable and reusable if another loader is added later
 * (brief §5.1). The Minecraft observation adapter feeds it {@link ClientStateSnapshot}s and
 * {@link RawGameEvent}s.
 */
public final class MinegasmClient {

    /** Fixed duration of a UI test pulse before its scheduled stop. */
    private static final long TEST_PULSE_MS = 400;

    private final ConfigStore configStore;
    private final AtomicReference<RuntimeConfig> config;
    private final HapticProvider provider;
    private final HapticRuntime runtime;
    private final Clock clock;
    private final AtomicBoolean shutdown = new AtomicBoolean();
    private final boolean firstRun;
    private final List<String> errorHistory = new ArrayList<>();
    private static final int MAX_ERROR_HISTORY = 50;
    private static final DateTimeFormatter ERROR_TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private String lastRecordedError;

    /** Default: the dependency-free JDK-native Buttplug provider (see {@link ButtplugProvider}). */
    public MinegasmClient(Path configFile) {
        this(configFile, new ButtplugProvider(new WebSocketTransport(), "Minegasm"), SystemClock.INSTANCE);
    }

    /**
     * Inject a specific provider backend. The NeoForge bootstrap uses this to select the buttplug4j
     * client ({@code Buttplug4jProvider}) or the JDK-native provider per config (brief §9.2).
     */
    public MinegasmClient(Path configFile, HapticProvider provider, Clock clock) {
        this.clock = clock;
        this.configStore = new ConfigStore(configFile);
        ConfigStore.LoadResult loaded = configStore.load();
        this.firstRun = !loaded.wasPresent() || loaded.recoveredFromCorruption();
        if (firstRun) {
            configStore.save(loaded.config());
        }
        this.config = new AtomicReference<>(RuntimeConfig.of(loaded.config()));
        this.provider = provider;
        this.runtime = new HapticRuntime(provider, clock, config::get);
        provider.setStatusListener(this::recordProviderError);
    }

    public void start() {
        runtime.start();
        if (config.get().autoConnect()) {
            connect();
        }
    }

    public void shutdown() {
        if (!shutdown.compareAndSet(false, true)) {
            return;
        }
        runtime.shutdown();
        provider.close();
    }

    // --- config ---------------------------------------------------------------------------

    public RuntimeConfig config() {
        return config.get();
    }

    public boolean isFirstRun() {
        return firstRun;
    }

    public Path legacyConfigFile() {
        return configStore.file().resolveSibling("minegasm-client.toml");
    }

    public boolean hasLegacyConfig() {
        return Files.isRegularFile(legacyConfigFile());
    }

    /** Read and preview the legacy TOML without changing either configuration file. */
    public LegacyMinegasmImporter.ImportPreview previewLegacyImport() {
        try {
            String toml = Files.readString(legacyConfigFile(), StandardCharsets.UTF_8);
            return LegacyMinegasmImporter.fromToml(toml, config.get().raw());
        } catch (IOException e) {
            throw new UncheckedIOException("failed reading legacy config", e);
        }
    }

    /** Apply a previously displayed preview; the legacy TOML remains untouched. */
    public void applyLegacyImport(LegacyMinegasmImporter.ImportPreview preview) {
        Path modern = configStore.file();
        if (Files.exists(modern)) {
            try {
                backupBeforeLegacyImport(modern);
            } catch (IOException e) {
                throw new UncheckedIOException("failed backing up current config", e);
            }
        }
        updateConfig(preview.result());
    }

    static Path backupBeforeLegacyImport(Path modern) throws IOException {
        String baseName = modern.getFileName() + ".before-legacy-import";
        for (int suffix = 0; ; suffix++) {
            String name = suffix == 0 ? baseName : baseName + "." + suffix;
            Path candidate = modern.resolveSibling(name);
            try {
                return Files.copy(modern, candidate, StandardCopyOption.COPY_ATTRIBUTES);
            } catch (FileAlreadyExistsException occupied) {
                // Preserve every earlier backup and try the next available suffix.
            }
        }
    }

    /**
     * Turn master haptic output on or off, persisting the change exactly like the config screen's
     * enable toggle; a disable transition also stops any active output (via {@link #updateConfig}).
     * Returns {@code true} if the state actually changed, {@code false} if it was already there.
     */
    public boolean setHapticsEnabled(boolean enabled) {
        HapticConfig cfg = config.get().raw();
        var g = cfg.global();
        if (g.enabled() == enabled) {
            return false;
        }
        var updated = new HapticConfig(cfg.schemaVersion(), cfg.identity(),
                new HapticConfig.Global(enabled, g.intensity(), g.variation(),
                        g.fatigueProtection(), g.pauseBehavior(), g.stopOnWorldUnload(), g.panicKey(),
                        g.testMaxPercent(), g.testMaxDurationMs(),
                        g.unsafeTestMaxPercent(), g.unsafeTestMaxDurationMs()),
                cfg.buttplug(), cfg.events(), cfg.outputPolicy(), cfg.devices(),
                cfg.positionCalibrations(), cfg.accumulation(), cfg.customIntensity());
        updateConfig(updated);
        return true;
    }

    /** Apply a new config atomically: persist, swap the runtime snapshot, and stop if now disabled. */
    public void updateConfig(HapticConfig updated) {
        RuntimeConfig previous = config.get();
        config.set(RuntimeConfig.of(updated));
        configStore.save(updated);
        if (previous.enabled() && !updated.global().enabled()) {
            runtime.lifecycle().onConfigReset();
        }
    }

    // --- connection -----------------------------------------------------------------------

    /**
     * Connect to the configured Buttplug server. Refuses a non-loopback URL unless the user has
     * explicitly allowed remote servers (brief §9.1, §12.2).
     */
    public CompletionStage<ProviderStatus> connect() {
        RuntimeConfig cfg = config.get();
        URI uri;
        try {
            uri = URI.create(cfg.serverUrl());
        } catch (IllegalArgumentException bad) {
            return CompletableFuture.failedFuture(bad);
        }
        if (!cfg.allowRemoteServer() && !isLoopback(uri)) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "refusing non-loopback server " + safeHost(uri) + " (enable 'allow remote server')"));
        }
        CompletionStage<ProviderStatus> stage = provider.connect(uri);
        if (cfg.autoScan()) {
            stage = stage.thenCompose(s -> provider.startScanning().thenApply(v -> provider.status()));
        }
        return stage;
    }

    public CompletionStage<Void> startScanning() {
        return provider.startScanning();
    }

    public CompletionStage<Void> stopScanning() {
        return provider.stopScanning();
    }

    public CompletionStage<Void> refreshDevices() {
        return provider.refreshDevices();
    }

    public void disconnect() {
        runtime.lifecycle().onDisconnect();
        provider.disconnect();
    }

    public ProviderStatus status() {
        return provider.status();
    }

    public synchronized List<String> errorHistory() {
        return List.copyOf(errorHistory);
    }

    /** Clear errors recorded during this client session without affecting provider state. */
    public synchronized void clearErrorHistory() {
        errorHistory.clear();
        lastRecordedError = null;
    }

    private synchronized void recordProviderError(ProviderStatus status) {
        status.lastError().filter(message -> !message.isBlank()).ifPresent(message -> {
            if (message.equals(lastRecordedError)) {
                return;
            }
            lastRecordedError = message;
            errorHistory.add("[" + LocalTime.now().format(ERROR_TIME) + "] " + message);
            if (errorHistory.size() > MAX_ERROR_HISTORY) {
                errorHistory.remove(0);
            }
        });
    }

    public boolean isConnected() {
        return provider.status().state() != ConnectionState.DISCONNECTED;
    }

    // --- gameplay feed --------------------------------------------------------------------

    public void onClientTickEnd(ClientStateSnapshot snapshot) {
        runtime.onClientTickEnd(snapshot);
    }

    public void recordEvent(RawGameEvent event) {
        runtime.recordEvent(event);
    }

    // --- safety ---------------------------------------------------------------------------

    public void panic() {
        runtime.lifecycle().panic();
    }

    public void clearPanic() {
        runtime.lifecycle().clearPanic();
    }

    /**
     * Fire a short, capped test pulse on every enabled {@code Vibrate} feature. The master enable
     * and panic latch both gate this output. A scheduled {@code StopCmd} always ends the pulse.
     */
    public int testPulse(float level) {
        return testPulse(level, TEST_PULSE_MS);
    }

    /** Fire a bounded UI/command test pulse and return the number of targeted features. */
    public int testPulse(float level, long durationMs) {
        if (!config.get().enabled() || !runtime.worker().isOutputEnabled()) {
            return 0; // master-disabled or panic-latched; neither path may emit test output
        }
        var snapshot = provider.devices();
        float capped = HapticMath.clamp01(level);
        long boundedDurationMs = Math.max(TestOutputLimits.MIN_DURATION_MS,
                Math.min(durationMs, TestOutputLimits.MAX_DURATION_MS));
        int targeted = 0;
        for (var device : snapshot.all()) {
            if (!config.get().deviceSetting(device.identityKey()).enabled()) {
                continue;
            }
            for (var feature : device.features().values()) {
                if (feature.supports(OutputKind.VIBRATE)) {
                    targeted++;
                }
            }
        }
        if (targeted > 0) {
            long nowNs = clock.nanoTime();
            long durationNs = boundedDurationMs * 1_000_000L;
            HapticLayer layer = new HapticLayer("test:hold", HapticRole.IMPACT,
                    new HapticPrimitive.Hold(capped, (int) boundedDurationMs, 0, 0),
                    HapticRoute.vibrateAll(), CouplingMode.EXCLUSIVE, Priorities.CONTROL,
                    0, durationNs, "test");
            runtime.worker().offer(new HapticScene("test", GameEventKind.AMBIENT,
                    Priorities.CONTROL, List.of(layer), nowNs, nowNs + durationNs, "test"));
        }
        return targeted;
    }

    public HapticRuntime runtime() {
        return runtime;
    }

    public HapticProvider provider() {
        return provider;
    }

    private static boolean isLoopback(URI uri) {
        String host = uri.getHost();
        if (host == null) {
            return false;
        }
        host = host.toLowerCase(Locale.ROOT);
        return host.equals("127.0.0.1") || host.equals("localhost") || host.equals("::1")
                || host.equals("[::1]");
    }

    private static String safeHost(URI uri) {
        // Redact any credentials/query; only the host is logged (brief §12.3).
        return uri.getHost() == null ? "(unknown)" : uri.getHost();
    }
}
