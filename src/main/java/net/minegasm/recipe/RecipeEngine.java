package net.minegasm.recipe;

import net.minegasm.config.MinegasmMode;
import net.minegasm.config.RecipePackId;
import net.minegasm.config.RuntimeConfig;
import net.minegasm.core.CouplingMode;
import net.minegasm.core.GameEventKind;
import net.minegasm.core.HapticIntent;
import net.minegasm.core.HapticLayer;
import net.minegasm.core.HapticPrimitive;
import net.minegasm.core.HapticRole;
import net.minegasm.core.HapticRoute;
import net.minegasm.core.HapticScene;
import net.minegasm.core.Priorities;

import java.util.List;
import java.util.Optional;

/**
 * The recipe layer entry point (brief §5.2). Applies the layered configuration order — master
 * enable → per-event enable → mode preset base → user multiplier → global intensity — then delegates
 * to the selected recipe pack. Accumulation mode is handled specially by an internal
 * {@link AccumulationProcessor} producing a single continuous charge-driven scene.
 *
 * <p>Stateful only through the accumulator, which is confined to the haptic worker thread.
 */
public final class RecipeEngine implements RecipeResolver {

    private final RecipePack classic = new ClassicRecipePack();
    private final RecipePack balanced = new BalancedRecipePack();
    private final AccumulationProcessor accumulator = new AccumulationProcessor();

    @Override
    public Optional<HapticScene> resolve(HapticIntent intent, RuntimeConfig config) {
        if (!config.enabled()) {
            return Optional.empty();
        }
        GameEventKind kind = intent.kind();
        if (!config.eventEnabled(kind)) {
            return Optional.empty();
        }

        if (config.mode() == MinegasmMode.ACCUMULATION) {
            return resolveAccumulation(intent, config);
        }

        Preset preset = Presets.forMode(config.mode());
        float modeBase = preset.baseFor(kind, config);
        if (modeBase <= 0f) {
            return Optional.empty(); // event disabled in this mode
        }
        float userGain = config.eventMultiplier(kind) * config.globalIntensity();
        RecipeContext ctx = new RecipeContext(intent, modeBase, userGain, config, intent.createdAtNs());
        RecipePack pack = config.recipePack() == RecipePackId.CLASSIC ? classic : balanced;
        return pack.resolve(ctx);
    }

    /**
     * Accumulation mode: fold the event into the charge and emit a single continuous scene whose
     * level reflects the current charge. The worker should also call {@link #tickAccumulation} each
     * cycle so charge decays and the output falls even with no new events.
     */
    private Optional<HapticScene> resolveAccumulation(HapticIntent intent, RuntimeConfig config) {
        var params = config.accumulation();
        long now = intent.createdAtNs();
        accumulator.update(params, now);
        accumulator.contribute(params, intent.kind(), intent.hasTag("ore"), intent.strength());
        float level = accumulator.level(params) * config.globalIntensity();
        return Optional.of(accumulationScene(level, now));
    }

    /** Produce the current accumulation scene after decaying to {@code nowNs} (no new contribution). */
    public Optional<HapticScene> tickAccumulation(RuntimeConfig config, long nowNs) {
        if (!config.enabled() || config.mode() != MinegasmMode.ACCUMULATION) {
            return Optional.empty();
        }
        accumulator.update(config.accumulation(), nowNs);
        float level = accumulator.level(config.accumulation()) * config.globalIntensity();
        if (level <= 0f) {
            return Optional.empty();
        }
        return Optional.of(accumulationScene(level, nowNs));
    }

    public void resetAccumulation() {
        accumulator.reset();
    }

    double accumulationCharge() {
        return accumulator.charge();
    }

    private HapticScene accumulationScene(float level, long nowNs) {
        long expiry = 500L * 1_000_000L; // refreshed continuously; latest-wins
        var hold = new HapticPrimitive.Hold(net.minegasm.util.HapticMath.clamp01(level), 500, 20, 60);
        HapticLayer layer = new HapticLayer("accumulation:charge", HapticRole.TEXTURE, hold,
                HapticRoute.vibrateAll(), CouplingMode.MAX, Priorities.MINING_TEXTURE,
                0, expiry, "accumulation");
        return new HapticScene("accumulation", GameEventKind.AMBIENT, Priorities.MINING_TEXTURE,
                List.of(layer), nowNs, nowNs + expiry, "accumulation");
    }
}
