package net.minegasm.recipe;

import net.minegasm.config.AccumulationParams;
import net.minegasm.config.RecipePackId;
import net.minegasm.config.RuntimeConfig;
import net.minegasm.core.CouplingMode;
import net.minegasm.core.DeliveryMode;
import net.minegasm.core.GameEventKind;
import net.minegasm.core.HapticIntent;
import net.minegasm.core.HapticLayer;
import net.minegasm.core.HapticPrimitive;
import net.minegasm.core.HapticRole;
import net.minegasm.core.HapticRoute;
import net.minegasm.core.HapticScene;
import net.minegasm.core.OutputKind;
import net.minegasm.core.Priorities;

import java.util.List;
import java.util.Optional;

/**
 * The recipe layer entry point (brief §5.2). Applies the layered configuration order (master
 * enable → per-event enable → mode preset base → user multiplier → global intensity), then delegates
 * to the selected recipe pack. Accumulation mode is handled specially by an internal
 * {@link AccumulationProcessor} producing a single continuous charge-driven scene.
 *
 * <p>Stateful only through the accumulator, which is confined to the haptic worker thread.
 */
public final class RecipeEngine implements RecipeResolver {

    private static final HapticRoute STROKE_ROUTE = new HapticRoute(
            java.util.EnumSet.of(OutputKind.HW_POSITION_WITH_DURATION, OutputKind.POSITION),
            java.util.Collections.emptySet(), java.util.Collections.emptySet(),
            java.util.Collections.emptySet(), DeliveryMode.SUPPLEMENTAL);

    private final RecipePack classic = new ClassicRecipePack();
    private final RecipePack balanced = new BalancedRecipePack();
    private final AccumulationProcessor accumulator = new AccumulationProcessor();
    private final StrokeProcessor stroker = new StrokeProcessor();

    @Override
    public Optional<HapticScene> resolve(HapticIntent intent, RuntimeConfig config) {
        if (!config.enabled()) {
            return Optional.empty();
        }
        GameEventKind kind = intent.kind();
        if (!config.eventEnabled(kind)) {
            return Optional.empty();
        }

        if (config.mode().isMomentum()) {
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
        if (pack == balanced) {
            // Feed the rhythmic-stroke drive; only firing Balanced events count as activity.
            stroker.contribute(intent.strength());
        }
        return pack.resolve(ctx);
    }

    /**
     * Accumulation mode: fold the event into the charge and emit a single continuous scene whose
     * level reflects the current charge. The worker should also call {@link #tickAccumulation} each
     * cycle so charge decays and the output falls even with no new events.
     */
    private Optional<HapticScene> resolveAccumulation(HapticIntent intent, RuntimeConfig config) {
        AccumulationParams params = config.accumulation();
        long now = intent.createdAtNs();
        accumulator.update(params, now);
        accumulator.contribute(params, intent.kind(), intent.hasTag("ore"), intent.strength());
        float level = accumulator.level(params) * config.globalIntensity();
        return Optional.of(accumulationScene(level, now));
    }

    /** Produce the current accumulation scene after decaying to {@code nowNs} (no new contribution). */
    public Optional<HapticScene> tickAccumulation(RuntimeConfig config, long nowNs) {
        if (!config.enabled() || !config.mode().isMomentum()) {
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

    /**
     * Emit/refresh the continuous rhythmic-stroke scene for position devices (Balanced only). Called
     * every client tick like {@link #tickAccumulation}; the charge decays here so stroking fades when
     * gameplay goes quiet. The scene keeps a stable creation time (the stroke origin) so its slow phase
     * advances across the per-tick refreshes; SceneMixer turns it into stroke waypoints.
     */
    public Optional<HapticScene> tickStroke(RuntimeConfig config, long nowNs) {
        stroker.update(nowNs);
        if (!config.enabled() || config.recipePack() == RecipePackId.CLASSIC || !stroker.active()) {
            return Optional.empty();
        }
        long origin = stroker.strokeOriginNs(nowNs);
        long expiry = 500L * 1_000_000L; // rolling; the scene fades ~0.5 s after ticks stop
        float depth = net.minegasm.util.HapticMath.clamp01(
                stroker.depth() * (float) config.globalIntensity());
        HapticPrimitive.Oscillation osc =
                new HapticPrimitive.Oscillation(depth, stroker.periodMs(), Integer.MAX_VALUE);
        HapticLayer layer = new HapticLayer("stroke:drive", HapticRole.TEXTURE, osc, STROKE_ROUTE,
                CouplingMode.MAX, Priorities.MINING_TEXTURE, 0, Long.MAX_VALUE / 4, "stroke");
        return Optional.of(new HapticScene("stroke", GameEventKind.AMBIENT, Priorities.MINING_TEXTURE,
                java.util.Collections.singletonList(layer), origin, nowNs + expiry, "stroke"));
    }

    public void resetStroke() {
        stroker.reset();
    }

    private HapticScene accumulationScene(float level, long nowNs) {
        long expiry = 500L * 1_000_000L; // refreshed continuously; latest-wins
        HapticPrimitive.Hold hold = new HapticPrimitive.Hold(net.minegasm.util.HapticMath.clamp01(level), 500, 20, 60);
        HapticLayer layer = new HapticLayer("accumulation:charge", HapticRole.TEXTURE, hold,
                HapticRoute.buzzAll(), CouplingMode.MAX, Priorities.MINING_TEXTURE,
                0, expiry, "accumulation");
        return new HapticScene("accumulation", GameEventKind.AMBIENT, Priorities.MINING_TEXTURE,
                java.util.Collections.singletonList(layer), nowNs, nowNs + expiry, "accumulation");
    }
}
