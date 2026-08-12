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
            // The config is a graph of immutable value types. This factory builds each one through its
            // all-args constructor (running its validation and defaults) rather than by setting fields,
            // which also sidesteps Gson 2.8.9 on Minecraft 1.19.2 failing to set final fields.
            .registerTypeAdapterFactory(ConfigValueTypeAdapterFactory.INSTANCE)
            .create();

    private final Path file;

    public ConfigStore(Path file) {
        this.file = file;
    }

    public Path file() {
        return file;
    }

    /** Result of a load, distinguishing a fresh default from a real load and reporting recovery. */
    public static final class LoadResult {
        private final HapticConfig config;
        private final boolean wasPresent;
        private final boolean recoveredFromCorruption;
        private final boolean migrated;
        private final boolean fromNewerSchema;

        public LoadResult(HapticConfig config, boolean wasPresent, boolean recoveredFromCorruption,
                          boolean migrated) {
            this(config, wasPresent, recoveredFromCorruption, migrated, false);
        }

        public LoadResult(HapticConfig config, boolean wasPresent, boolean recoveredFromCorruption,
                          boolean migrated, boolean fromNewerSchema) {
            this.config = config;
            this.wasPresent = wasPresent;
            this.recoveredFromCorruption = recoveredFromCorruption;
            this.migrated = migrated;
            this.fromNewerSchema = fromNewerSchema;
        }

        public HapticConfig config() {
            return config;
        }

        public boolean wasPresent() {
            return wasPresent;
        }

        public boolean recoveredFromCorruption() {
            return recoveredFromCorruption;
        }

        public boolean migrated() {
            return migrated;
        }

        /** The file on disk was written by a newer schema; it was left untouched and defaults are used. */
        public boolean fromNewerSchema() {
            return fromNewerSchema;
        }
    }

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
            text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("failed reading config " + file, e);
        }
        try {
            JsonObject tree = GSON.fromJson(text, JsonObject.class);
            if (tree == null) {
                throw new JsonParseException("empty config");
            }
            int version = tree.has("schemaVersion") && tree.get("schemaVersion").isJsonPrimitive()
                    ? tree.get("schemaVersion").getAsInt()
                    : 0;
            if (version > HapticConfig.CURRENT_SCHEMA_VERSION) {
                // A newer Minegasm wrote this file. Don't parse-and-downgrade it: an older build would
                // drop fields it doesn't understand and later save a lossy file labelled as current. Keep
                // the original byte-for-byte, back it up, and run on safe defaults instead.
                backupNewer();
                return new LoadResult(HapticConfig.defaults(), true, false, false, true);
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
            Files.write(tmp, json.getBytes(StandardCharsets.UTF_8));
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

    /**
     * Copy a newer-schema file aside without touching the original, so a later save can't lose it. Picks
     * a name that does not already exist ({@code .newer}, then {@code .newer.1}, {@code .newer.2}, ...) so
     * a backup from an earlier launch is never overwritten.
     */
    private void backupNewer() {
        try {
            Path backup = file.resolveSibling(file.getFileName() + ".newer");
            for (int n = 1; Files.exists(backup) && n < 1000; n++) {
                backup = file.resolveSibling(file.getFileName() + ".newer." + n);
            }
            if (!Files.exists(backup)) {
                Files.copy(file, backup);
            }
        } catch (IOException ignored) {
            // Best effort: the original file is left in place regardless.
        }
    }
}
