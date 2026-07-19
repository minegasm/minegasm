package gg.meza.feelcraft.engine;
import gg.meza.feelcraft.core.GameHapticEvent;
import gg.meza.feelcraft.core.MaterialFeel;
import gg.meza.feelcraft.haptics.HapticIntent;
import gg.meza.feelcraft.haptics.HapticMath;
import java.util.ArrayList;
import java.util.List;
public final class HapticAggregator {
    public List<HapticIntent> aggregate(List<GameHapticEvent> events) {
        List<HapticIntent> intents = new ArrayList<>();
        float damage = 0.0f;
        GameHapticEvent.BlockBroken lastBreak = null;
        GameHapticEvent.MiningTick lastMining = null;
        GameHapticEvent.LowHealth lastLowHealth = null;
        for (GameHapticEvent event : events) {
            if (event instanceof GameHapticEvent.PlayerDamaged d) damage += d.amount();
            else if (event instanceof GameHapticEvent.BlockBroken b) lastBreak = b;
            else if (event instanceof GameHapticEvent.MiningTick m) lastMining = m;
            else if (event instanceof GameHapticEvent.LowHealth l) lastLowHealth = l;
        }
        if (damage > 0.0f) intents.add(new HapticIntent.PlayerHurt(HapticMath.smoothstep(damage / 10.0f)));
        if (lastBreak != null) intents.add(new HapticIntent.BlockBreak(0.45f, lastBreak.material()));
        if (lastMining != null) {
            MaterialFeel material = lastMining.material() == null ? MaterialFeel.GENERIC : lastMining.material();
            float strength = 0.12f + HapticMath.clamp(Math.max(0.0f, lastMining.hardness()) / 15.0f, 0.0f, 1.0f) * 0.25f;
            intents.add(new HapticIntent.MiningTexture(strength, material));
        }
        if (lastLowHealth != null) {
            float danger = 1.0f - HapticMath.clamp(lastLowHealth.healthFraction() / 0.30f, 0.0f, 1.0f);
            intents.add(new HapticIntent.LowHealthWarning(0.20f + danger * 0.25f));
        }
        return intents;
    }
}
