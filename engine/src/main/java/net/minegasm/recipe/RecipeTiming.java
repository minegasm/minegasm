package net.minegasm.recipe;

import net.minegasm.core.GameEventKind;
import net.minegasm.core.Priorities;

import java.util.Objects;

/**
 * Data-driven priority + expiry per event kind (brief §6.6). Central table so packs and the
 * scheduler never encode per-event {@code if} chains (guideline §H).
 */
public final class RecipeTiming {

    private final int priority;
    private final int expiryMs;

    public RecipeTiming(int priority, int expiryMs) {
        this.priority = priority;
        this.expiryMs = expiryMs;
    }

    public int priority() {
        return priority;
    }

    public int expiryMs() {
        return expiryMs;
    }

    public long expiryNs() {
        return expiryMs * 1_000_000L;
    }

    public static RecipeTiming forKind(GameEventKind kind) {
        switch (kind) {
            case ATTACK:
                return new RecipeTiming(Priorities.ATTACK_CONFIRM, 180);
            case HURT:
                return new RecipeTiming(Priorities.HURT, 250);
            case MINING_ACTIVE:
                return new RecipeTiming(Priorities.MINING_TEXTURE, 180);
            case BLOCK_BROKEN:
                return new RecipeTiming(Priorities.BLOCK_BREAK, 250);
            case PLACE:
                return new RecipeTiming(Priorities.PLACE, 200);
            case HARVEST:
                return new RecipeTiming(Priorities.HARVEST, 300);
            case FISHING_BITE:
                return new RecipeTiming(Priorities.FISHING_BITE, 300);
            case XP_GAIN:
                return new RecipeTiming(Priorities.XP, 350);
            case ADVANCEMENT:
                return new RecipeTiming(Priorities.ADVANCEMENT, 500);
            case VITALITY:
                return new RecipeTiming(Priorities.VITALITY, 500);
            case EXPLOSION:
                return new RecipeTiming(Priorities.EXPLOSION, 200);
            case AMBIENT:
                return new RecipeTiming(Priorities.AMBIENT, 100);
            default:
                throw new IllegalStateException("Unhandled GameEventKind: " + kind);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RecipeTiming)) {
            return false;
        }
        RecipeTiming other = (RecipeTiming) o;
        return priority == other.priority && expiryMs == other.expiryMs;
    }

    @Override
    public int hashCode() {
        return Objects.hash(priority, expiryMs);
    }

    @Override
    public String toString() {
        return "RecipeTiming[priority=" + priority + ", expiryMs=" + expiryMs + "]";
    }
}
