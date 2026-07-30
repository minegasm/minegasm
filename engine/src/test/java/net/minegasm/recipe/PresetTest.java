package net.minegasm.recipe;

import net.minegasm.config.MinegasmMode;
import net.minegasm.config.RuntimeConfig;
import net.minegasm.core.GameEventKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Snapshot of the legacy Minegasm per-mode intensity table (brief §3.3, parity source
 * {@code AbstractVibrationState.getIntensity}). These values are the compatibility contract.
 */
class PresetTest {

    private final RuntimeConfig cfg = RuntimeConfig.defaults();

    @Test
    void actionModeMatchesLegacy() {
        Preset p = Presets.forMode(MinegasmMode.ACTION);
        assertEquals(0.60f, p.baseFor(GameEventKind.ATTACK, cfg));
        assertEquals(0.00f, p.baseFor(GameEventKind.HURT, cfg));
        assertEquals(0.80f, p.baseFor(GameEventKind.MINING_ACTIVE, cfg));
        assertEquals(0.80f, p.baseFor(GameEventKind.BLOCK_BROKEN, cfg));
        assertEquals(0.20f, p.baseFor(GameEventKind.PLACE, cfg));
        assertEquals(1.00f, p.baseFor(GameEventKind.XP_GAIN, cfg));
        assertEquals(0.10f, p.baseFor(GameEventKind.HARVEST, cfg));
        assertEquals(0.50f, p.baseFor(GameEventKind.FISHING_BITE, cfg));
        assertEquals(0.00f, p.baseFor(GameEventKind.VITALITY, cfg));
        assertEquals(1.00f, p.baseFor(GameEventKind.ADVANCEMENT, cfg));
    }

    @Test
    void reactionFocusesOnDamage() {
        Preset p = Presets.forMode(MinegasmMode.REACTION);
        assertEquals(1.00f, p.baseFor(GameEventKind.HURT, cfg));
        assertEquals(0.10f, p.baseFor(GameEventKind.VITALITY, cfg));
        assertEquals(0.00f, p.baseFor(GameEventKind.ATTACK, cfg));
        assertEquals(0.00f, p.baseFor(GameEventKind.MINING_ACTIVE, cfg));
    }

    @Test
    void immersionIsBroad() {
        Preset p = Presets.forMode(MinegasmMode.IMMERSION);
        assertEquals(0.60f, p.baseFor(GameEventKind.ATTACK, cfg));
        assertEquals(0.10f, p.baseFor(GameEventKind.HURT, cfg));
        assertEquals(0.20f, p.baseFor(GameEventKind.HARVEST, cfg));
        assertEquals(0.10f, p.baseFor(GameEventKind.VITALITY, cfg));
    }
}
