package net.minegasm.recipe;

import net.minegasm.config.RecipePackId;
import net.minegasm.core.CouplingMode;
import net.minegasm.core.DeliveryMode;
import net.minegasm.core.HapticLayer;
import net.minegasm.core.HapticPrimitive;
import net.minegasm.core.HapticPrimitive.Beat;
import net.minegasm.core.HapticRole;
import net.minegasm.core.HapticRoute;
import net.minegasm.core.HapticScene;
import net.minegasm.core.MaterialFeel;
import net.minegasm.core.OutputKind;
import net.minegasm.util.HapticMath;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The modern, shaped recipe pack (brief §8.3, appendix B). Short envelopes rather than long flat
 * output, mining texture plus a completion pop, rhythm/attack/decay to convey importance, and
 * deterministic bounded variation. Amplitudes and durations follow the recipe catalog ranges.
 */
public final class BalancedRecipePack implements RecipePack {

    /** Supplemental experimental motion route: rendered only if opted-in and calibrated. */
    private static final HapticRoute MOTION = new HapticRoute(
            EnumSet.of(OutputKind.HW_POSITION_WITH_DURATION, OutputKind.POSITION),
            true, Set.of(), Set.of(), Set.of(), DeliveryMode.SUPPLEMENTAL);

    @Override
    public RecipePackId id() {
        return RecipePackId.BALANCED;
    }

    @Override
    public Optional<HapticScene> resolve(RecipeContext ctx) {
        return switch (ctx.intent().kind()) {
            case ATTACK -> attack(ctx);
            case HURT -> hurt(ctx);
            case MINING_ACTIVE -> mining(ctx);
            case BLOCK_BROKEN -> blockBreak(ctx);
            case PLACE -> place(ctx);
            case HARVEST -> harvest(ctx);
            case FISHING_BITE -> fishing(ctx);
            case XP_GAIN -> xp(ctx);
            case ADVANCEMENT -> advancement(ctx);
            case VITALITY -> vitality(ctx);
            case EXPLOSION -> explosion(ctx);
            case AMBIENT -> Optional.empty();
        };
    }

    // --- combat ----------------------------------------------------------------------------

    private Optional<HapticScene> attack(RecipeContext ctx) {
        float strength = ctx.intent().hasTag("critical") ? 1.0f : 0.6f;
        float peak = clampRange(ctx.amplitude(strength), 0.20f, 0.45f);
        peak = vary(ctx, peak);
        int dur = varyMs(ctx, 35 + Math.round(55 * strength));
        var impulse = new HapticPrimitive.Impulse(peak, dur, 6, Math.round(dur * 0.5f));
        List<HapticLayer> layers = new ArrayList<>();
        layers.add(vibe(ctx, "impact", HapticRole.IMPACT, impulse, CouplingMode.MAX));
        layers.add(motion(ctx, "impact", scaledMotion(peak, 0.06f), dur));
        return Optional.of(Recipes.scene(ctx, layers));
    }

    private Optional<HapticScene> hurt(RecipeContext ctx) {
        float shape = HapticMath.smoothstep(ctx.intent().strength());
        float peak = clampRange(ctx.amplitude(shape), 0.30f, 0.80f);
        peak = vary(ctx, peak);
        int dur = varyMs(ctx, 90 + Math.round(100 * ctx.intent().strength()));
        var impulse = new HapticPrimitive.Impulse(peak, dur, 8, 55);
        List<HapticLayer> layers = new ArrayList<>();
        layers.add(vibe(ctx, "impact", HapticRole.IMPACT, impulse, CouplingMode.MAX));
        layers.add(motion(ctx, "impact", scaledMotion(peak, 0.16f), dur));
        return Optional.of(Recipes.scene(ctx, layers));
    }

    private Optional<HapticScene> explosion(RecipeContext ctx) {
        float shock = vary(ctx, clampRange(ctx.amplitude(1.0f), 0.55f, 0.90f));
        int shockMs = varyMs(ctx, 120);
        var impulse = new HapticPrimitive.Impulse(shock, shockMs, 4, 60);
        float after = clampRange(shock * 0.6f, 0.20f, 0.55f);
        var rumble = new HapticPrimitive.Rumble(after, 600, 0.6f, true);
        List<HapticLayer> layers = List.of(
                vibe(ctx, "shock", HapticRole.IMPACT, impulse, CouplingMode.EXCLUSIVE),
                Recipes.layer("explosion:after", HapticRole.IMPACT, rumble, HapticRoute.vibrateAll(),
                        CouplingMode.MAX, RecipeTiming.forKind(ctx.intent().kind()).priority(),
                        Recipes.ms(shockMs), Recipes.ms(800), null));
        return Optional.of(Recipes.scene(ctx, "", layers, null, Recipes.ms(200 + 800)));
    }

    // --- mining / breaking / placing -------------------------------------------------------

    private Optional<HapticScene> mining(RecipeContext ctx) {
        MaterialFeel mat = ctx.intent().material();
        float hardness = ctx.intent().strength(); // normalized hardness from observation
        float level = clampRange(ctx.amplitude(0.4f + 0.6f * hardness), 0.10f, 0.32f);
        var texture = new HapticPrimitive.Texture(level, 180, mat.grain(), mat.density(), 0.10f);
        String key = ctx.intent().tags().stream().filter(t -> t.startsWith("pos:")).findFirst()
                .map(t -> "mining:" + t).orElse("mining:active");
        HapticLayer layer = Recipes.layer("mining:texture", HapticRole.TEXTURE, texture,
                HapticRoute.vibrateAll(), CouplingMode.MAX,
                RecipeTiming.forKind(ctx.intent().kind()).priority(),
                0, Recipes.ms(180), key);
        return Optional.of(Recipes.scene(ctx, "", List.of(layer), key, 0));
    }

    private Optional<HapticScene> blockBreak(RecipeContext ctx) {
        boolean ore = ctx.intent().hasTag("ore");
        float hardness = ctx.intent().strength();
        float level = clampRange(ctx.amplitude(0.5f + 0.5f * hardness), 0.20f, 0.52f);
        if (ore) {
            level = HapticMath.clamp01(level + 0.08f);
        }
        level = vary(ctx, level);
        int dur = varyMs(ctx, 70);
        var pop = new HapticPrimitive.Impulse(level, dur, 5, 40);
        List<HapticLayer> layers = new ArrayList<>();
        layers.add(vibe(ctx, "pop", HapticRole.REWARD, pop, CouplingMode.MAX));
        if (ore) {
            // Ore accent: a second micro-beat rather than just a louder pop (brief appendix B).
            var accent = new HapticPrimitive.BeatPattern(List.of(
                    new Beat(dur + 40, HapticMath.clamp01(level * 0.8f), 45)));
            layers.add(Recipes.layer("break:oreAccent", HapticRole.REWARD, accent,
                    HapticRoute.vibrateAll(), CouplingMode.MAX,
                    RecipeTiming.forKind(ctx.intent().kind()).priority(), 0, Recipes.ms(200), null));
        }
        layers.add(motion(ctx, "pop", scaledMotion(level, 0.08f), dur));
        return Optional.of(Recipes.scene(ctx, layers));
    }

    private Optional<HapticScene> place(RecipeContext ctx) {
        MaterialFeel mat = ctx.intent().material();
        float sharp = switch (mat) {
            case METAL, GLASS_CRYSTAL -> 1.0f;
            case WOOL_SOFT, SOIL_CLAY, PLANTS_CROPS -> 0.5f;
            default -> 0.75f;
        };
        float level = vary(ctx, clampRange(ctx.amplitude(sharp), 0.12f, 0.30f));
        int dur = varyMs(ctx, 40 + Math.round(30 * sharp));
        var knock = new HapticPrimitive.Impulse(level, dur, 4, 30);
        return Optional.of(Recipes.scene(ctx, List.of(
                vibe(ctx, "knock", HapticRole.IMPACT, knock, CouplingMode.MAX))));
    }

    private Optional<HapticScene> harvest(RecipeContext ctx) {
        float level = vary(ctx, clampRange(ctx.amplitude(0.6f), 0.15f, 0.35f));
        var pattern = new HapticPrimitive.BeatPattern(List.of(
                new Beat(0, level * 0.8f, 40),
                new Beat(90, level, 50)));
        return Optional.of(Recipes.scene(ctx, List.of(
                vibe(ctx, "reward", HapticRole.REWARD, pattern, CouplingMode.MAX))));
    }

    // --- notifications ---------------------------------------------------------------------

    private Optional<HapticScene> fishing(RecipeContext ctx) {
        float level = clampRange(ctx.amplitude(0.7f), 0.30f, 0.55f);
        var pattern = new HapticPrimitive.BeatPattern(List.of(
                new Beat(0, level * 0.85f, 45),
                new Beat(45 + 75, level, 60)));
        return Optional.of(Recipes.scene(ctx, List.of(
                vibe(ctx, "bite", HapticRole.WARNING, pattern, CouplingMode.MAX))));
    }

    private Optional<HapticScene> xp(RecipeContext ctx) {
        float magnitude = ctx.intent().strength();
        boolean levelUp = ctx.intent().hasTag("levelup");
        int beats = magnitude < 0.33f ? 1 : (magnitude < 0.66f ? 2 : 3);
        float cap = 0.45f;
        List<Beat> list = new ArrayList<>();
        for (int i = 0; i < beats; i++) {
            float lvl = clampRange(ctx.amplitude(0.4f + 0.2f * i), 0.12f, cap);
            list.add(new Beat(i * 70, vary(ctx, lvl), 45));
        }
        if (levelUp) {
            list.add(new Beat(beats * 70 + 30, clampRange(ctx.amplitude(1.0f), 0.20f, cap + 0.10f), 90));
        }
        return Optional.of(Recipes.scene(ctx, List.of(
                vibe(ctx, "sparkle", HapticRole.REWARD, new HapticPrimitive.BeatPattern(list),
                        CouplingMode.MAX))));
    }

    private Optional<HapticScene> advancement(RecipeContext ctx) {
        String tier = ctx.intent().hasTag("challenge") ? "challenge"
                : ctx.intent().hasTag("goal") ? "goal" : "task";
        float base = clampRange(ctx.amplitude(0.8f), 0.20f, 0.55f);
        List<Beat> beats = new ArrayList<>();
        beats.add(new Beat(0, base * 0.6f, 60));
        beats.add(new Beat(140, base * 0.8f, 60));
        beats.add(new Beat(300, base, 80));
        List<HapticLayer> layers = new ArrayList<>();
        long expiry;
        switch (tier) {
            case "goal" -> {
                beats.add(new Beat(430, base, 100));
                expiry = Recipes.ms(700);
            }
            case "challenge" -> {
                beats.add(new Beat(430, HapticMath.clamp01(base + 0.15f), 120));
                layers.add(Recipes.layer("adv:rumble", HapticRole.REWARD,
                        new HapticPrimitive.Rumble(base * 0.5f, 250, 0.5f, true),
                        HapticRoute.vibrateAll(), CouplingMode.MAX,
                        net.minegasm.core.Priorities.ADVANCEMENT, Recipes.ms(560),
                        Recipes.ms(250), null));
                expiry = Recipes.ms(900);
            }
            default -> expiry = Recipes.ms(500);
        }
        layers.add(0, vibe(ctx, "fanfare", HapticRole.REWARD,
                new HapticPrimitive.BeatPattern(beats), CouplingMode.MAX));
        return Optional.of(Recipes.scene(ctx, "", layers, null, expiry));
    }

    private Optional<HapticScene> vitality(RecipeContext ctx) {
        boolean critical = ctx.intent().hasTag("critical");
        float level = critical
                ? clampRange(ctx.amplitude(1.0f), 0.30f, 0.50f)
                : clampRange(ctx.amplitude(0.6f), 0.15f, 0.30f);
        var pattern = new HapticPrimitive.BeatPattern(List.of(
                new Beat(0, level, 60),
                new Beat(120, level, 60)));
        HapticRole role = critical ? HapticRole.WARNING : HapticRole.AMBIENT;
        return Optional.of(Recipes.scene(ctx, List.of(
                vibe(ctx, "pulse", role, pattern, CouplingMode.MAX))));
    }

    // --- helpers ---------------------------------------------------------------------------

    private HapticLayer vibe(RecipeContext ctx, String id, HapticRole role,
                             HapticPrimitive primitive, CouplingMode coupling) {
        RecipeTiming timing = RecipeTiming.forKind(ctx.intent().kind());
        return Recipes.layer(ctx.intent().eventKey() + ":" + id, role, primitive,
                HapticRoute.vibrateAll(), coupling, timing.priority(), 0,
                Math.max(Recipes.ms(primitive.durationMs()), timing.expiryNs()), null);
    }

    private HapticLayer motion(RecipeContext ctx, String id, HapticPrimitive primitive, int durMs) {
        RecipeTiming timing = RecipeTiming.forKind(ctx.intent().kind());
        return Recipes.layer(ctx.intent().eventKey() + ":motion:" + id, HapticRole.IMPACT, primitive,
                MOTION, CouplingMode.MAX, timing.priority(), 0, Recipes.ms(Math.max(durMs, 120)), null);
    }

    /** An impulse used as a small calibrated motion segment (amplitude is a travel fraction). */
    private HapticPrimitive scaledMotion(float level, float travelFraction) {
        float amp = HapticMath.clamp01(level) * travelFraction;
        return new HapticPrimitive.Impulse(amp, 120, 10, 40);
    }

    private static float clampRange(float v, float lo, float hi) {
        return HapticMath.clamp(v, lo, hi);
    }

    private static float vary(RecipeContext ctx, float level) {
        return HapticMath.varyLevel(level, ctx.variationSeed(), ctx.variation() * 0.08f);
    }

    private static int varyMs(RecipeContext ctx, int ms) {
        float jitter = HapticMath.seededJitter(ctx.variationSeed(), 7, ctx.variation() * 0.10f);
        return Math.max(10, Math.round(ms * (1f + jitter)));
    }
}
