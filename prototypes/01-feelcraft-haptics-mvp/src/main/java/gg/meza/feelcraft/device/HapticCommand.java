package gg.meza.feelcraft.device;
import gg.meza.feelcraft.haptics.OutputKind;
public record HapticCommand(int deviceIndex, int featureIndex, OutputKind outputKind, int value, Integer durationMs, int priority, long earliestSendNs, long expiresAtNs, String coalesceKey) {}
