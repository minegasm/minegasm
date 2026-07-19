package gg.meza.feelcraft.device;
import java.util.List;
public record HapticDevice(int deviceIndex, String name, String displayName, int timingGapMs, List<HapticFeature> features) {}
