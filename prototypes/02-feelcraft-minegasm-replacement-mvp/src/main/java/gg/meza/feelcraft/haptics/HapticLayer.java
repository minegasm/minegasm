package gg.meza.feelcraft.haptics;
import java.util.Set;
public record HapticLayer(String id, HapticRole role, HapticPrimitive primitive, Set<OutputKind> route, int priority, int startDelayMs, int expiresAfterMs, String coalesceKey) {}
