package net.minegasm.recipe;

import net.minegasm.config.RuntimeConfig;
import net.minegasm.core.HapticIntent;
import net.minegasm.core.HapticScene;

import java.util.Optional;

/**
 * Resolves a normalized {@link HapticIntent} into an optional {@link HapticScene} under the current
 * config (brief §5.2, domain interfaces). Implementations are the entry point from the aggregator
 * into the recipe layer.
 */
public interface RecipeResolver {

    Optional<HapticScene> resolve(HapticIntent intent, RuntimeConfig config);
}
