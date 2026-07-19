package net.minegasm.recipe;

import net.minegasm.core.GameEventKind;
import net.minegasm.core.Priorities;

/**
 * Data-driven priority + expiry per event kind (brief §6.6). Central table so packs and the
 * scheduler never encode per-event {@code if} chains (guideline §H).
 */
public record RecipeTiming(int priority, int expiryMs) {

    public long expiryNs() {
        return expiryMs * 1_000_000L;
    }

    public static RecipeTiming forKind(GameEventKind kind) {
        return switch (kind) {
            case ATTACK -> new RecipeTiming(Priorities.ATTACK_CONFIRM, 180);
            case HURT -> new RecipeTiming(Priorities.HURT, 250);
            case MINING_ACTIVE -> new RecipeTiming(Priorities.MINING_TEXTURE, 180);
            case BLOCK_BROKEN -> new RecipeTiming(Priorities.BLOCK_BREAK, 250);
            case PLACE -> new RecipeTiming(Priorities.PLACE, 200);
            case HARVEST -> new RecipeTiming(Priorities.HARVEST, 300);
            case FISHING_BITE -> new RecipeTiming(Priorities.FISHING_BITE, 300);
            case XP_GAIN -> new RecipeTiming(Priorities.XP, 350);
            case ADVANCEMENT -> new RecipeTiming(Priorities.ADVANCEMENT, 500);
            case VITALITY -> new RecipeTiming(Priorities.VITALITY, 500);
            case EXPLOSION -> new RecipeTiming(Priorities.EXPLOSION, 200);
            case AMBIENT -> new RecipeTiming(Priorities.AMBIENT, 100);
        };
    }
}
