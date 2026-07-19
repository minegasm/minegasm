package gg.meza.feelcraft.core;

public sealed interface GameHapticEvent permits GameHapticEvent.PlayerDamaged, GameHapticEvent.MiningTick, GameHapticEvent.BlockBroken, GameHapticEvent.LowHealth {
    int priority();

    static PlayerDamaged playerDamaged(float amount) { return new PlayerDamaged(amount); }
    static MiningTick miningTick(MaterialFeel material, float hardness) { return new MiningTick(material, hardness); }
    static BlockBroken blockBroken(MaterialFeel material) { return new BlockBroken(material); }
    static LowHealth lowHealth(float healthFraction) { return new LowHealth(healthFraction); }

    record PlayerDamaged(float amount) implements GameHapticEvent { @Override public int priority() { return 90; } }
    record MiningTick(MaterialFeel material, float hardness) implements GameHapticEvent { @Override public int priority() { return 40; } }
    record BlockBroken(MaterialFeel material) implements GameHapticEvent { @Override public int priority() { return 60; } }
    record LowHealth(float healthFraction) implements GameHapticEvent { @Override public int priority() { return 70; } }
}
