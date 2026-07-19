package gg.meza.feelcraft.config;

import gg.meza.feelcraft.haptics.OutputKind;

/**
 * MVP config defaults. The mode/intensity names intentionally mirror Minegasm so
 * users can migrate minegasm-client.toml behavior to Feelcraft without relearning
 * the event model.
 */
public record HapticConfig(
        boolean enabled,
        String buttplugUri,
        MinegasmMode mode,
        boolean minegasmCompatibility,
        float globalIntensity,
        float textureMultiplier,
        float attackIntensity,
        float hurtIntensity,
        float mineIntensity,
        float xpChangeIntensity,
        float harvestIntensity,
        float vitalityIntensity,
        float maxVibrateLevel,
        float maxRotateLevel,
        float maxOscillateLevel,
        float maxLinearAmplitude,
        boolean allowConstrict,
        boolean allowTemperature
) {
    public static HapticConfig defaults() {
        // NORMAL matches Minegasm's documented preset: attack 60%, mine 80%, XP 100%, others off.
        return new HapticConfig(
                true,
                "ws://127.0.0.1:12345/buttplug",
                MinegasmMode.NORMAL,
                true,
                0.75f,
                0.65f,
                0.60f,
                1.00f,
                0.80f,
                1.00f,
                0.20f,
                0.10f,
                0.80f,
                0.35f,
                0.50f,
                0.22f,
                false,
                false
        );
    }

    public float eventIntensity(LegacyEventType eventType) {
        return switch (mode) {
            case NORMAL -> switch (eventType) {
                case ATTACK -> 0.60f;
                case HURT -> 0.00f;
                case MINE -> 0.80f;
                case XP_CHANGE -> 1.00f;
                case HARVEST -> 0.00f;
                case VITALITY -> 0.00f;
            };
            case MASOCHIST -> switch (eventType) {
                case ATTACK -> 0.00f;
                case HURT -> 1.00f;
                case MINE -> 0.00f;
                case XP_CHANGE -> 0.00f;
                case HARVEST -> 0.00f;
                case VITALITY -> 0.10f;
            };
            case HEDONIST -> switch (eventType) {
                case ATTACK -> 0.60f;
                case HURT -> 0.10f;
                case MINE -> 0.80f;
                case XP_CHANGE -> 1.00f;
                case HARVEST -> 0.20f;
                case VITALITY -> 0.10f;
            };
            case CUSTOM -> customEventIntensity(eventType);
        };
    }

    private float customEventIntensity(LegacyEventType eventType) {
        return switch (eventType) {
            case ATTACK -> attackIntensity;
            case HURT -> hurtIntensity;
            case MINE -> mineIntensity;
            case XP_CHANGE -> xpChangeIntensity;
            case HARVEST -> harvestIntensity;
            case VITALITY -> vitalityIntensity;
        };
    }

    public float maxLevelFor(OutputKind kind) {
        return switch (kind) {
            case VIBRATE -> maxVibrateLevel;
            case ROTATE -> maxRotateLevel;
            case OSCILLATE -> maxOscillateLevel;
            case POSITION, HW_POSITION_WITH_DURATION -> maxLinearAmplitude;
            case CONSTRICT -> allowConstrict ? 0.35f : 0.0f;
            case TEMPERATURE -> allowTemperature ? 0.25f : 0.0f;
            case LED -> 0.30f;
        };
    }
}
