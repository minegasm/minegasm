package net.minegasm.client;

import net.minegasm.buttplug.ConnectionState;
import net.minegasm.buttplug.HapticProvider;
import net.minegasm.buttplug.ProviderStatus;
import net.minegasm.buttplug.SwappableProvider;
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
import net.minegasm.bridge.BridgeEndpoint;
import net.minegasm.bridge.TcpLineBridgeTransport;
import net.minegasm.observe.ClientStateSnapshot;
import net.minegasm.pack.PackLoader;
import net.minegasm.pack.PackRegistry;
import net.minegasm.pack.ScenePackInfo;
import net.minegasm.runtime.HapticRuntime;
import net.minegasm.runtime.ReconnectSupervisor;
import net.minegasm.runtime.ScanSupervisor;
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
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * Loader-independent client glue: owns the config, provider, and haptic runtime, and exposes the small
 * surface the Minecraft layer drives (tick, record event, connect, panic). No Minecraft or
 * WebSocket-library types, so it is unit-testable (brief §5.1). The observation adapter feeds it
 * {@link ClientStateSnapshot}s and {@link RawGameEvent}s.
 */
public final class MinegasmClient {

    /** Fixed duration of a UI test pulse before its scheduled stop. */
    private static final long TEST_PULSE_MS = 400;

    private final ConfigStore configStore;
    private final AtomicReference<RuntimeConfig> config;
    private final Function<String, HapticProvider> backendFactory;
    private final SwappableProvider provider;
    private final HapticRuntime runtime;
    private final Clock clock;
    private final AtomicBoolean shutdown = new AtomicBoolean();
    private final boolean firstRun;
    private final PackRegistry scenePacks;
    private final ReconnectSupervisor reconnectSupervisor = new ReconnectSupervisor();
    private final ScanSupervisor scanSupervisor = new ScanSupervisor();
    /**
     * Whether a connection is wanted. Auto-connect and any successful connect set it; a manual
     * disconnect clears it, so the reconnect supervisor never reconnects against the user's intent.
     */
    private volatile boolean desiredConnected;
    private final List<String> errorHistory = new ArrayList<>();
    private static final int MAX_ERROR_HISTORY = 50;
    private static final DateTimeFormatter ERROR_TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private String lastRecordedError;

    /**
     * Inject a backend factory (backend name to provider); the bootstrap builds the one named in config
     * and wraps it in a {@link SwappableProvider} so {@link #setBackend} can switch backends live. The
     * concrete WebSocket transport lives in the loader layer, keeping this engine module free of
     * {@code java.net.http} and Java 8-compilable for Classic.
     */
    public MinegasmClient(Path configFile, Function<String, HapticProvider> backendFactory, Clock clock) {
        this.clock = clock;
        this.backendFactory = backendFactory;
        this.configStore = new ConfigStore(configFile);
        ConfigStore.LoadResult loaded = configStore.load();
        this.firstRun = !loaded.wasPresent() || loaded.recoveredFromCorruption();
        if (firstRun) {
            configStore.save(loaded.config());
        }
        this.config = new AtomicReference<>(RuntimeConfig.of(loaded.config()));
        this.provider = new SwappableProvider(backendFactory.apply(config.get().providerBackend()));
        // Load user scene packs before the runtime so the recipe engine can select them (brief 0003
        // §2.5). A bad pack is isolated and surfaced in the error history, never fatal.
        PackLoader.Result packs = new PackLoader().loadDirectory(packsDir());
        for (String packError : packs.errors()) {
            errorHistory.add("[scene-packs] " + packError);
        }
        this.scenePacks = packs.registry();
        this.runtime = new HapticRuntime(provider, clock, config::get, scenePacks, buildBridgeEndpoints());
        provider.setStatusListener(this::recordProviderError);
    }

    /** Convenience for tests and callers with a single fixed backend instance. */
    public MinegasmClient(Path configFile, HapticProvider provider, Clock clock) {
        this(configFile, backend -> provider, clock);
    }

    /**
     * Build an outbound endpoint for every bridge that is enabled and allowed. Each endpoint must be
     * loopback unless the user opted into remote (same rule as the Buttplug server). Only the
     * dependency-free TCP transport is built here; a disabled, invalid, or non-loopback bridge is skipped
     * and surfaced in the error history, never fatal. Several endpoints run at once (multi-endpoint).
     */
    private List<BridgeEndpoint> buildBridgeEndpoints() {
        List<BridgeEndpoint> endpoints = new ArrayList<>();
        for (HapticConfig.Bridge bridge : config.get().bridges()) {
            if (!bridge.enabled()) {
                continue;
            }
            URI uri;
            try {
                uri = URI.create(bridge.url());
            } catch (IllegalArgumentException bad) {
                errorHistory.add("[bridge:" + bridge.name() + "] invalid endpoint " + bridge.url());
                continue;
            }
            if (!bridge.allowRemote() && !isLoopback(uri)) {
                errorHistory.add("[bridge:" + bridge.name() + "] refusing non-loopback endpoint "
                        + safeHost(uri) + " (enable 'allow remote')");
                continue;
            }
            if (!"tcp".equals(bridge.transport())) {
                errorHistory.add("[bridge:" + bridge.name() + "] transport '" + bridge.transport()
                        + "' is not available in this build");
                continue;
            }
            endpoints.add(new BridgeEndpoint(bridge.name(), uri, new TcpLineBridgeTransport()));
        }
        return endpoints;
    }

    /**
     * Folder holding user scene packs: {@code <config-base>/scene-packs} next to the config file, where
     * {@code <config-base>} is the config file name without its extension (brief 0003 §2.5). For
     * {@code config/minegasm.json} this is {@code config/minegasm/scene-packs}.
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

    /**
     * Per-tick connection upkeep, driven from the client tick on every loader (so it also runs at the
     * main menu, before a world loads). Reconciles the provider's reported state with its real socket,
     * then lets the reconnect supervisor retry a wanted-but-dropped connection with backoff.
     */
    public void pollConnection() {
        provider.poll();
        ConnectionState state = provider.status().state();
        reconnectSupervisor.tick(clock.nanoTime(), state, desiredConnected, config.get().reconnect(),
                this::connect);
        scanSupervisor.tick(clock.nanoTime(), state, this::stopScanning);
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

    private static final DateTimeFormatter BACKUP_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    /**
     * Reset the whole configuration to defaults, keeping only the master enable flag, after backing up
     * the current file. Returns the backup file, or null if there was no existing file to back up.
     */
    public Path resetToDefaults() {
        Path file = configStore.file();
        Path backup = null;
        if (Files.exists(file)) {
            try {
                backup = backupConfig(file);
            } catch (IOException e) {
                throw new UncheckedIOException("failed backing up config before reset", e);
            }
        }
        boolean wasEnabled = config.get().enabled();
        HapticConfig d = HapticConfig.defaults();
        HapticConfig.Global dg = d.global();
        HapticConfig reset = new HapticConfig(d.schemaVersion(), d.profile(),
                new HapticConfig.Global(wasEnabled, dg.intensity(), dg.variation(),
                        dg.fatigueProtection(), dg.pauseBehavior(), dg.stopOnWorldUnload(), dg.panicKey(),
                        dg.testMaxPercent(), dg.testMaxDurationMs(),
                        dg.unsafeTestMaxPercent(), dg.unsafeTestMaxDurationMs()),
                d.buttplug(), d.events(), d.outputPolicy(), d.devices(),
                d.positionCalibrations(), d.accumulation(), d.customIntensity(), d.bridges());
        updateConfig(reset);
        return backup;
    }

    /** Copy the config to a timestamped sibling, adding a numeric suffix if that name is taken, so
     *  repeated resets keep every earlier backup. */
    static Path backupConfig(Path file) throws IOException {
        String baseName = file.getFileName() + ".backup-" + LocalDateTime.now().format(BACKUP_STAMP);
        for (int suffix = 0; ; suffix++) {
            String name = suffix == 0 ? baseName : baseName + "." + suffix;
            Path candidate = file.resolveSibling(name);
            try {
                return Files.copy(file, candidate, StandardCopyOption.COPY_ATTRIBUTES);
            } catch (FileAlreadyExistsException occupied) {
                // Keep every earlier backup and try the next suffix.
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
                cfg.positionCalibrations(), cfg.accumulation(), cfg.customIntensity(), cfg.bridges());
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
        desiredConnected = true; // a connection is now wanted, so a later drop should reconnect
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
        desiredConnected = false; // an explicit disconnect means stay disconnected; do not reconnect
        runtime.lifecycle().onDisconnect();
        provider.disconnect();
    }

    /** The Buttplug backend currently selected: {@code "native"} or {@code "buttplug4j"}. */
    public String backend() {
        return config.get().providerBackend();
    }

    /**
     * Switch the Buttplug backend live, without a restart: persist the choice, stop and release the old
     * backend, wire in the new one, and reconnect if auto-connect is on. Returns {@code true} if the
     * backend changed, {@code false} if the name was unrecognised or already active.
     */
    public synchronized boolean setBackend(String backend) {
        String normalized = "buttplug4j".equalsIgnoreCase(backend) ? "buttplug4j"
                : "native".equalsIgnoreCase(backend) ? "native" : null;
        if (normalized == null || normalized.equals(backend())) {
            return false;
        }
        boolean wasConnected = isConnected();
        HapticProvider next = backendFactory.apply(normalized);
        // Persist the choice so config, the reconnect supervisor, and a later restart all agree.
        HapticConfig cfg = config.get().raw();
        HapticConfig.Buttplug b = cfg.buttplug();
        HapticConfig.Buttplug nb = new HapticConfig.Buttplug(b.serverUrl(), b.autoConnect(), b.autoScan(),
                b.allowRemoteServer(), b.reconnect(), normalized);
        updateConfig(new HapticConfig(cfg.schemaVersion(), cfg.profile(), cfg.global(), nb, cfg.events(),
                cfg.outputPolicy(), cfg.devices(), cfg.positionCalibrations(), cfg.accumulation(),
                cfg.customIntensity(), cfg.bridges()));
        // Quiesce and release the old backend, then install the new one and reconnect if wanted.
        HapticProvider old = provider.current();
        runtime.lifecycle().onConfigReset(); // stop output before the old socket goes away
        old.disconnect();
        provider.swap(next);
        try {
            old.close();
        } catch (RuntimeException ignored) {
            // best effort: the old backend is being discarded
        }
        desiredConnected = false;
        if (config.get().autoConnect() || wasConnected) {
            connect(); // keep a live session live across the switch, or honour auto-connect
        }
        return true;
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
        pollConnection();
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
