package gg.meza.feelcraft.haptics;
import java.util.List;
public record HapticScene(String id, int priority, List<HapticLayer> layers) {}
