package net.minegasm.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.UUID;

/**
 * Stepwise schema migrations operating on the parsed JSON tree, so unknown fields are preserved
 * across upgrades where practical (brief §11.3). Each step upgrades exactly one version.
 */
final class ConfigMigrations {

    private ConfigMigrations() {}

    /**
     * Migrate {@code tree} in place, upgrading older schemas to the current version and applying additive
     * normalizations. Returns true if anything changed. A version newer than
     * {@link HapticConfig#CURRENT_SCHEMA_VERSION} is left untouched: only an upgrade path exists, never a
     * downgrade, so an older build can never silently rewrite a file a newer build wrote.
     * {@link ConfigStore#load()} rejects future versions before calling this, so that check is a defensive
     * guard.
     */
    static boolean migrateInPlace(JsonObject tree) {
        int version = tree.has("schemaVersion") && tree.get("schemaVersion").isJsonPrimitive()
                ? tree.get("schemaVersion").getAsInt()
                : 0;
        if (version > HapticConfig.CURRENT_SCHEMA_VERSION) {
            return false; // a future version: never downgrade it
        }
        boolean changed = false;

        // v0 -> v1: the first real schema. Older/handwritten files may lack schemaVersion.
        if (version < 1) {
            tree.add("schemaVersion", new JsonPrimitive(1));
            version = 1;
            changed = true;
        }

        // Additive normalization applied at the current version too (no schema bump; still beta): bridges
        // carry an immutable id separate from the display name, so give any bridge that lacks one a fresh
        // id. Persisted on the next save so it is not regenerated on later loads.
        changed |= assignBridgeIds(tree);

        if (version != HapticConfig.CURRENT_SCHEMA_VERSION) {
            tree.add("schemaVersion", new JsonPrimitive(HapticConfig.CURRENT_SCHEMA_VERSION));
            changed = true;
        }
        return changed;
    }

    /** Give every bridge without an {@code id} a fresh one, in place. Returns true if anything changed. */
    private static boolean assignBridgeIds(JsonObject tree) {
        if (!tree.has("bridges") || !tree.get("bridges").isJsonArray()) {
            return false;
        }
        boolean changed = false;
        JsonArray bridges = tree.getAsJsonArray("bridges");
        for (JsonElement element : bridges) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject bridge = element.getAsJsonObject();
            if (!bridge.has("id") || !bridge.get("id").isJsonPrimitive()
                    || bridge.get("id").getAsString().trim().isEmpty()) {
                bridge.add("id", new JsonPrimitive(UUID.randomUUID().toString().substring(0, 8)));
                changed = true;
            }
        }
        return changed;
    }
}
