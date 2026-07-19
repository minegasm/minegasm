package gg.meza.feelcraft.device;
import gg.meza.feelcraft.haptics.HapticMath;
import gg.meza.feelcraft.haptics.OutputKind;
public record HapticOutputCapability(OutputKind kind, int minValue, int maxValue, Integer minDurationMs, Integer maxDurationMs) {
    public int scale(float normalized) { return HapticMath.scale(normalized, minValue, maxValue); }
    public int clampDuration(int durationMs) { int min = minDurationMs == null ? 0 : minDurationMs; int max = maxDurationMs == null ? Integer.MAX_VALUE : maxDurationMs; return Math.max(min, Math.min(max, durationMs)); }
}
