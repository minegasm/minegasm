package net.minegasm.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinegasmClientBackupTest {
    @TempDir
    Path temp;

    @Test
    void legacyImportBackupsNeverOverwriteEarlierCopies() throws Exception {
        Path modern = temp.resolve("minegasm.json");
        Path first = temp.resolve("minegasm.json.before-legacy-import");
        Files.writeString(modern, "current");
        Files.writeString(first, "original backup");

        Path second = MinegasmClient.backupBeforeLegacyImport(modern);
        Files.writeString(modern, "new current");
        Path third = MinegasmClient.backupBeforeLegacyImport(modern);

        assertEquals(first.resolveSibling(first.getFileName() + ".1"), second);
        assertEquals(first.resolveSibling(first.getFileName() + ".2"), third);
        assertEquals("original backup", Files.readString(first));
        assertEquals("current", Files.readString(second));
        assertEquals("new current", Files.readString(third));
    }

    @Test
    void resetBackupsAreTimestampedAndNeverOverwrite() throws Exception {
        Path file = temp.resolve("minegasm.json");
        Files.writeString(file, "current");
        Path first = MinegasmClient.backupConfig(file);
        Files.writeString(file, "changed");
        Path second = MinegasmClient.backupConfig(file);

        assertTrue(first.getFileName().toString().startsWith("minegasm.json.backup-"),
                "backup name carries a timestamp");
        assertNotEquals(first, second, "a second backup in the same second must not overwrite the first");
        assertEquals("current", Files.readString(first));
        assertEquals("changed", Files.readString(second));
    }
}
