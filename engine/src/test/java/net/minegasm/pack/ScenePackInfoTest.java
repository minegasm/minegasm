package net.minegasm.pack;

import net.minegasm.core.CouplingMode;
import net.minegasm.core.DeliveryMode;
import net.minegasm.core.GameEventKind;
import net.minegasm.core.HapticPrimitive;
import net.minegasm.core.HapticRole;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The display summary a pack manager reads: metadata, id-as-selector, and blank-name fallback. */
class ScenePackInfoTest {

    @Test
    void summarizesLoadedPacksInOrder() {
        PackRegistry registry = new PackRegistry();
        registry.register(pack("a.pack", "Alpha"));
        registry.register(pack("b.pack", ""));

        List<ScenePackInfo> infos = ScenePackInfo.from(registry);

        assertEquals(2, infos.size());
        assertEquals("a.pack", infos.get(0).id());
        assertEquals("Alpha", infos.get(0).displayName());
        assertEquals(1, infos.get(0).triggerCount());
        assertEquals("b.pack", infos.get(1).displayName(), "blank name falls back to the id");
    }

    private static ScenePack pack(String id, String name) {
        LayerTemplate layer = new LayerTemplate("l", HapticRole.IMPACT,
                new HapticPrimitive.Impulse(0.5f, 200, 10, 40), Collections.emptySet(),
                DeliveryMode.ALL_COMPATIBLE, CouplingMode.MAX, 0, 0, 250, null);
        SceneTemplate scene = new SceneTemplate(100, 250, null, Collections.singletonList(layer));
        return new ScenePack(ScenePack.SCHEMA_VERSION, id, name, "author", "desc",
                Collections.singletonList(new PackTrigger(GameEventKind.HURT, scene)));
    }
}
