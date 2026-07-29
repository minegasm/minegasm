package net.minegasm.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/**
 * Stepwise schema migrations operating on the parsed JSON tree, so unknown fields are preserved
 * across upgrades where practical (brief §11.3). Each step upgrades exactly one version.
 */
final class ConfigMigrations {

    private ConfigMigrations() {}

    /**
     * Migrate {@code tree} in place to the current schema version. Returns true if anything changed.
     */
    static boolean migrateInPlace(JsonObject tree) {
        int version = tree.has("schemaVersion") && tree.get("schemaVersion").isJsonPrimitive()
                ? tree.get("schemaVersion").getAsInt()
                : 0;
        boolean changed = false;

        // v0 -> v1: the first real schema. Older/handwritten files may lack schemaVersion.
        if (version < 1) {
            tree.add("schemaVersion", new JsonPrimitive(1));
            version = 1;
            changed = true;
        }

        if (tree.has("schemaVersion") && tree.get("schemaVersion").getAsInt()
                != HapticConfig.CURRENT_SCHEMA_VERSION) {
            tree.add("schemaVersion", new JsonPrimitive(HapticConfig.CURRENT_SCHEMA_VERSION));
            changed = true;
        }
        return changed;
    }
}
