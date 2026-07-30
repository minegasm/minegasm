package net.minegasm.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyMinegasmImporterTest {

    private static final String TOML = """
            [buttplug]
            serverUrl = "ws://localhost:12345/buttplug"

            [minegasm]
            vibrate = true
            mode = "HEDONIST"

            [minegasm.intensity]
            attackIntensity = 0.6   # fractional form
            hurtIntensity = 1.0
            mineIntensity = 0.8
            """;

    @Test
    void mapsConnectionModeAndIntensities() {
        var preview = LegacyMinegasmImporter.fromToml(TOML, HapticConfig.defaults());
        HapticConfig result = preview.result();
        assertEquals("ws://localhost:12345/buttplug", result.buttplug().serverUrl());
        assertTrue(result.global().enabled());
        assertEquals(MinegasmMode.IMMERSION, result.profile().mode()); // legacy HEDONIST migrates
        // Imported configs replay through the Classic pack for faithful parity.
        assertEquals(RecipePackId.CLASSIC, result.profile().recipePackId());
        assertEquals(0.6, result.customIntensity().attack(), 1e-6);
        assertEquals(1.0, result.customIntensity().hurt(), 1e-6);
        assertEquals(0.8, result.customIntensity().mine(), 1e-6);
    }

    @Test
    void normalisesLegacyHundredScale() {
        String legacy = """
                [minegasm]
                mode = "NORMAL"
                [minegasm.intensity]
                attackIntensity = 60
                xpChangeIntensity = 100
                """;
        var preview = LegacyMinegasmImporter.fromToml(legacy, HapticConfig.defaults());
        assertEquals(0.60, preview.result().customIntensity().attack(), 1e-6);
        assertEquals(1.00, preview.result().customIntensity().xpChange(), 1e-6);
    }

    @Test
    void previewSummaryIsHumanReadable() {
        var preview = LegacyMinegasmImporter.fromToml(TOML, HapticConfig.defaults());
        assertEquals("IMMERSION", preview.summary().get("mode"));
        assertTrue(preview.summary().containsKey("serverUrl"));
    }

    @Test
    void keepsDefaultOutputPolicy() {
        var preview = LegacyMinegasmImporter.fromToml(TOML, HapticConfig.defaults());
        // Import maps gameplay tuning only; output enablement is left at the defaults.
        assertEquals(HapticConfig.defaults().outputPolicy(), preview.result().outputPolicy());
    }
}
