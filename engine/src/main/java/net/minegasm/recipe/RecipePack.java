package net.minegasm.recipe;

import net.minegasm.core.HapticScene;

import java.util.Optional;

/**
 * A recipe pack turns a resolved {@link RecipeContext} into an optional {@link HapticScene}. The
 * built-in implementations are {@link ClassicRecipePack} (legacy Minegasm parity) and
 * {@link BalancedRecipePack} (modern shaped); {@link FileRecipePack} renders a user-supplied scene
 * pack. Packs are pure and deterministic given their inputs (brief §3.4, ADR-009).
 *
 * <p>The id is a stable string ("classic", "balanced", or a file pack's own id) matching the config's
 * {@code recipePack} selector, so built-in and file packs share one identity space (ADR-017).
 */
public interface RecipePack {

    String id();

    Optional<HapticScene> resolve(RecipeContext ctx);
}
