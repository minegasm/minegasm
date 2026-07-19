package gg.meza.feelcraft.haptics;
public final class HapticMath {
    private HapticMath() {}
    public static float clamp(float x, float min, float max) { return Math.max(min, Math.min(max, x)); }
    public static float smoothstep(float x) { float t = clamp(x, 0.0f, 1.0f); return t * t * (3.0f - 2.0f * t); }
    public static int scale(float normalized, int min, int max) { float t = clamp(normalized, 0.0f, 1.0f); return Math.round(min + (max - min) * t); }
}
