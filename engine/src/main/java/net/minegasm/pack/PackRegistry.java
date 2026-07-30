package net.minegasm.pack;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The loaded scene packs, keyed by pack id (brief 0003 §2.5). Populated once at startup from the pack
 * folder, then read by the recipe engine when the user's selected pack name matches a file pack.
 *
 * <p>Not synchronized: registration happens before the worker starts consuming it. A later hot-reload
 * feature would add its own coordination.
 */
public final class PackRegistry {

    private final Map<String, ScenePack> byId = new LinkedHashMap<>();

    /** Register a pack, replacing any earlier pack with the same id. */
    public void register(ScenePack pack) {
        byId.put(pack.packId(), pack);
    }

    /** The pack with this id, if one is loaded. A null id yields empty. */
    public Optional<ScenePack> find(String id) {
        return id == null ? Optional.<ScenePack>empty() : Optional.ofNullable(byId.get(id));
    }

    public boolean isEmpty() {
        return byId.isEmpty();
    }

    public int size() {
        return byId.size();
    }
}
