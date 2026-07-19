package net.minegasm.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Loads and saves {@link HapticConfig} as JSON with the safety requirements of brief §11.3:
 * atomic write via temp-file + rename, corrupt-file backup with safe defaults, and schema-version
 * migration. No secrets are required or stored.
 */
public final class ConfigStore {

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private final Path file;

    public ConfigStore(Path file) {
        this.file = file;
    }

    public Path file() {
        return file;
    }

    /** Result of a load, distinguishing a fresh default from a real load and reporting recovery. */
    public record LoadResult(HapticConfig config, boolean wasPresent, boolean recoveredFromCorruption,
                             boolean migrated) {}

    /**
     * Load the config, applying migration and corruption recovery. Never throws for a bad file: a
     * corrupt file is backed up and safe defaults are returned so haptics fail toward "stopped".
     */
    public LoadResult load() {
        if (!Files.exists(file)) {
            return new LoadResult(HapticConfig.defaults(), false, false, false);
        }
        String text;
        try {
            text = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("failed reading config " + file, e);
        }
        try {
            JsonObject tree = GSON.fromJson(text, JsonObject.class);
            if (tree == null) {
                throw new JsonParseException("empty config");
            }
            boolean migrated = ConfigMigrations.migrateInPlace(tree);
            HapticConfig cfg = GSON.fromJson(tree, HapticConfig.class);
            return new LoadResult(cfg, true, false, migrated);
        } catch (RuntimeException parseError) {
            backupCorrupt();
            return new LoadResult(HapticConfig.defaults(), true, true, false);
        }
    }

    /** Atomically persist the config (temp file + move). */
    public void save(HapticConfig config) {
        String json = GSON.toJson(config);
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path tmp = Files.createTempFile(parent, "minegasm-config", ".tmp");
            Files.writeString(tmp, json, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicUnsupported) {
                // Fall back to a non-atomic replace on filesystems without atomic move.
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed writing config " + file, e);
        }
    }

    public String toJson(HapticConfig config) {
        return GSON.toJson(config);
    }

    private void backupCorrupt() {
        try {
            Path backup = file.resolveSibling(file.getFileName() + ".corrupt");
            Files.move(file, backup, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
            // Best effort: if we cannot back up, defaults are still returned by load().
        }
    }
}
