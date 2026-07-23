package net.minegasm.recipe;

import net.minegasm.config.MinegasmMode;
import net.minegasm.core.GameEventKind;

import java.util.EnumMap;
import java.util.Map;

/**
 * The fixed mode preset tables (brief §3.3). Values are the legacy Minegasm per-mode intensities
 * normalised from 0..100 to 0..1 (parity source: {@code AbstractVibrationState.getIntensity}), with
 * modern additions ({@code BLOCK_BROKEN} shares the mine base; {@code EXPLOSION} is an always-on
 * impact gated by config). Snapshot-tested in {@code PresetTest}.
 */
public final class Presets {

    private Presets() {}

    private static final Map<MinegasmMode, Preset> TABLE = build();

    public static Preset forMode(MinegasmMode mode) {
        return TABLE.getOrDefault(mode, TABLE.get(MinegasmMode.NORMAL));
    }

    private static Map<MinegasmMode, Preset> build() {
        Map<MinegasmMode, Preset> t = new EnumMap<>(MinegasmMode.class);

        // NORMAL = legacy default: attack .60, mine .80, place .20, xp 1.0, harvest .10,
        // fishing .50, advancement 1.0; hurt & vitality off.
        t.put(MinegasmMode.NORMAL, preset(MinegasmMode.NORMAL,
                0.60f, 0.00f, 0.80f, 0.20f, 1.00f, 0.10f, 0.50f, 0.00f, 1.00f));

        // MASOCHIST: hurt 1.0, vitality .10 (critical); rewards off.
        t.put(MinegasmMode.MASOCHIST, preset(MinegasmMode.MASOCHIST,
                0.00f, 1.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.10f, 0.00f));

        // HEDONIST: broad: attack .60, hurt .10, mine .80, place .20, xp 1.0, harvest .20,
        // fishing .50, vitality .10, advancement 1.0.
        t.put(MinegasmMode.HEDONIST, preset(MinegasmMode.HEDONIST,
                0.60f, 0.10f, 0.80f, 0.20f, 1.00f, 0.20f, 0.50f, 0.10f, 1.00f));

        // ACCUMULATION: per-event base is unused (charge model drives amplitude); leave a nominal
        // enablement so contributing events are considered "on". Amplitude comes from the charge.
        t.put(MinegasmMode.ACCUMULATION, preset(MinegasmMode.ACCUMULATION,
                0.01f, 0.01f, 0.01f, 0.01f, 0.01f, 0.10f, 0.50f, 0.00f, 0.01f));

        // CUSTOM resolves base from config; table is empty.
        t.put(MinegasmMode.CUSTOM, new Preset(MinegasmMode.CUSTOM, Map.of()));
        return t;
    }

    private static Preset preset(MinegasmMode mode,
                                 float attack, float hurt, float mine, float place, float xp,
                                 float harvest, float fishing, float vitality, float advancement) {
        Map<GameEventKind, Float> m = new EnumMap<>(GameEventKind.class);
        m.put(GameEventKind.ATTACK, attack);
        m.put(GameEventKind.HURT, hurt);
        m.put(GameEventKind.MINING_ACTIVE, mine);
        m.put(GameEventKind.BLOCK_BROKEN, mine);
        m.put(GameEventKind.PLACE, place);
        m.put(GameEventKind.XP_GAIN, xp);
        m.put(GameEventKind.HARVEST, harvest);
        m.put(GameEventKind.FISHING_BITE, fishing);
        m.put(GameEventKind.VITALITY, vitality);
        m.put(GameEventKind.ADVANCEMENT, advancement);
        // Explosion is a modern safety-impact enhancement: always felt strongly, config-gated.
        m.put(GameEventKind.EXPLOSION, 1.00f);
        return new Preset(mode, m);
    }
}
