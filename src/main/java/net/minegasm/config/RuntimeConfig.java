package net.minegasm.config;

import net.minegasm.core.GameEventKind;
import net.minegasm.core.OutputKind;

import java.util.Optional;

/**
 * Immutable, validated snapshot the engine and renderers read (brief §11.3). Wraps a
 * {@link HapticConfig} and exposes typed accessors so no engine code parses raw config maps. UI
 * changes are applied by building a new snapshot and swapping it atomically — engine state is never
 * mutated directly (brief §5.1).
 */
public final class RuntimeConfig {

    private final HapticConfig config;

    private RuntimeConfig(HapticConfig config) {
        this.config = config;
    }

    public static RuntimeConfig of(HapticConfig config) {
        return new RuntimeConfig(config == null ? HapticConfig.defaults() : config);
    }

    public static RuntimeConfig defaults() {
        return of(HapticConfig.defaults());
    }

    public HapticConfig raw() {
        return config;
    }

    // --- global -----------------------------------------------------------------------------

    public boolean enabled() {
        return config.global().enabled();
    }

    public float globalIntensity() {
        return (float) config.global().intensity();
    }

    public float variation() {
        return (float) config.global().variation();
    }

    public boolean fatigueProtection() {
        return config.global().fatigueProtection();
    }

    public PauseBehavior pauseBehavior() {
        return config.global().pauseBehaviorMode();
    }

    public boolean stopOnWorldUnload() {
        return config.global().stopOnWorldUnload();
    }

    public MinegasmMode mode() {
        return config.identity().mode();
    }

    public RecipePackId recipePack() {
        return config.identity().recipePackId();
    }

    // --- events -----------------------------------------------------------------------------

    private EventSetting event(GameEventKind kind) {
        return config.events().getOrDefault(kind.configKey(), EventSetting.enabled(1.0));
    }

    public boolean eventEnabled(GameEventKind kind) {
        return event(kind).enabled();
    }

    public float eventMultiplier(GameEventKind kind) {
        return (float) event(kind).multiplier();
    }

    /** Base intensity for CUSTOM mode / imported config (0 for events with no custom slot). */
    public float customIntensity(GameEventKind kind) {
        return (float) config.customIntensity().forEvent(kind);
    }

    // --- outputs ----------------------------------------------------------------------------

    private OutputPolicy policy(OutputKind kind) {
        return config.outputPolicy().getOrDefault(kind.wireName(),
                kind == OutputKind.VIBRATE ? OutputPolicy.on() : OutputPolicy.off());
    }

    public boolean outputEnabled(OutputKind kind) {
        return policy(kind).effectivelyEnabled();
    }

    public boolean outputExperimental(OutputKind kind) {
        return policy(kind).experimental();
    }

    // --- devices ----------------------------------------------------------------------------

    /** Device setting by best-effort identity key; defaults to enabled at full cap if unknown. */
    public DeviceSetting deviceSetting(String identityKey) {
        return config.devices().getOrDefault(identityKey, DeviceSetting.defaultOn());
    }

    public Optional<PositionCalibration> calibration(String identityKey) {
        return Optional.ofNullable(config.positionCalibrations().get(identityKey));
    }

    // --- accumulation & connection ----------------------------------------------------------

    public AccumulationParams accumulation() {
        return config.accumulation();
    }

    public String serverUrl() {
        return config.buttplug().serverUrl();
    }

    public boolean allowRemoteServer() {
        return config.buttplug().allowRemoteServer();
    }

    public boolean autoConnect() {
        return config.buttplug().autoConnect();
    }

    public boolean autoScan() {
        return config.buttplug().autoScan();
    }

    public ReconnectParams reconnect() {
        return config.buttplug().reconnect();
    }

    /** Which Buttplug client backend to use: {@code "buttplug4j"} (default) or {@code "native"}. */
    public String providerBackend() {
        return config.buttplug().client();
    }
}
