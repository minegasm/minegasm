package gg.meza.feelcraft.engine;

import gg.meza.feelcraft.config.HapticConfig;
import gg.meza.feelcraft.config.LegacyEventType;
import gg.meza.feelcraft.core.GameHapticEvent;
import gg.meza.feelcraft.core.MaterialFeel;
import gg.meza.feelcraft.haptics.HapticIntent;
import gg.meza.feelcraft.haptics.HapticMath;
import java.util.ArrayList;
import java.util.List;

/**
 * Coalesces raw per-tick Minecraft observations into a small number of semantic
 * haptic intents. This layer implements Minegasm-compatible modes while still
 * producing Feelcraft's richer semantic events.
 */
public final class HapticAggregator {
    private final HapticConfig config;

    public HapticAggregator(HapticConfig config) {
        this.config = config;
    }

    public List<HapticIntent> aggregate(List<GameHapticEvent> events) {
        List<HapticIntent> intents = new ArrayList<>();
        float damage = 0.0f;
        boolean attackedEntity = false;
        int xpDelta = 0;
        GameHapticEvent.BlockBroken lastBreak = null;
        GameHapticEvent.MiningTick lastMining = null;
        GameHapticEvent.LowHealth lastLowHealth = null;
        GameHapticEvent.Harvest lastHarvest = null;
        GameHapticEvent.Vitality lastVitality = null;

        for (GameHapticEvent event : events) {
            if (event instanceof GameHapticEvent.PlayerDamaged d) damage += d.amount();
            else if (event instanceof GameHapticEvent.PlayerAttack a) attackedEntity = attackedEntity || a.entityTargeted();
            else if (event instanceof GameHapticEvent.XpChanged xp) xpDelta += Math.max(0, xp.delta());
            else if (event instanceof GameHapticEvent.BlockBroken b) lastBreak = b;
            else if (event instanceof GameHapticEvent.MiningTick m) lastMining = m;
            else if (event instanceof GameHapticEvent.LowHealth l) lastLowHealth = l;
            else if (event instanceof GameHapticEvent.Harvest h) lastHarvest = h;
            else if (event instanceof GameHapticEvent.Vitality v) lastVitality = v;
        }

        float hurtMode = config.eventIntensity(LegacyEventType.HURT);
        if (damage > 0.0f && hurtMode > 0.0f) {
            intents.add(new HapticIntent.PlayerHurt(HapticMath.clamp(HapticMath.smoothstep(damage / 10.0f) * hurtMode, 0.0f, 1.0f)));
        }

        float attackMode = config.eventIntensity(LegacyEventType.ATTACK);
        if (attackedEntity && attackMode > 0.0f) {
            intents.add(new HapticIntent.PlayerAttack(attackMode, true));
        }

        if (lastBreak != null) {
            // Feelcraft-specific completion pop. Kept even though Minegasm did not expose a separate block-break toggle.
            intents.add(new HapticIntent.BlockBreak(0.45f, lastBreak.material()));
        }

        float mineMode = config.eventIntensity(LegacyEventType.MINE);
        if (lastMining != null && mineMode > 0.0f) {
            MaterialFeel material = lastMining.material() == null ? MaterialFeel.GENERIC : lastMining.material();
            float base = 0.12f + HapticMath.clamp(Math.max(0.0f, lastMining.hardness()) / 15.0f, 0.0f, 1.0f) * 0.25f;
            intents.add(new HapticIntent.MiningTexture(HapticMath.clamp(base * mineMode, 0.0f, 1.0f), material));
        }

        float xpMode = config.eventIntensity(LegacyEventType.XP_CHANGE);
        if (xpDelta > 0 && xpMode > 0.0f) {
            float strength = HapticMath.clamp((0.30f + Math.min(20, xpDelta) / 20.0f * 0.35f) * xpMode, 0.0f, 1.0f);
            intents.add(new HapticIntent.XpReward(strength));
        }

        float harvestMode = config.eventIntensity(LegacyEventType.HARVEST);
        if (lastHarvest != null && harvestMode > 0.0f) {
            intents.add(new HapticIntent.HarvestReward(0.35f * harvestMode, lastHarvest.material()));
        }

        if (lastLowHealth != null) {
            float danger = 1.0f - HapticMath.clamp(lastLowHealth.healthFraction() / 0.30f, 0.0f, 1.0f);
            intents.add(new HapticIntent.LowHealthWarning(0.20f + danger * 0.25f));
        }

        float vitalityMode = config.eventIntensity(LegacyEventType.VITALITY);
        if (lastVitality != null && vitalityMode > 0.0f) {
            float health = HapticMath.clamp(lastVitality.healthFraction(), 0.0f, 1.0f);
            float food = HapticMath.clamp(lastVitality.foodFraction(), 0.0f, 1.0f);
            float vitality = (health + food) * 0.5f;
            intents.add(new HapticIntent.VitalityPulse(HapticMath.clamp((0.15f + vitality * 0.35f) * vitalityMode, 0.0f, 1.0f)));
        }

        return intents;
    }
}
