package gg.meza.feelcraft.engine;

import gg.meza.feelcraft.core.MaterialFeel;
import gg.meza.feelcraft.haptics.*;
import java.util.List;
import java.util.Set;

public final class SceneResolver {
    public HapticScene resolve(HapticIntent intent) {
        if (intent instanceof HapticIntent.PlayerHurt hurt) return playerHurt(hurt.strength());
        if (intent instanceof HapticIntent.BlockBreak blockBreak) return blockBreak(blockBreak.strength(), blockBreak.material());
        if (intent instanceof HapticIntent.MiningTexture mining) return mining(mining.strength(), mining.material());
        if (intent instanceof HapticIntent.LowHealthWarning low) return lowHealth(low.strength());
        if (intent instanceof HapticIntent.PlayerAttack attack) return playerAttack(attack.strength(), attack.entityTargeted());
        if (intent instanceof HapticIntent.XpReward xp) return xpReward(xp.strength());
        if (intent instanceof HapticIntent.HarvestReward harvest) return harvestReward(harvest.strength(), harvest.material());
        if (intent instanceof HapticIntent.VitalityPulse vitality) return vitality(vitality.strength());
        throw new IllegalArgumentException("Unknown haptic intent " + intent);
    }

    private HapticScene playerHurt(float s) {
        HapticLayer impact = new HapticLayer("hurt_impact", HapticRole.IMPACT, new HapticPrimitive.Impulse(0.30f + s * 0.50f, (int)(90 + s * 120), 8, 60), Set.of(OutputKind.VIBRATE, OutputKind.HW_POSITION_WITH_DURATION, OutputKind.OSCILLATE), 90, 0, 250, "player_hurt");
        return new HapticScene("player_hurt", 90, List.of(impact));
    }

    private HapticScene playerAttack(float s, boolean entityTargeted) {
        float level = entityTargeted ? 0.25f + s * 0.35f : 0.18f + s * 0.20f;
        HapticLayer snap = new HapticLayer("attack_snap", HapticRole.IMPACT, new HapticPrimitive.Impulse(level, 80, 5, 35), Set.of(OutputKind.VIBRATE, OutputKind.HW_POSITION_WITH_DURATION, OutputKind.OSCILLATE), 55, 0, 180, "attack");
        return new HapticScene("attack", 55, List.of(snap));
    }

    private HapticScene blockBreak(float strength, MaterialFeel material) {
        HapticLayer pop = new HapticLayer("block_break_pop", HapticRole.REWARD, new HapticPrimitive.Impulse(0.25f + strength * 0.35f, 75, 5, 40), Set.of(OutputKind.VIBRATE, OutputKind.HW_POSITION_WITH_DURATION), 60, 0, 250, "block_break");
        return new HapticScene("block_break", 60, List.of(pop));
    }

    private HapticScene mining(float strength, MaterialFeel material) {
        HapticLayer texture = new HapticLayer("mining_texture", HapticRole.TEXTURE, new HapticPrimitive.Texture(strength, 110, material.grain, material.density, material.irregularity), Set.of(OutputKind.VIBRATE, OutputKind.HW_POSITION_WITH_DURATION, OutputKind.POSITION), 40, 0, 180, "mining_texture");
        return new HapticScene("mining", 40, List.of(texture));
    }

    private HapticScene lowHealth(float strength) {
        HapticLayer heartbeat = new HapticLayer("low_health_heartbeat", HapticRole.WARNING, new HapticPrimitive.Pattern(List.of(new HapticPrimitive.Beat(0, strength, 55), new HapticPrimitive.Beat(125, strength * 0.75f, 50))), Set.of(OutputKind.VIBRATE), 70, 0, 400, "low_health");
        return new HapticScene("low_health", 70, List.of(heartbeat));
    }

    private HapticScene xpReward(float strength) {
        HapticLayer sparkle = new HapticLayer("xp_sparkle", HapticRole.REWARD, new HapticPrimitive.Pattern(List.of(new HapticPrimitive.Beat(0, strength * 0.65f, 35), new HapticPrimitive.Beat(70, strength, 40), new HapticPrimitive.Beat(140, strength * 0.45f, 30))), Set.of(OutputKind.VIBRATE, OutputKind.HW_POSITION_WITH_DURATION), 50, 0, 300, "xp_change");
        return new HapticScene("xp_change", 50, List.of(sparkle));
    }

    private HapticScene harvestReward(float strength, MaterialFeel material) {
        HapticLayer harvest = new HapticLayer("harvest_pop", HapticRole.REWARD, new HapticPrimitive.Impulse(0.20f + strength * 0.35f, 90, 8, 45), Set.of(OutputKind.VIBRATE, OutputKind.HW_POSITION_WITH_DURATION), 55, 0, 260, "harvest");
        return new HapticScene("harvest", 55, List.of(harvest));
    }

    private HapticScene vitality(float strength) {
        HapticLayer pulse = new HapticLayer("vitality_pulse", HapticRole.REWARD, new HapticPrimitive.Pattern(List.of(new HapticPrimitive.Beat(0, strength, 70), new HapticPrimitive.Beat(150, strength * 0.55f, 55))), Set.of(OutputKind.VIBRATE), 35, 0, 450, "vitality");
        return new HapticScene("vitality", 35, List.of(pulse));
    }
}
