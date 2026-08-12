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
     * Migrate {@code tree} in place, upgrading older schemas to the current version. Returns true if
     * anything changed. A version newer than {@link HapticConfig#CURRENT_SCHEMA_VERSION} is left
     * untouched: only an upgrade path exists, never a downgrade, so an older build can never silently
     * rewrite a file a newer build wrote. {@link ConfigStore#load()} rejects future versions before
     * calling this, so this is a defensive guard.
     */
    static boolean migrateInPlace(JsonObject tree) {
        int version = tree.has("schemaVersion") && tree.get("schemaVersion").isJsonPrimitive()
                ? tree.get("schemaVersion").getAsInt()
                : 0;
        if (version >= HapticConfig.CURRENT_SCHEMA_VERSION) {
            return false; // current or newer: nothing to upgrade, and never downgrade a future version
        }
        boolean changed = false;

        // v0 -> v1: the first real schema. Older/handwritten files may lack schemaVersion.
        if (version < 1) {
            tree.add("schemaVersion", new JsonPrimitive(1));
            version = 1;
            changed = true;
        }

        // Future step migrations go here, one version bump each, before this final normalization.
        if (version != HapticConfig.CURRENT_SCHEMA_VERSION) {
            tree.add("schemaVersion", new JsonPrimitive(HapticConfig.CURRENT_SCHEMA_VERSION));
            changed = true;
        }
        return changed;
    }
}
