package net.minegasm.classic;

import net.minegasm.client.MinegasmClient;
import net.minegasm.config.HapticConfig;
import net.minegasm.pack.ScenePackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared scene-pack list and selection for the classic pack screens (one thin view per Minecraft
 * version). Matches the modern {@code MinegasmScenePackScreen}: the two built-ins first, then every
 * loaded file pack, and a selection is written straight to the config as its raw recipe-pack id
 * (ADR-017), preserving everything else.
 */
public final class ScenePackList {

    private ScenePackList() {
    }

    /** One selectable row: the raw pack id plus the label to show. */
    public static final class Entry {
        public final String id;
        public final String label;

        Entry(String id, String label) {
            this.id = id;
            this.label = label;
        }
    }

    /** Built-ins first, then each loaded file pack in load order, deduped by id. */
    public static List<Entry> options(MinegasmClient client) {
        List<Entry> out = new ArrayList<Entry>();
        out.add(new Entry("classic", "Classic (built-in)"));
        out.add(new Entry("balanced", "Balanced (built-in)"));
        for (ScenePackInfo info : client.scenePacks()) {
            if (!contains(out, info.id())) {
                out.add(new Entry(info.id(), info.displayName()));
            }
        }
        return out;
    }

    /** The currently selected recipe-pack id (a built-in name or a file pack id). */
    public static String selected(MinegasmClient client) {
        return client.config().raw().profile().recipePack();
    }

    /** Persist the chosen id as the recipe pack, preserving everything else the config holds. */
    public static void select(MinegasmClient client, String id) {
        HapticConfig cfg = client.config().raw();
        HapticConfig.Profile p = cfg.profile();
        client.updateConfig(new HapticConfig(cfg.schemaVersion(),
                new HapticConfig.Profile(id, p.hapticMode()),
                cfg.global(), cfg.buttplug(), cfg.events(), cfg.outputPolicy(), cfg.devices(),
                cfg.positionCalibrations(), cfg.accumulation(), cfg.customIntensity(), cfg.bridges()));
    }

    private static boolean contains(List<Entry> list, String id) {
        for (Entry e : list) {
            if (e.id.equals(id)) {
                return true;
            }
        }
        return false;
    }
}
