package gg.meza.feelcraft.haptics;

import gg.meza.feelcraft.core.MaterialFeel;

public sealed interface HapticIntent permits
        HapticIntent.PlayerHurt,
        HapticIntent.MiningTexture,
        HapticIntent.BlockBreak,
        HapticIntent.LowHealthWarning,
        HapticIntent.PlayerAttack,
        HapticIntent.XpReward,
        HapticIntent.HarvestReward,
        HapticIntent.VitalityPulse {
    int priority();
    float strength();

    record PlayerHurt(float strength) implements HapticIntent { @Override public int priority() { return 90; } }
    record MiningTexture(float strength, MaterialFeel material) implements HapticIntent { @Override public int priority() { return 40; } }
    record BlockBreak(float strength, MaterialFeel material) implements HapticIntent { @Override public int priority() { return 60; } }
    record LowHealthWarning(float strength) implements HapticIntent { @Override public int priority() { return 70; } }
    record PlayerAttack(float strength, boolean entityTargeted) implements HapticIntent { @Override public int priority() { return 55; } }
    record XpReward(float strength) implements HapticIntent { @Override public int priority() { return 50; } }
    record HarvestReward(float strength, MaterialFeel material) implements HapticIntent { @Override public int priority() { return 55; } }
    record VitalityPulse(float strength) implements HapticIntent { @Override public int priority() { return 35; } }
}
