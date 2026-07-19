package gg.meza.feelcraft.haptics;
import java.util.List;
public sealed interface HapticPrimitive permits HapticPrimitive.Impulse, HapticPrimitive.Texture, HapticPrimitive.Rumble, HapticPrimitive.Pattern {
    record Impulse(float level, int durationMs, int attackMs, int releaseMs) implements HapticPrimitive {}
    record Texture(float level, int durationMs, float grain, float density, float irregularity) implements HapticPrimitive {}
    record Rumble(float level, int durationMs, boolean decay) implements HapticPrimitive {}
    record Pattern(List<Beat> beats) implements HapticPrimitive {}
    record Beat(int atMs, float level, int durationMs) {}
}
