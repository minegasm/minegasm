package gg.meza.feelcraft.engine;
import gg.meza.feelcraft.haptics.HapticScene;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
public final class HapticMixer {
    public List<HapticScene> mix(List<HapticScene> scenes) {
        List<HapticScene> sorted = scenes.stream().sorted(Comparator.comparingInt(HapticScene::priority).reversed()).toList();
        List<HapticScene> result = new ArrayList<>();
        for (HapticScene scene : sorted) {
            if (scene.priority() >= 90) result.removeIf(existing -> existing.priority() < 50);
            if (scene.priority() <= 20 && !result.isEmpty()) continue;
            result.add(scene);
        }
        return result;
    }
}
