package net.minegasm.client;

import net.minegasm.buttplug.ConnectionState;
import net.minegasm.buttplug.HapticProvider;
import net.minegasm.buttplug.ProviderStatus;
import net.minegasm.config.ConfigStore;
import net.minegasm.config.HapticConfig;
import net.minegasm.config.LegacyMinegasmImporter;
import net.minegasm.config.RuntimeConfig;
import net.minegasm.config.TestOutputLimits;
import net.minegasm.core.DeliveryMode;
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
import net.minegasm.device.DeviceRegistrySnapshot;
import net.minegasm.device.HapticDevice;
import net.minegasm.device.HapticFeature;
import net.minegasm.observe.ClientStateSnapshot;
import net.minegasm.pack.PackLoader;
import net.minegasm.pack.PackRegistry;
import net.minegasm.pack.ScenePackInfo;
import net.minegasm.runtime.HapticRuntime;
import net.minegasm.time.Clock;
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
    private final PackRegistry scenePacks;
    private final List<String> errorHistory = new ArrayList<>();
    private static final int MAX_ERROR_HISTORY = 50;
    private static final DateTimeFormatter ERROR_TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private String lastRecordedError;

    /**
     * Inject a specific provider backend. The Minecraft bootstrap uses this to select the buttplug4j
     * client ({@code Buttplug4jProvider}) or the JDK-native provider per config (brief §9.2). The
     * concrete JDK-WebSocket transport lives in the loader layer, so this engine module stays free of
     * {@code java.net.http} and compiles as Java 8 for the Classic build.
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
        // Load user scene packs before the runtime so the recipe engine can select them (brief 0003
        // §2.5). A bad pack is isolated and surfaced in the error history, never fatal.
        PackLoader.Result packs = new PackLoader().loadDirectory(packsDir());
        for (String packError : packs.errors()) {
            errorHistory.add("[scene-packs] " + packError);
        }
        this.scenePacks = packs.registry();
        this.runtime = new HapticRuntime(provider, clock, config::get, scenePacks);
        provider.setStatusListener(this::recordProviderError);
    }

    /**
     * Folder holding user scene packs: {@code <config-base>/scene-packs} next to the config file, where
     * {@code <config-base>} is the config file name without its extension (brief 0003 §2.5). For a
     * config at {@code config/minegasm.json} this is {@code config/minegasm/scene-packs}, a namespaced
     * subfolder rather than dropping packs loose in the shared config directory.
     */
    private Path packsDir() {
        Path file = configStore.file().toAbsolutePath();
        String fileName = file.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        String base = dot > 0 ? fileName.substring(0, dot) : fileName;
        return file.resolveSibling(base).resolve("scene-packs");
    }

    /** Number of scene packs successfully loaded from the packs folder at startup. */
    public int scenePackCount() {
        return scenePacks.size();
    }

    /** The loaded scene packs as display summaries, for a pack-manager UI or command (brief 0003 §2.7). */
    public List<ScenePackInfo> scenePacks() {
        return ScenePackInfo.from(scenePacks);
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
            String toml = new String(Files.readAllBytes(legacyConfigFile()), StandardCharsets.UTF_8);
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
        HapticConfig.Global g = cfg.global();
        if (g.enabled() == enabled) {
            return false;
        }
        HapticConfig updated = new HapticConfig(cfg.schemaVersion(), cfg.profile(),
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
            return failed(bad);
        }
        if (!cfg.allowRemoteServer() && !isLoopback(uri)) {
            return failed(new IllegalStateException(
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
        return java.util.Collections.unmodifiableList(new ArrayList<>(errorHistory));
    }

    /** Clear errors recorded during this client session without affecting provider state. */
    public synchronized void clearErrorHistory() {
        errorHistory.clear();
        lastRecordedError = null;
    }

    /** Java 8 stand-in for {@code CompletableFuture.failedFuture} (added in Java 9). */
    private static <T> CompletableFuture<T> failed(Throwable cause) {
        CompletableFuture<T> future = new CompletableFuture<>();
        future.completeExceptionally(cause);
        return future;
    }

    private synchronized void recordProviderError(ProviderStatus status) {
        status.lastError().filter(message -> !message.trim().isEmpty()).ifPresent(message -> {
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

    /** Motion route for the test stroke; matches the recipe's stroke route (position/stroker features). */
    private static final HapticRoute TEST_MOTION_ROUTE = new HapticRoute(
            java.util.EnumSet.of(OutputKind.HW_POSITION_WITH_DURATION, OutputKind.POSITION),
            java.util.Collections.emptySet(), java.util.Collections.emptySet(),
            java.util.Collections.emptySet(), DeliveryMode.SUPPLEMENTAL);

    /**
     * Fire a short, capped test on every enabled feature: a buzz on vibrate/oscillate/rotate features
     * and a brief bounded test stroke on position/stroker features. The master enable and panic latch
     * both gate this output. A scheduled {@code StopCmd} always ends the pulse.
     */
    public int testPulse(float level) {
        return testPulse(level, TEST_PULSE_MS);
    }

    /** Fire a bounded UI/command test pulse and return the number of targeted features. */
    public int testPulse(float level, long durationMs) {
        if (!config.get().enabled() || !runtime.worker().isOutputEnabled()) {
            return 0; // master-disabled or panic-latched; neither path may emit test output
        }
        DeviceRegistrySnapshot snapshot = provider.devices();
        float capped = HapticMath.clamp01(level);
        long boundedDurationMs = Math.max(TestOutputLimits.MIN_DURATION_MS,
                Math.min(durationMs, TestOutputLimits.MAX_DURATION_MS));
        int targeted = 0;
        boolean anyMotion = false;
        for (HapticDevice device : snapshot.all()) {
            if (!config.get().deviceSetting(device.identityKey()).enabled()) {
                continue;
            }
            for (HapticFeature feature : device.features().values()) {
                boolean buzz = feature.supports(OutputKind.VIBRATE)
                        || feature.supports(OutputKind.OSCILLATE) || feature.supports(OutputKind.ROTATE);
                boolean motion = feature.supports(OutputKind.POSITION)
                        || feature.supports(OutputKind.HW_POSITION_WITH_DURATION);
                if (buzz || motion) {
                    targeted++;
                }
                anyMotion |= motion;
            }
        }
        if (targeted > 0) {
            long nowNs = clock.nanoTime();
            long durationNs = boundedDurationMs * 1_000_000L;
            List<HapticLayer> layers = new ArrayList<>();
            layers.add(new HapticLayer("test:hold", HapticRole.IMPACT,
                    new HapticPrimitive.Hold(capped, (int) boundedDurationMs, 0, 0),
                    HapticRoute.buzzAll(), CouplingMode.EXCLUSIVE, Priorities.CONTROL,
                    0, durationNs, "test"));
            if (anyMotion) {
                // A clearly visible test stroke, bounded like all motion by the travel window downstream.
                float strokeDepth = HapticMath.clamp01(Math.max(capped, 0.7f));
                int periodMs = (int) Math.max(700L, boundedDurationMs);
                layers.add(new HapticLayer("test:stroke", HapticRole.IMPACT,
                        new HapticPrimitive.Oscillation(strokeDepth, periodMs, (int) boundedDurationMs),
                        TEST_MOTION_ROUTE, CouplingMode.EXCLUSIVE, Priorities.CONTROL,
                        0, durationNs, "test"));
            }
            runtime.worker().offer(new HapticScene("test", GameEventKind.AMBIENT,
                    Priorities.CONTROL, layers, nowNs, nowNs + durationNs, "test"));
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
