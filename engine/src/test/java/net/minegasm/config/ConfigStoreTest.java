package net.minegasm.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigStoreTest {

    @Test
    void missingFileReturnsDefaults(@TempDir Path dir) {
        ConfigStore store = new ConfigStore(dir.resolve("config.json"));
        ConfigStore.LoadResult result = store.load();
        assertFalse(result.wasPresent());
        assertEquals(HapticConfig.CURRENT_SCHEMA_VERSION, result.config().schemaVersion());
        assertFalse(result.config().global().enabled());
        assertTrue(result.config().buttplug().autoConnect());
        assertTrue(result.config().buttplug().autoScan());
    }

    @Test
    void saveThenLoadRoundTrips(@TempDir Path dir) {
        ConfigStore store = new ConfigStore(dir.resolve("config.json"));
        HapticConfig cfg = HapticConfig.defaults();
        var enabled = new HapticConfig.Global(true, 0.5, 0.25, true, "STOP", true, "KEY_P",
                40, 1_500, 80, 30_000);
        HapticConfig toSave = new HapticConfig(1, new HapticConfig.Identity("classic", "MASOCHIST"),
                enabled, cfg.buttplug(), cfg.events(), cfg.outputPolicy(), cfg.devices(),
                cfg.positionCalibrations(), cfg.accumulation(), cfg.customIntensity());
        store.save(toSave);

        ConfigStore.LoadResult result = store.load();
        assertTrue(result.wasPresent());
        assertFalse(result.recoveredFromCorruption());
        assertTrue(result.config().global().enabled());
        assertEquals(0.5, result.config().global().intensity(), 1e-9);
        assertEquals(MinegasmMode.MASOCHIST, result.config().identity().mode());
        assertEquals(RecipePackId.CLASSIC, result.config().identity().recipePackId());
        assertEquals(40, result.config().global().testMaxPercent());
        assertEquals(1_500, result.config().global().testMaxDurationMs());
        assertEquals(80, result.config().global().unsafeTestMaxPercent());
        assertEquals(30_000, result.config().global().unsafeTestMaxDurationMs());
        assertEquals(toSave, result.config());
    }

    @Test
    void defaultsRoundTripPreservesEveryNestedField(@TempDir Path dir) {
        // Exercises the record deserialization path end-to-end over the full nested graph (nested
        // records plus the populated events/outputPolicy Maps of records). This is the assertion that
        // guards 1.19.2, where Gson 2.8.9 cannot construct records: a factory bug fails deep equality
        // here instead of silently tripping load()'s corrupt-recovery path and resetting the config.
        ConfigStore store = new ConfigStore(dir.resolve("config.json"));
        HapticConfig original = HapticConfig.defaults();
        store.save(original);

        ConfigStore.LoadResult result = store.load();
        assertTrue(result.wasPresent());
        assertFalse(result.recoveredFromCorruption());
        assertFalse(result.migrated());
        assertEquals(original, result.config());
    }

    @Test
    void corruptFileRecoversToDefaultsAndBacksUp(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("config.json");
        Files.writeString(file, "{ this is not valid json ", StandardCharsets.UTF_8);
        ConfigStore store = new ConfigStore(file);
        ConfigStore.LoadResult result = store.load();
        assertTrue(result.recoveredFromCorruption());
        assertEquals(HapticConfig.CURRENT_SCHEMA_VERSION, result.config().schemaVersion());
        assertTrue(Files.exists(dir.resolve("config.json.corrupt")), "corrupt file should be backed up");
    }

    @Test
    void olderFileWithoutSchemaVersionMigrates(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("config.json");
        Files.writeString(file, "{\"global\":{\"enabled\":true}}", StandardCharsets.UTF_8);
        ConfigStore store = new ConfigStore(file);
        ConfigStore.LoadResult result = store.load();
        assertTrue(result.migrated());
        assertEquals(HapticConfig.CURRENT_SCHEMA_VERSION, result.config().schemaVersion());
        assertTrue(result.config().global().enabled());
        assertEquals(PauseBehavior.PAUSE, result.config().global().pauseBehaviorMode());
    }

    @Test
    void atomicSaveLeavesNoTempFiles(@TempDir Path dir) throws IOException {
        ConfigStore store = new ConfigStore(dir.resolve("config.json"));
        store.save(HapticConfig.defaults());
        long tempCount;
        try (var stream = Files.list(dir)) {
            tempCount = stream.filter(p -> p.getFileName().toString().contains(".tmp")).count();
        }
        assertEquals(0, tempCount);
    }

    @Test
    void testLimitsClampToCentralAbsolutePolicy() {
        var global = new HapticConfig.Global(true, 1, 0, false, "STOP", true, "",
                500, Integer.MAX_VALUE, 500, Integer.MAX_VALUE);

        assertEquals(TestOutputLimits.MAX_PERCENT, global.testMaxPercent());
        assertEquals(TestOutputLimits.MAX_DURATION_MS, global.testMaxDurationMs());
        assertEquals(TestOutputLimits.MAX_PERCENT, global.unsafeTestMaxPercent());
        assertEquals(TestOutputLimits.MAX_DURATION_MS, global.unsafeTestMaxDurationMs());
    }
}
