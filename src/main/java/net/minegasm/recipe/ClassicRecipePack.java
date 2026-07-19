package net.minegasm.recipe;

import net.minegasm.config.RecipePackId;
import net.minegasm.core.CouplingMode;
import net.minegasm.core.HapticLayer;
import net.minegasm.core.HapticPrimitive;
import net.minegasm.core.HapticRole;
import net.minegasm.core.HapticRoute;
import net.minegasm.core.HapticScene;
import net.minegasm.util.HapticMath;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The Classic recipe pack (brief §3.4, ADR-009). Reproduces the legacy behaviour: a flat
 * plateau at the mode base intensity, a short {@code +0.20} "feedback" boost at the start of most
 * events, vibration-only output, and the legacy durations (attack/mine/place 3&nbsp;s,
 * advancement 5/7/10&nbsp;s by tier, fishing 1.5&nbsp;s, harvest a brief tap). Parity source:
 * legacy {@code AbstractVibrationState} / {@code VibrationState*}.
 *
 * <p>Where the legacy value depends on data we do not carry losslessly (exact XP amount), the
 * duration is approximated from the normalized magnitude and documented; the felt result matches.
 */
public final class ClassicRecipePack implements RecipePack {

    private static final float BOOST = 0.20f;

    @Override
    public RecipePackId id() {
        return RecipePackId.CLASSIC;
    }

    @Override
    public Optional<HapticScene> resolve(RecipeContext ctx) {
        return switch (ctx.intent().kind()) {
            case ATTACK -> plateau(ctx, 3000, 1000, true, HapticRole.IMPACT);
            case HURT -> plateau(ctx, 3000, 1000, true, HapticRole.IMPACT);
            // Legacy "mine" fired on the completed break; ore blocks add the boost window.
            case BLOCK_BROKEN -> plateau(ctx, 3000, ctx.intent().hasTag("ore") ? 1000 : 0,
                    ctx.intent().hasTag("ore"), HapticRole.REWARD);
            // Legacy had no continuous active-mining texture; suppress it in Classic.
            case MINING_ACTIVE -> Optional.empty();
            case PLACE -> plateau(ctx, 3000, 0, false, HapticRole.IMPACT);
            case XP_GAIN -> plateau(ctx, xpDurationMs(ctx), 1000, true, HapticRole.REWARD);
            case ADVANCEMENT -> plateau(ctx, advancementDurationMs(ctx), 1500, true, HapticRole.REWARD);
            case FISHING_BITE -> plateau(ctx, 1500, 0, false, HapticRole.WARNING);
            case HARVEST -> plateau(ctx, 150, 0, false, HapticRole.REWARD);
            case VITALITY -> plateau(ctx, 3000, 3000, true, HapticRole.WARNING);
            case EXPLOSION -> plateau(ctx, 500, 0, false, HapticRole.IMPACT);
            case AMBIENT -> Optional.empty();
        };
    }

    /**
     * Build a flat plateau scene: a base {@code Hold} for {@code plateauMs}, optionally overlaid by
     * a boosted {@code Hold} ({@code base + 0.20}) for {@code boostMs}. Vibration-only.
     */
    private Optional<HapticScene> plateau(RecipeContext ctx, int plateauMs, int boostMs,
                                          boolean boosted, HapticRole role) {
        float base = HapticMath.clamp01(ctx.modeBase() * ctx.userGain());
        if (base <= 0f) {
            return Optional.empty();
        }
        List<HapticLayer> layers = new ArrayList<>();
        int priority = RecipeTiming.forKind(ctx.intent().kind()).priority();

        var basePlateau = new HapticPrimitive.Hold(base, plateauMs, 0, 0);
        layers.add(Recipes.layer(ctx.intent().eventKey() + ":plateau", role, basePlateau,
                HapticRoute.vibrateAll(), CouplingMode.MAX, priority, 0, Recipes.ms(plateauMs), null));

        if (boosted && boostMs > 0) {
            float boostLevel = HapticMath.clamp01(HapticMath.clamp01(ctx.modeBase() + BOOST) * ctx.userGain());
            var boost = new HapticPrimitive.Hold(boostLevel, boostMs, 0, 0);
            layers.add(Recipes.layer(ctx.intent().eventKey() + ":boost", role, boost,
                    HapticRoute.vibrateAll(), CouplingMode.MAX, priority, 0, Recipes.ms(boostMs), null));
        }
        return Optional.of(Recipes.scene(ctx, "", layers, null, Recipes.ms(plateauMs)));
    }

    /** Legacy XP duration was {@code ceil(ln(amount + 0.5))} seconds; approximate from magnitude. */
    private int xpDurationMs(RecipeContext ctx) {
        float strength = ctx.intent().strength();
        // Map normalized magnitude to ~1..4 s, matching the log growth of the legacy formula.
        return Math.round(1000 + 3000 * strength);
    }

    private int advancementDurationMs(RecipeContext ctx) {
        if (ctx.intent().hasTag("challenge")) {
            return 10_000;
        }
        if (ctx.intent().hasTag("goal")) {
            return 7_000;
        }
        return 5_000;
    }
}
