package net.minegasm.config.editor;

import net.minegasm.client.MinegasmClient;
import net.minegasm.config.AccumulationParams;
import net.minegasm.config.CustomIntensities;
import net.minegasm.config.EventSetting;
import net.minegasm.config.HapticConfig;
import net.minegasm.config.OutputPolicy;
import net.minegasm.config.ReconnectParams;
import net.minegasm.core.GameEventKind;
import net.minegasm.core.OutputKind;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minecraft-free staged editor for the config members no existing screen exposes: per-event
 * enable/multiplier, per-output-kind enable, the Buttplug reconnect policy, and the mode-gated
 * accumulation and custom-intensity parameter sets. Shaped like {@code ClassicConfigModel}: built
 * from the live config, edited in place by a screen, then {@link #toConfig()} rebuilds a
 * {@link HapticConfig} with only these fields replaced so anything a screen does not model is
 * preserved unchanged. Shared by every modern and classic screen so the rebuild discipline lives in
 * one place.
 */
public final class CustomizationModel {

    /** Events exposed in the customization screen, in display order. AMBIENT is a reserved channel
     * with no default entry and nothing reading it yet, so it is intentionally left out. */
    public static final GameEventKind[] EVENTS = {
            GameEventKind.ATTACK, GameEventKind.HURT, GameEventKind.MINING_ACTIVE,
            GameEventKind.BLOCK_BROKEN, GameEventKind.PLACE, GameEventKind.HARVEST,
            GameEventKind.FISHING_BITE, GameEventKind.XP_GAIN, GameEventKind.ADVANCEMENT,
            GameEventKind.VITALITY, GameEventKind.EXPLOSION,
    };

    /** Output kinds a recipe route can actually target (the same set {@link HapticConfig#defaults()}
     * seeds); the rest have nothing to configure. */
    public static final OutputKind[] OUTPUT_KINDS = {
            OutputKind.VIBRATE, OutputKind.OSCILLATE, OutputKind.ROTATE,
            OutputKind.POSITION, OutputKind.HW_POSITION_WITH_DURATION,
    };

    private final HapticConfig original;

    public final Map<GameEventKind, EventRow> events = new LinkedHashMap<>();
    public final Map<OutputKind, Boolean> outputPolicy = new LinkedHashMap<>();

    public boolean reconnectEnabled;
    public int reconnectMaxDelaySeconds;

    public double accumulationCapacity;
    public double accumulationDecayPerSecond;
    public String accumulationCurve;
    public final Map<String, Double> accumulationContributions = new LinkedHashMap<>();

    public double customAttack;
    public double customHurt;
    public double customMine;
    public double customPlace;
    public double customXpChange;
    public double customFishing;
    public double customHarvest;
    public double customVitality;
    public double customAdvancement;

    public CustomizationModel(HapticConfig original) {
        this.original = original;

        for (GameEventKind kind : EVENTS) {
            EventSetting setting = original.events().getOrDefault(kind.configKey(),
                    EventSetting.enabled(1.0));
            events.put(kind, new EventRow(setting.enabled(), setting.multiplier()));
        }

        // Mirrors RuntimeConfig.policy(): a kind with no entry defaults on for Vibrate, off otherwise.
        for (OutputKind kind : OUTPUT_KINDS) {
            OutputPolicy policy = original.outputPolicy().get(kind.wireName());
            outputPolicy.put(kind, policy != null ? policy.enabled() : kind == OutputKind.VIBRATE);
        }

        ReconnectParams reconnect = original.buttplug().reconnect();
        reconnectEnabled = reconnect.enabled();
        reconnectMaxDelaySeconds = reconnect.maxDelaySeconds();

        AccumulationParams accumulation = original.accumulation();
        accumulationCapacity = accumulation.capacity();
        accumulationDecayPerSecond = accumulation.decayPerSecond();
        accumulationCurve = accumulation.outputCurve();
        accumulationContributions.putAll(accumulation.contributions());

        CustomIntensities custom = original.customIntensity();
        customAttack = custom.attack();
        customHurt = custom.hurt();
        customMine = custom.mine();
        customPlace = custom.place();
        customXpChange = custom.xpChange();
        customFishing = custom.fishing();
        customHarvest = custom.harvest();
        customVitality = custom.vitality();
        customAdvancement = custom.advancement();
    }

    /** Step {@link #accumulationCurve} through its three valid values (see AccumulationProcessor.level()). */
    public void cycleAccumulationCurve() {
        if ("smoothstep".equals(accumulationCurve)) {
            accumulationCurve = "linear";
        } else if ("linear".equals(accumulationCurve)) {
            accumulationCurve = "square";
        } else {
            accumulationCurve = "smoothstep";
        }
    }

    /** Rebuild the persisted config: the original with only the fields this model owns replaced. */
    public HapticConfig toConfig() {
        Map<String, EventSetting> newEvents = new LinkedHashMap<>(original.events());
        for (Map.Entry<GameEventKind, EventRow> entry : events.entrySet()) {
            EventRow row = entry.getValue();
            newEvents.put(entry.getKey().configKey(), EventSetting.of(row.enabled, row.multiplier));
        }

        Map<String, OutputPolicy> newOutputPolicy = new LinkedHashMap<>(original.outputPolicy());
        for (Map.Entry<OutputKind, Boolean> entry : outputPolicy.entrySet()) {
            newOutputPolicy.put(entry.getKey().wireName(),
                    entry.getValue() ? OutputPolicy.on() : OutputPolicy.off());
        }

        HapticConfig.Buttplug b = original.buttplug();
        HapticConfig.Buttplug newButtplug = new HapticConfig.Buttplug(b.serverUrl(), b.autoConnect(),
                b.autoScan(), b.allowRemoteServer(),
                new ReconnectParams(reconnectEnabled, reconnectMaxDelaySeconds), b.client());

        AccumulationParams newAccumulation = new AccumulationParams(accumulationCapacity,
                accumulationDecayPerSecond, accumulationCurve,
                new LinkedHashMap<>(accumulationContributions));

        CustomIntensities newCustomIntensity = new CustomIntensities(customAttack, customHurt,
                customMine, customPlace, customXpChange, customFishing, customHarvest,
                customVitality, customAdvancement);

        return new HapticConfig(original.schemaVersion(), original.profile(), original.global(),
                newButtplug, newEvents, newOutputPolicy, original.devices(),
                original.positionCalibrations(), newAccumulation, newCustomIntensity);
    }

    /** Persist the edits through the shared client. */
    public void apply(MinegasmClient client) {
        client.updateConfig(toConfig());
    }

    /** One event's enable flag and multiplier, edited in place by the screen. */
    public static final class EventRow {
        public boolean enabled;
        public double multiplier;

        public EventRow(boolean enabled, double multiplier) {
            this.enabled = enabled;
            this.multiplier = multiplier;
        }
    }
}
