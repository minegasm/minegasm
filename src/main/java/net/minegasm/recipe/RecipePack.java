package net.minegasm.recipe;

import net.minegasm.config.RecipePackId;
import net.minegasm.core.HapticScene;

import java.util.Optional;

/**
 * A recipe pack turns a resolved {@link RecipeContext} into an optional {@link HapticScene}. Two
 * implementations exist: {@link ClassicRecipePack} (legacy Minegasm parity) and {@link BalancedRecipePack}
 * (modern shaped). Packs are pure and deterministic given their inputs (brief §3.4, ADR-009).
 */
public interface RecipePack {

    RecipePackId id();

    Optional<HapticScene> resolve(RecipeContext ctx);
}
