package net.minegasm.recipe;

import net.minegasm.core.HapticScene;
import net.minegasm.pack.ScenePack;

import java.util.Optional;

/**
 * Renders a user-supplied {@link ScenePack} as a {@link RecipePack} (brief 0003 §2.4). The pack
 * materializes its authored scene scaled by the user's volume ({@code userGain} = master intensity
 * times the per-event multiplier) and, per layer, by the event's strength (Tier 2 strength response).
 * The mode preset's shaping is not applied: the author already chose the levels.
 *
 * <p>All scaling and clamping happen in the pack's own materialization, which touches amplitude only,
 * so this class is just the adapter from a {@link RecipeContext} to the pack's inputs.
 */
public final class FileRecipePack implements RecipePack {

    private final ScenePack pack;

    public FileRecipePack(ScenePack pack) {
        if (pack == null) {
            throw new IllegalArgumentException("pack required");
        }
        this.pack = pack;
    }

    @Override
    public String id() {
        return pack.packId();
    }

    @Override
    public Optional<HapticScene> resolve(RecipeContext ctx) {
        return pack.resolve(ctx.intent().kind(), ctx.nowNs(), ctx.userGain(), ctx.intent().strength());
    }
}
