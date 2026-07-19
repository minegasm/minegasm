package gg.meza.feelcraft.config;
import gg.meza.feelcraft.haptics.OutputKind;
public record HapticConfig(boolean enabled, String buttplugUri, float globalIntensity, float textureMultiplier, float maxVibrateLevel, float maxRotateLevel, float maxOscillateLevel, float maxLinearAmplitude, boolean allowConstrict, boolean allowTemperature) {
    public static HapticConfig defaults() { return new HapticConfig(true, "ws://127.0.0.1:12345", 0.75f, 0.65f, 0.80f, 0.35f, 0.50f, 0.22f, false, false); }
    public float maxLevelFor(OutputKind kind) { return switch (kind) { case VIBRATE -> maxVibrateLevel; case ROTATE -> maxRotateLevel; case OSCILLATE -> maxOscillateLevel; case POSITION, HW_POSITION_WITH_DURATION -> maxLinearAmplitude; case CONSTRICT -> allowConstrict ? 0.35f : 0.0f; case TEMPERATURE -> allowTemperature ? 0.25f : 0.0f; case LED -> 0.30f; }; }
}
