package net.minegasm.config;

import net.minegasm.core.GameEventKind;
import net.minegasm.core.OutputKind;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The persisted, versioned configuration schema (brief §11.3). Gson-serialisable; missing fields
 * deserialise to {@code null} and are normalised to defaults by the constructors, so partial or older
 * files load safely. The mutable runtime view is {@link RuntimeConfig}.
 *
 * <p>This type is a pure data holder: no Minecraft or Buttplug types.
 */
public final class HapticConfig implements ConfigValue {

    /** Current schema version. Bump when a breaking migration is introduced. */
    public static final int CURRENT_SCHEMA_VERSION = 1;

    private final int schemaVersion;
    private final Profile profile;
    private final Global global;
    private final Buttplug buttplug;
    private final Map<String, EventSetting> events;
    private final Map<String, OutputPolicy> outputPolicy;
    private final Map<String, DeviceSetting> devices;
    private final Map<String, PositionCalibration> positionCalibrations;
    private final AccumulationParams accumulation;
    private final CustomIntensities customIntensity;
    private final List<Bridge> bridges;

    /**
     * Full constructor including the bridge list. The Gson adapter uses this one (it matches every
     * instance field). New code that needs to set the bridges calls it directly; older call sites use the
     * shorter constructor below, which defaults them.
     */
    public HapticConfig(
            int schemaVersion,
            Profile profile,
            Global global,
            Buttplug buttplug,
            Map<String, EventSetting> events,
            Map<String, OutputPolicy> outputPolicy,
            Map<String, DeviceSetting> devices,
            Map<String, PositionCalibration> positionCalibrations,
            AccumulationParams accumulation,
            CustomIntensities customIntensity,
            List<Bridge> bridges) {
        this.schemaVersion = schemaVersion <= 0 ? CURRENT_SCHEMA_VERSION : schemaVersion;
        this.profile = profile == null ? Profile.defaults() : profile;
        this.global = global == null ? Global.defaults() : global;
        this.buttplug = buttplug == null ? Buttplug.defaults() : buttplug;
        this.events = events == null || events.isEmpty()
                ? defaultEvents() : unmodifiableCopy(events);
        this.outputPolicy = outputPolicy == null || outputPolicy.isEmpty()
                ? defaultOutputPolicy() : unmodifiableCopy(outputPolicy);
        this.devices = devices == null ? Collections.emptyMap() : unmodifiableCopy(devices);
        this.positionCalibrations = positionCalibrations == null
                ? Collections.emptyMap() : unmodifiableCopy(positionCalibrations);
        this.accumulation = accumulation == null ? AccumulationParams.defaults() : accumulation;
        this.customIntensity = customIntensity == null
                ? CustomIntensities.legacyDefaults() : customIntensity;
        this.bridges = bridges == null || bridges.isEmpty()
                ? defaultBridges()
                : Collections.unmodifiableList(new java.util.ArrayList<>(bridges));
    }

    private static <K, V> Map<K, V> unmodifiableCopy(Map<K, V> source) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    public int schemaVersion() {
        return schemaVersion;
    }

    public Profile profile() {
        return profile;
    }

    public Global global() {
        return global;
    }

    public Buttplug buttplug() {
        return buttplug;
    }

    public Map<String, EventSetting> events() {
        return events;
    }

    public Map<String, OutputPolicy> outputPolicy() {
        return outputPolicy;
    }

    public Map<String, DeviceSetting> devices() {
        return devices;
    }

    public Map<String, PositionCalibration> positionCalibrations() {
        return positionCalibrations;
    }

    public AccumulationParams accumulation() {
        return accumulation;
    }

    public CustomIntensities customIntensity() {
        return customIntensity;
    }

    public List<Bridge> bridges() {
        return bridges;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof HapticConfig)) {
            return false;
        }
        HapticConfig other = (HapticConfig) o;
        return schemaVersion == other.schemaVersion
                && Objects.equals(bridges, other.bridges)
                && Objects.equals(profile, other.profile)
                && Objects.equals(global, other.global)
                && Objects.equals(buttplug, other.buttplug)
                && Objects.equals(events, other.events)
                && Objects.equals(outputPolicy, other.outputPolicy)
                && Objects.equals(devices, other.devices)
                && Objects.equals(positionCalibrations, other.positionCalibrations)
                && Objects.equals(accumulation, other.accumulation)
                && Objects.equals(customIntensity, other.customIntensity);
    }

    @Override
    public int hashCode() {
        return Objects.hash(schemaVersion, profile, global, buttplug, events, outputPolicy, devices,
                positionCalibrations, accumulation, customIntensity, bridges);
    }

    @Override
    public String toString() {
        return "HapticConfig[schemaVersion=" + schemaVersion + ", profile=" + profile
                + ", global=" + global + ", buttplug=" + buttplug + ", events=" + events
                + ", outputPolicy=" + outputPolicy + ", devices=" + devices
                + ", positionCalibrations=" + positionCalibrations + ", accumulation=" + accumulation
                + ", customIntensity=" + customIntensity + ", bridges=" + bridges + "]";
    }

    /** Recipe pack + haptic mode selection. */
    public static final class Profile implements ConfigValue {
        private final String recipePack;
        private final String hapticMode;

        public Profile(String recipePack, String hapticMode) {
            this.recipePack = recipePack == null ? "balanced" : recipePack;
            this.hapticMode = hapticMode == null ? "IMMERSION" : hapticMode;
        }

        public String recipePack() {
            return recipePack;
        }

        public String hapticMode() {
            return hapticMode;
        }

        public static Profile defaults() {
            return new Profile("balanced", "IMMERSION");
        }

        public RecipePackId recipePackId() {
            return RecipePackId.fromString(recipePack, RecipePackId.BALANCED);
        }

        public MinegasmMode mode() {
            return MinegasmMode.fromString(hapticMode, MinegasmMode.IMMERSION);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Profile)) {
                return false;
            }
            Profile other = (Profile) o;
            return Objects.equals(recipePack, other.recipePack)
                    && Objects.equals(hapticMode, other.hapticMode);
        }

        @Override
        public int hashCode() {
            return Objects.hash(recipePack, hapticMode);
        }

        @Override
        public String toString() {
            return "Profile[recipePack=" + recipePack + ", hapticMode=" + hapticMode
                    + "]";
        }
    }

    /** Global controls. {@code enabled} defaults OFF until setup completes (brief §12.1). */
    public static final class Global implements ConfigValue {
        private final boolean enabled;
        private final double intensity;
        private final double variation;
        private final boolean fatigueProtection;
        private final String pauseBehavior;
        private final boolean stopOnWorldUnload;
        private final String panicKey;
        private final int testMaxPercent;
        private final int testMaxDurationMs;
        private final int unsafeTestMaxPercent;
        private final int unsafeTestMaxDurationMs;

        public Global(
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
            this.enabled = enabled;
            this.intensity = clamp01(intensity, 0.75);
            this.variation = clamp01(variation, 0.50);
            this.fatigueProtection = fatigueProtection;
            this.pauseBehavior = PauseBehavior.fromString(pauseBehavior, PauseBehavior.PAUSE).name();
            this.stopOnWorldUnload = stopOnWorldUnload;
            this.panicKey = panicKey == null ? "UNKNOWN_UNASSIGNED" : panicKey;
            int tp = testMaxPercent <= 0 ? TestOutputLimits.DEFAULT_NORMAL_PERCENT
                    : Math.min(testMaxPercent, TestOutputLimits.MAX_PERCENT);
            int td = testMaxDurationMs <= 0
                    ? TestOutputLimits.DEFAULT_NORMAL_DURATION_MS
                    : Math.max(TestOutputLimits.MIN_DURATION_MS,
                            Math.min(testMaxDurationMs, TestOutputLimits.MAX_DURATION_MS));
            int up = unsafeTestMaxPercent <= 0
                    ? TestOutputLimits.DEFAULT_UNSAFE_PERCENT : unsafeTestMaxPercent;
            int ud = unsafeTestMaxDurationMs <= 0
                    ? TestOutputLimits.DEFAULT_UNSAFE_DURATION_MS : unsafeTestMaxDurationMs;
            up = Math.max(tp, Math.min(up, TestOutputLimits.MAX_PERCENT));
            ud = Math.max(td, Math.min(ud, TestOutputLimits.MAX_DURATION_MS));
            this.testMaxPercent = tp;
            this.testMaxDurationMs = td;
            this.unsafeTestMaxPercent = up;
            this.unsafeTestMaxDurationMs = ud;
        }

        public boolean enabled() {
            return enabled;
        }

        public double intensity() {
            return intensity;
        }

        public double variation() {
            return variation;
        }

        public boolean fatigueProtection() {
            return fatigueProtection;
        }

        public String pauseBehavior() {
            return pauseBehavior;
        }

        public boolean stopOnWorldUnload() {
            return stopOnWorldUnload;
        }

        public String panicKey() {
            return panicKey;
        }

        public int testMaxPercent() {
            return testMaxPercent;
        }

        public int testMaxDurationMs() {
            return testMaxDurationMs;
        }

        public int unsafeTestMaxPercent() {
            return unsafeTestMaxPercent;
        }

        public int unsafeTestMaxDurationMs() {
            return unsafeTestMaxDurationMs;
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

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Global)) {
                return false;
            }
            Global other = (Global) o;
            return enabled == other.enabled
                    && Double.compare(intensity, other.intensity) == 0
                    && Double.compare(variation, other.variation) == 0
                    && fatigueProtection == other.fatigueProtection
                    && stopOnWorldUnload == other.stopOnWorldUnload
                    && testMaxPercent == other.testMaxPercent
                    && testMaxDurationMs == other.testMaxDurationMs
                    && unsafeTestMaxPercent == other.unsafeTestMaxPercent
                    && unsafeTestMaxDurationMs == other.unsafeTestMaxDurationMs
                    && Objects.equals(pauseBehavior, other.pauseBehavior)
                    && Objects.equals(panicKey, other.panicKey);
        }

        @Override
        public int hashCode() {
            return Objects.hash(enabled, intensity, variation, fatigueProtection, pauseBehavior,
                    stopOnWorldUnload, panicKey, testMaxPercent, testMaxDurationMs,
                    unsafeTestMaxPercent, unsafeTestMaxDurationMs);
        }

        @Override
        public String toString() {
            return "Global[enabled=" + enabled + ", intensity=" + intensity + ", variation=" + variation
                    + ", fatigueProtection=" + fatigueProtection + ", pauseBehavior=" + pauseBehavior
                    + ", stopOnWorldUnload=" + stopOnWorldUnload + ", panicKey=" + panicKey
                    + ", testMaxPercent=" + testMaxPercent + ", testMaxDurationMs=" + testMaxDurationMs
                    + ", unsafeTestMaxPercent=" + unsafeTestMaxPercent
                    + ", unsafeTestMaxDurationMs=" + unsafeTestMaxDurationMs + "]";
        }
    }

    /** Buttplug connection settings. Loopback URL by default (brief §9.1, §12.2). */
    public static final class Buttplug implements ConfigValue {
        private final String serverUrl;
        private final boolean autoConnect;
        private final boolean autoScan;
        private final boolean allowRemoteServer;
        private final ReconnectParams reconnect;
        private final String client;

        public Buttplug(
                String serverUrl,
                boolean autoConnect,
                boolean autoScan,
                boolean allowRemoteServer,
                ReconnectParams reconnect,
                String client) {
            this.serverUrl = serverUrl == null || serverUrl.trim().isEmpty()
                    ? "ws://127.0.0.1:12345" : serverUrl;
            this.autoConnect = autoConnect;
            this.autoScan = autoScan;
            this.allowRemoteServer = allowRemoteServer;
            this.reconnect = reconnect == null ? ReconnectParams.defaults() : reconnect;
            // Which Buttplug client backend to use: "native" (JDK/bundled WebSocket, the default) or
            // "buttplug4j" (the library, which pulls in Jetty/Jackson).
            this.client = client == null || client.trim().isEmpty() ? "native" : client;
        }

        public String serverUrl() {
            return serverUrl;
        }

        public boolean autoConnect() {
            return autoConnect;
        }

        public boolean autoScan() {
            return autoScan;
        }

        public boolean allowRemoteServer() {
            return allowRemoteServer;
        }

        public ReconnectParams reconnect() {
            return reconnect;
        }

        public String client() {
            return client;
        }

        public static Buttplug defaults() {
            return new Buttplug("ws://127.0.0.1:12345", true, true, false,
                    ReconnectParams.defaults(), "native");
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Buttplug)) {
                return false;
            }
            Buttplug other = (Buttplug) o;
            return autoConnect == other.autoConnect
                    && autoScan == other.autoScan
                    && allowRemoteServer == other.allowRemoteServer
                    && Objects.equals(serverUrl, other.serverUrl)
                    && Objects.equals(reconnect, other.reconnect)
                    && Objects.equals(client, other.client);
        }

        @Override
        public int hashCode() {
            return Objects.hash(serverUrl, autoConnect, autoScan, allowRemoteServer, reconnect, client);
        }

        @Override
        public String toString() {
            return "Buttplug[serverUrl=" + serverUrl + ", autoConnect=" + autoConnect
                    + ", autoScan=" + autoScan + ", allowRemoteServer=" + allowRemoteServer
                    + ", reconnect=" + reconnect + ", client=" + client + "]";
        }
    }

    /**
     * Local haptic-bridge settings (brief 0002 §4.3, 0003 §3.4). Disabled by default, so a fresh
     * install and every existing config are unaffected. The endpoint is loopback by default; a
     * non-loopback URL is refused unless {@code allowRemote} is set, mirroring the Buttplug remote
     * opt-in.
     */
    public static final class Bridge implements ConfigValue {
        private final String name;
        private final boolean enabled;
        private final String url;
        private final String transport;
        private final boolean allowRemote;
        // Immutable internal identity, kept separate from the display name. The runtime keys backends on
        // this, so renaming a bridge keeps its connection instead of tearing it down, and two rows can be
        // told apart even if their names momentarily match. Declared last so the config adapter's
        // field-order constructor still lines up and an older file without it reads through migration.
        private final String id;

        /** Convenience for new bridges: generates a fresh id. Not used by the config adapter. */
        public Bridge(String name, boolean enabled, String url, String transport, boolean allowRemote) {
            this(name, enabled, url, transport, allowRemote, generateId());
        }

        public Bridge(String name, boolean enabled, String url, String transport, boolean allowRemote,
                      String id) {
            this.name = name == null || name.trim().isEmpty() ? "bridge" : name.trim();
            this.enabled = enabled;
            this.url = url == null || url.trim().isEmpty() ? "tcp://127.0.0.1:12347" : url;
            this.transport = transport == null || transport.trim().isEmpty()
                    ? "tcp" : transport.trim().toLowerCase(java.util.Locale.ROOT);
            this.allowRemote = allowRemote;
            this.id = id == null || id.trim().isEmpty() ? generateId() : id.trim();
        }

        private static String generateId() {
            return java.util.UUID.randomUUID().toString().substring(0, 8);
        }

        /** A user-facing label, unique among endpoints, so several bridges can be told apart. */
        public String name() {
            return name;
        }

        /** Immutable internal identity the runtime keys on; stable across a rename. */
        public String id() {
            return id;
        }

        public boolean enabled() {
            return enabled;
        }

        public String url() {
            return url;
        }

        /** Which transport carries the bridge frames: {@code "tcp"} (default, both loaders) or another
         *  the loader supports (e.g. a modern-only {@code "websocket"}). */
        public String transport() {
            return transport;
        }

        public boolean allowRemote() {
            return allowRemote;
        }

        public static Bridge defaults() {
            // A fixed id so the seed bridge is deterministic across fresh installs and round trips.
            return new Bridge("local", false, "tcp://127.0.0.1:12347", "tcp", false, "local");
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Bridge)) {
                return false;
            }
            Bridge other = (Bridge) o;
            return enabled == other.enabled
                    && allowRemote == other.allowRemote
                    && Objects.equals(name, other.name)
                    && Objects.equals(url, other.url)
                    && Objects.equals(transport, other.transport)
                    && Objects.equals(id, other.id);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, enabled, url, transport, allowRemote, id);
        }

        @Override
        public String toString() {
            return "Bridge[name=" + name + ", enabled=" + enabled + ", url=" + url + ", transport="
                    + transport + ", allowRemote=" + allowRemote + ", id=" + id + "]";
        }
    }

    /** Default set of bridge endpoints: one disabled loopback entry, so a fresh install has a row to edit. */
    public static java.util.List<Bridge> defaultBridges() {
        return Collections.singletonList(Bridge.defaults());
    }

    /** A complete default configuration (matches {@code config.example.yaml} intent). */
    public static HapticConfig defaults() {
        return new HapticConfig(
                CURRENT_SCHEMA_VERSION,
                Profile.defaults(),
                Global.defaults(),
                Buttplug.defaults(),
                defaultEvents(),
                defaultOutputPolicy(),
                Collections.emptyMap(),
                Collections.emptyMap(),
                AccumulationParams.defaults(),
                CustomIntensities.legacyDefaults(),
                defaultBridges());
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
        return Collections.unmodifiableMap(m);
    }

    private static Map<String, OutputPolicy> defaultOutputPolicy() {
        Map<String, OutputPolicy> m = new LinkedHashMap<>();
        // Only the kinds a recipe route can target need an entry; the rest are never chosen anyway.
        // Oscillators and rotators are continuous, vibrate-equivalent outputs. Strokers (position) are
        // enabled too, but only move once the device is calibrated (SceneMixer.buildTarget).
        m.put(OutputKind.VIBRATE.wireName(), OutputPolicy.on());
        m.put(OutputKind.OSCILLATE.wireName(), OutputPolicy.on());
        m.put(OutputKind.ROTATE.wireName(), OutputPolicy.on());
        m.put(OutputKind.POSITION.wireName(), OutputPolicy.on());
        m.put(OutputKind.HW_POSITION_WITH_DURATION.wireName(), OutputPolicy.on());
        return Collections.unmodifiableMap(m);
    }
}
