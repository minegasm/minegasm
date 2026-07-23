package net.minegasm.config;

import net.minegasm.core.GameEventKind;
import net.minegasm.core.OutputKind;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The persisted, versioned configuration schema (brief §11.3). Gson-serialisable; missing fields
 * deserialise to {@code null} and are normalised to defaults by the compact constructors, so partial
 * or older files load safely. The mutable runtime view is {@link RuntimeConfig}.
 *
 * <p>This type is a pure data holder: no Minecraft or Buttplug types.
 */
public record HapticConfig(
        int schemaVersion,
        Identity identity,
        Global global,
        Buttplug buttplug,
        Map<String, EventSetting> events,
        Map<String, OutputPolicy> outputPolicy,
        Map<String, DeviceSetting> devices,
        Map<String, PositionCalibration> positionCalibrations,
        AccumulationParams accumulation,
        CustomIntensities customIntensity) {

    /** Current schema version. Bump when a breaking migration is introduced. */
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public HapticConfig {
        if (schemaVersion <= 0) {
            schemaVersion = CURRENT_SCHEMA_VERSION;
        }
        identity = identity == null ? Identity.defaults() : identity;
        global = global == null ? Global.defaults() : global;
        buttplug = buttplug == null ? Buttplug.defaults() : buttplug;
        events = events == null || events.isEmpty() ? defaultEvents() : Map.copyOf(events);
        outputPolicy = outputPolicy == null || outputPolicy.isEmpty()
                ? defaultOutputPolicy() : Map.copyOf(outputPolicy);
        devices = devices == null ? Map.of() : Map.copyOf(devices);
        positionCalibrations = positionCalibrations == null ? Map.of() : Map.copyOf(positionCalibrations);
        accumulation = accumulation == null ? AccumulationParams.defaults() : accumulation;
        customIntensity = customIntensity == null ? CustomIntensities.legacyDefaults() : customIntensity;
    }

    /** Recipe pack + compatibility mode selection. */
    public record Identity(String recipePack, String compatibilityMode) {
        public Identity {
            recipePack = recipePack == null ? "balanced" : recipePack;
            compatibilityMode = compatibilityMode == null ? "NORMAL" : compatibilityMode;
        }

        public static Identity defaults() {
            return new Identity("balanced", "NORMAL");
        }

        public RecipePackId recipePackId() {
            return RecipePackId.fromString(recipePack, RecipePackId.BALANCED);
        }

        public MinegasmMode mode() {
            return MinegasmMode.fromString(compatibilityMode, MinegasmMode.NORMAL);
        }
    }

    /** Global controls. {@code enabled} defaults OFF until setup completes (brief §12.1). */
    public record Global(
            boolean enabled,
            double intensity,
            double variation,
            boolean fatigueProtection,
            String pauseBehavior,
            boolean stopOnWorldUnload,
            String panicKey,
            int testMaxPercent,
            int testMaxDurationMs,
            int unsafeTestMaxPercent,
            int unsafeTestMaxDurationMs) {

        public Global {
            intensity = clamp01(intensity, 0.75);
            variation = clamp01(variation, 0.50);
            panicKey = panicKey == null ? "UNKNOWN_UNASSIGNED" : panicKey;
            pauseBehavior = PauseBehavior.fromString(pauseBehavior, PauseBehavior.PAUSE).name();
            testMaxPercent = testMaxPercent <= 0 ? TestOutputLimits.DEFAULT_NORMAL_PERCENT
                    : Math.min(testMaxPercent, TestOutputLimits.MAX_PERCENT);
            testMaxDurationMs = testMaxDurationMs <= 0
                    ? TestOutputLimits.DEFAULT_NORMAL_DURATION_MS
                    : Math.max(TestOutputLimits.MIN_DURATION_MS,
                            Math.min(testMaxDurationMs, TestOutputLimits.MAX_DURATION_MS));
            unsafeTestMaxPercent = unsafeTestMaxPercent <= 0
                    ? TestOutputLimits.DEFAULT_UNSAFE_PERCENT : unsafeTestMaxPercent;
            unsafeTestMaxDurationMs = unsafeTestMaxDurationMs <= 0
                    ? TestOutputLimits.DEFAULT_UNSAFE_DURATION_MS
                    : unsafeTestMaxDurationMs;
            unsafeTestMaxPercent = Math.max(testMaxPercent,
                    Math.min(unsafeTestMaxPercent, TestOutputLimits.MAX_PERCENT));
            unsafeTestMaxDurationMs = Math.max(testMaxDurationMs,
                    Math.min(unsafeTestMaxDurationMs, TestOutputLimits.MAX_DURATION_MS));
        }

        public static Global defaults() {
            return new Global(false, 0.75, 0.50, true, PauseBehavior.PAUSE.name(), true,
                    "UNKNOWN_UNASSIGNED", TestOutputLimits.DEFAULT_NORMAL_PERCENT,
                    TestOutputLimits.DEFAULT_NORMAL_DURATION_MS,
                    TestOutputLimits.DEFAULT_UNSAFE_PERCENT,
                    TestOutputLimits.DEFAULT_UNSAFE_DURATION_MS);
        }

        public PauseBehavior pauseBehaviorMode() {
            return PauseBehavior.fromString(pauseBehavior, PauseBehavior.PAUSE);
        }

        private static double clamp01(double v, double fallback) {
            if (Double.isNaN(v)) {
                return fallback;
            }
            if (v < 0) {
                return 0;
            }
            return v > 1 ? 1 : v;
        }
    }

    /** Buttplug connection settings. Loopback URL by default (brief §9.1, §12.2). */
    public record Buttplug(
            String serverUrl,
            boolean autoConnect,
            boolean autoScan,
            boolean allowRemoteServer,
            ReconnectParams reconnect,
            String client) {

        public Buttplug {
            serverUrl = serverUrl == null || serverUrl.isBlank()
                    ? "ws://127.0.0.1:12345" : serverUrl;
            reconnect = reconnect == null ? ReconnectParams.defaults() : reconnect;
            // Which Buttplug client backend to use: "buttplug4j" (library) or "native" (JDK WebSocket).
            client = client == null || client.isBlank() ? "buttplug4j" : client;
        }

        public static Buttplug defaults() {
            return new Buttplug("ws://127.0.0.1:12345", true, true, false,
                    ReconnectParams.defaults(), "buttplug4j");
        }
    }

    /** A complete default configuration (matches {@code config.example.yaml} intent). */
    public static HapticConfig defaults() {
        return new HapticConfig(
                CURRENT_SCHEMA_VERSION,
                Identity.defaults(),
                Global.defaults(),
                Buttplug.defaults(),
                defaultEvents(),
                defaultOutputPolicy(),
                Map.of(),
                Map.of(),
                AccumulationParams.defaults(),
                CustomIntensities.legacyDefaults());
    }

    private static Map<String, EventSetting> defaultEvents() {
        Map<String, EventSetting> m = new LinkedHashMap<>();
        m.put(GameEventKind.ATTACK.configKey(), EventSetting.enabled(0.80));
        m.put(GameEventKind.HURT.configKey(), EventSetting.enabled(1.00));
        m.put(GameEventKind.MINING_ACTIVE.configKey(), EventSetting.enabled(0.55));
        m.put(GameEventKind.BLOCK_BROKEN.configKey(), EventSetting.enabled(0.75));
        m.put(GameEventKind.PLACE.configKey(), EventSetting.enabled(0.35));
        m.put(GameEventKind.HARVEST.configKey(), EventSetting.enabled(0.45));
        m.put(GameEventKind.FISHING_BITE.configKey(), EventSetting.enabled(0.70));
        m.put(GameEventKind.XP_GAIN.configKey(), EventSetting.enabled(0.50));
        m.put(GameEventKind.ADVANCEMENT.configKey(), EventSetting.enabled(0.75));
        m.put(GameEventKind.VITALITY.configKey(), EventSetting.enabled(0.40));
        m.put(GameEventKind.EXPLOSION.configKey(), EventSetting.enabled(1.00));
        return Map.copyOf(m);
    }

    private static Map<String, OutputPolicy> defaultOutputPolicy() {
        Map<String, OutputPolicy> m = new LinkedHashMap<>();
        m.put(OutputKind.VIBRATE.wireName(), OutputPolicy.on());
        m.put(OutputKind.POSITION.wireName(), OutputPolicy.experimentalOff());
        m.put(OutputKind.HW_POSITION_WITH_DURATION.wireName(), OutputPolicy.experimentalOff());
        m.put(OutputKind.OSCILLATE.wireName(), OutputPolicy.off());
        m.put(OutputKind.ROTATE.wireName(), OutputPolicy.off());
        m.put(OutputKind.CONSTRICT.wireName(), OutputPolicy.experimentalOff());
        m.put(OutputKind.TEMPERATURE.wireName(), OutputPolicy.off());
        m.put(OutputKind.LED.wireName(), OutputPolicy.off());
        // Spray: permanently unsupported, can never be enabled (brief non-goal, ADR-008).
        m.put("Spray", OutputPolicy.forbidden());
        return Map.copyOf(m);
    }
}
