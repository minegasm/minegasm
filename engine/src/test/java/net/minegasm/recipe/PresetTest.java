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
    void normalModeMatchesLegacy() {
        Preset p = Presets.forMode(MinegasmMode.NORMAL);
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
    void masochistFocusesOnDamage() {
        Preset p = Presets.forMode(MinegasmMode.MASOCHIST);
        assertEquals(1.00f, p.baseFor(GameEventKind.HURT, cfg));
        assertEquals(0.10f, p.baseFor(GameEventKind.VITALITY, cfg));
        assertEquals(0.00f, p.baseFor(GameEventKind.ATTACK, cfg));
        assertEquals(0.00f, p.baseFor(GameEventKind.MINING_ACTIVE, cfg));
    }

    @Test
    void hedonistIsBroad() {
        Preset p = Presets.forMode(MinegasmMode.HEDONIST);
        assertEquals(0.60f, p.baseFor(GameEventKind.ATTACK, cfg));
        assertEquals(0.10f, p.baseFor(GameEventKind.HURT, cfg));
        assertEquals(0.20f, p.baseFor(GameEventKind.HARVEST, cfg));
        assertEquals(0.10f, p.baseFor(GameEventKind.VITALITY, cfg));
    }
}
