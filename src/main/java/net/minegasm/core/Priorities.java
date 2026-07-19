package net.minegasm.core;

/**
 * Priority band constants (brief Â§6.6). Higher wins. {@link #CONTROL} bypasses all queues and
 * timing gaps. Kept as data here so the scheduler and mixer never encode per-event {@code if}
 * chains (guideline Â§H).
 */
public final class Priorities {

    private Priorities() {}

    public static final int CONTROL = 1000;
    public static final int DEATH_CRITICAL = 110;
    public static final int EXPLOSION = 100;
    public static final int ATTACK_CONFIRM = 95;
    public static final int HURT = 90;
    public static final int SHIELD_FALL = 80;
    public static final int ADVANCEMENT = 70;
    public static final int VITALITY = 70;
    public static final int FISHING_BITE = 65;
    public static final int BLOCK_BREAK = 60;
    public static final int HARVEST = 60;
    public static final int XP = 50;
    public static final int MINING_TEXTURE = 40;
    public static final int PLACE = 35;
    public static final int AMBIENT = 15;
}

