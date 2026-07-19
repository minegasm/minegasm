package net.minegasm.observe;

import net.minegasm.config.MinegasmMode;
import net.minegasm.config.RuntimeConfig;
import net.minegasm.core.GameEventKind;
import net.minegasm.core.HapticIntent;
import net.minegasm.core.MaterialFeel;
import net.minegasm.core.RawGameEvent;
import net.minegasm.core.SpatialDirection;
import net.minegasm.util.HapticMath;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Turns per-tick discrete events plus continuous-state transitions into normalized
 * {@link HapticIntent}s (brief §6.3, §7). Owns the semantic acquisition rules that must be prompt yet
 * de-duplicated: damage merging within a short real-time window, XP orb coalescing, a fishing-bite
 * refractory period, mining-as-continuous, and vitality edge/repeat. Client-thread confined.
 */
public final class HapticAggregator {

    private static final float REFERENCE_DAMAGE = 10.0f;      // HP for full-strength hurt
    private static final long HURT_MERGE_WINDOW_NS = 100_000_000L;   // 100 ms
    private static final long XP_COALESCE_WINDOW_NS = 130_000_000L;  // 130 ms
    private static final int XP_REFERENCE = 64;               // XP amount mapped to ~full strength
    private static final long VITALITY_REPEAT_NS = 3_000_000_000L;   // 3 s
    private static final long FISHING_REFRACTORY_NS = 600_000_000L;  // 600 ms

    private long lastHurtNs;
    private float lastHurtStrength;

    private int pendingXp;
    private long pendingXpFirstNs;
    private boolean pendingXpLeveled;

    private long lastVitalityNs;
    private boolean lastVitalityActive;

    private long lastFishingBiteNs = Long.MIN_VALUE / 2;

    public List<HapticIntent> aggregate(List<RawGameEvent> discrete, StateTransitions transitions,
                                        ClientStateSnapshot snapshot, RuntimeConfig config, long nowNs) {
        List<HapticIntent> intents = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (RawGameEvent e : discrete) {
            String dedupe = e.kind() + "@" + e.gameTick() + ":" + e.getInt("dedupe", e.hashCode());
            if (!seen.add(dedupe)) {
                continue;
            }
            mapDiscrete(e, nowNs).ifPresent(intents::add);
        }

        if (transitions.tookDamage()) {
            intents.add(hurt(transitions, snapshot, nowNs));
        }

        coalesceXp(transitions, snapshot, nowNs).ifPresent(intents::add);

        if (snapshot.mining() && config.eventEnabled(GameEventKind.MINING_ACTIVE)) {
            intents.add(mining(snapshot, nowNs));
        }

        vitality(transitions, config, nowNs).ifPresent(intents::add);

        return intents;
    }

    public void reset() {
        lastHurtNs = 0;
        lastHurtStrength = 0;
        pendingXp = 0;
        pendingXpFirstNs = 0;
        pendingXpLeveled = false;
        lastVitalityNs = 0;
        lastVitalityActive = false;
        lastFishingBiteNs = Long.MIN_VALUE / 2;
    }

    // --- discrete --------------------------------------------------------------------------

    private java.util.Optional<HapticIntent> mapDiscrete(RawGameEvent e, long nowNs) {
        return switch (e.kind()) {
            case ATTACK -> {
                Set<String> tags = e.getBool("critical", false) ? Set.of("critical") : Set.of();
                float strength = e.getFloat("cooldown", 0.6f);
                yield java.util.Optional.of(intent(GameEventKind.ATTACK, strength, MaterialFeel.UNKNOWN,
                        SpatialDirection.FORWARD, tags, e.gameTick(), nowNs));
            }
            case BLOCK_BROKEN -> {
                Set<String> tags = new HashSet<>();
                if (e.getBool("ore", false)) {
                    tags.add("ore");
                }
                float hardness = e.getFloat("hardness", 0.4f);
                yield java.util.Optional.of(intent(GameEventKind.BLOCK_BROKEN, hardness,
                        material(e), SpatialDirection.NONE, tags, e.gameTick(), nowNs));
            }
            case PLACE -> java.util.Optional.of(intent(GameEventKind.PLACE, e.getFloat("strength", 0.4f),
                    material(e), SpatialDirection.NONE, Set.of(), e.gameTick(), nowNs));
            case HARVEST -> java.util.Optional.of(intent(GameEventKind.HARVEST, e.getFloat("strength", 0.5f),
                    material(e), SpatialDirection.NONE, Set.of(), e.gameTick(), nowNs));
            case FISHING_BITE -> {
                if (nowNs - lastFishingBiteNs < FISHING_REFRACTORY_NS) {
                    yield java.util.Optional.empty(); // one pulse per bite
                }
                lastFishingBiteNs = nowNs;
                yield java.util.Optional.of(intent(GameEventKind.FISHING_BITE, 0.7f, MaterialFeel.UNKNOWN,
                        SpatialDirection.NONE, Set.of(), e.gameTick(), nowNs));
            }
            case ADVANCEMENT -> {
                String frame = e.get("frame", String.class).orElse("task").toLowerCase(java.util.Locale.ROOT);
                yield java.util.Optional.of(intent(GameEventKind.ADVANCEMENT, 0.75f, MaterialFeel.UNKNOWN,
                        SpatialDirection.NONE, Set.of(frame), e.gameTick(), nowNs));
            }
            case EXPLOSION -> {
                float power = e.getFloat("power", 4f);
                float distance = e.getFloat("distance", 4f);
                float strength = HapticMath.clamp01(power / Math.max(1f, distance) / 4f);
                yield java.util.Optional.of(intent(GameEventKind.EXPLOSION, strength, MaterialFeel.UNKNOWN,
                        SpatialDirection.OMNI, Set.of(), e.gameTick(), nowNs));
            }
            default -> java.util.Optional.empty();
        };
    }

    // --- hurt merge ------------------------------------------------------------------------

    private HapticIntent hurt(StateTransitions t, ClientStateSnapshot snapshot, long nowNs) {
        float raw = HapticMath.clamp01(-t.effectiveHealthDelta() / REFERENCE_DAMAGE);
        float strength;
        if (nowNs - lastHurtNs <= HURT_MERGE_WINDOW_NS && lastHurtStrength > 0f) {
            // Merge within the window (brief §10.3): max + 0.35*min, clamped.
            strength = HapticMath.clamp01(Math.max(lastHurtStrength, raw)
                    + 0.35f * Math.min(lastHurtStrength, raw));
        } else {
            strength = raw;
        }
        lastHurtNs = nowNs;
        lastHurtStrength = strength;
        Set<String> tags = snapshot.health() > 0f && snapshot.health() <= 6f
                ? Set.of("critical") : Set.of();
        return intent(GameEventKind.HURT, strength, MaterialFeel.UNKNOWN, SpatialDirection.NONE,
                tags, snapshot.gameTick(), nowNs);
    }

    // --- xp coalesce -----------------------------------------------------------------------

    private java.util.Optional<HapticIntent> coalesceXp(StateTransitions t, ClientStateSnapshot snapshot,
                                                        long nowNs) {
        if (t.gainedXp()) {
            if (pendingXp == 0) {
                pendingXpFirstNs = nowNs;
            }
            pendingXp += t.xpGained();
            pendingXpLeveled |= t.leveledUp();
        }
        if (pendingXp <= 0) {
            return java.util.Optional.empty();
        }
        boolean windowElapsed = nowNs - pendingXpFirstNs >= XP_COALESCE_WINDOW_NS;
        if (!windowElapsed && !pendingXpLeveled) {
            return java.util.Optional.empty();
        }
        float strength = HapticMath.clamp01(
                (float) (Math.log1p(pendingXp) / Math.log1p(XP_REFERENCE)));
        Set<String> tags = pendingXpLeveled ? Set.of("levelup") : Set.of();
        long tick = snapshot.gameTick();
        pendingXp = 0;
        pendingXpLeveled = false;
        return java.util.Optional.of(intent(GameEventKind.XP_GAIN, strength, MaterialFeel.UNKNOWN,
                SpatialDirection.NONE, tags, tick, nowNs));
    }

    // --- mining ----------------------------------------------------------------------------

    private HapticIntent mining(ClientStateSnapshot s, long nowNs) {
        Set<String> tags = s.miningTarget().map(pos -> Set.of("pos:" + pos)).orElse(Set.of());
        float strength = s.miningHardness() > 0 ? s.miningHardness() : s.miningProgress();
        return intent(GameEventKind.MINING_ACTIVE, strength, s.miningMaterial(),
                SpatialDirection.FORWARD, tags, s.gameTick(), nowNs);
    }

    // --- vitality --------------------------------------------------------------------------

    private java.util.Optional<HapticIntent> vitality(StateTransitions t, RuntimeConfig config, long nowNs) {
        boolean critical = t.vitalityCritical();
        boolean active = config.mode() == MinegasmMode.MASOCHIST ? critical : t.vitalityFull();
        boolean edge = active && !lastVitalityActive;
        boolean repeat = active && (nowNs - lastVitalityNs >= VITALITY_REPEAT_NS);
        lastVitalityActive = active;
        if (!active || (!edge && !repeat)) {
            return java.util.Optional.empty();
        }
        lastVitalityNs = nowNs;
        Set<String> tags = critical ? Set.of("critical") : Set.of();
        return java.util.Optional.of(intent(GameEventKind.VITALITY, critical ? 0.7f : 0.4f,
                MaterialFeel.UNKNOWN, SpatialDirection.NONE, tags, 0, nowNs));
    }

    // --- helpers ---------------------------------------------------------------------------

    private static MaterialFeel material(RawGameEvent e) {
        return e.get("material", MaterialFeel.class).orElseGet(() ->
                e.get("material", String.class)
                        .map(HapticAggregator::parseMaterial)
                        .orElse(MaterialFeel.UNKNOWN));
    }

    private static MaterialFeel parseMaterial(String s) {
        try {
            return MaterialFeel.valueOf(s.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return MaterialFeel.UNKNOWN;
        }
    }

    private static HapticIntent intent(GameEventKind kind, float strength, MaterialFeel material,
                                       SpatialDirection direction, Set<String> tags, long tick, long nowNs) {
        return new HapticIntent(kind, kind.key(), strength, strength, material, direction, tags,
                tick, nowNs);
    }
}
