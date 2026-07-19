package gg.meza.feelcraft.device;
import java.util.List;
public record HapticFeature(int featureIndex, String description, List<HapticOutputCapability> outputs) {}
