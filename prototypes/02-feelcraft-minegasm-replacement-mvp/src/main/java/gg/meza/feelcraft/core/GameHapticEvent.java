package gg.meza.feelcraft.core;

public sealed interface GameHapticEvent permits
        GameHapticEvent.PlayerDamaged,
        GameHapticEvent.MiningTick,
        GameHapticEvent.BlockBroken,
        GameHapticEvent.LowHealth,
        GameHapticEvent.PlayerAttack,
        GameHapticEvent.XpChanged,
        GameHapticEvent.Harvest,
        GameHapticEvent.Vitality {
    int priority();

    static PlayerDamaged playerDamaged(float amount) { return new PlayerDamaged(amount); }
    static MiningTick miningTick(MaterialFeel material, float hardness) { return new MiningTick(material, hardness); }
    static BlockBroken blockBroken(MaterialFeel material) { return new BlockBroken(material); }
    static LowHealth lowHealth(float healthFraction) { return new LowHealth(healthFraction); }
    static PlayerAttack playerAttack(boolean entityTargeted) { return new PlayerAttack(entityTargeted); }
    static XpChanged xpChanged(int delta) { return new XpChanged(delta); }
    static Harvest harvest(MaterialFeel material) { return new Harvest(material); }
    static Vitality vitality(float healthFraction, float foodFraction) { return new Vitality(healthFraction, foodFraction); }

    record PlayerDamaged(float amount) implements GameHapticEvent { @Override public int priority() { return 90; } }
    record MiningTick(MaterialFeel material, float hardness) implements GameHapticEvent { @Override public int priority() { return 40; } }
    record BlockBroken(MaterialFeel material) implements GameHapticEvent { @Override public int priority() { return 60; } }
    record LowHealth(float healthFraction) implements GameHapticEvent { @Override public int priority() { return 70; } }
    record PlayerAttack(boolean entityTargeted) implements GameHapticEvent { @Override public int priority() { return 55; } }
    record XpChanged(int delta) implements GameHapticEvent { @Override public int priority() { return 50; } }
    record Harvest(MaterialFeel material) implements GameHapticEvent { @Override public int priority() { return 55; } }
    record Vitality(float healthFraction, float foodFraction) implements GameHapticEvent { @Override public int priority() { return 35; } }
}
