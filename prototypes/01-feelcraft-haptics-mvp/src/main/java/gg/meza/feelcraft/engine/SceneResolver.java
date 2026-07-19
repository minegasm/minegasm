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
        throw new IllegalArgumentException("Unknown haptic intent " + intent);
    }
    private HapticScene playerHurt(float s) {
        HapticLayer impact = new HapticLayer("hurt_impact", HapticRole.IMPACT, new HapticPrimitive.Impulse(0.30f + s * 0.50f, (int)(90 + s * 120), 8, 60), Set.of(OutputKind.VIBRATE, OutputKind.HW_POSITION_WITH_DURATION, OutputKind.OSCILLATE), 90, 0, 250, "player_hurt");
        return new HapticScene("player_hurt", 90, List.of(impact));
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
}
