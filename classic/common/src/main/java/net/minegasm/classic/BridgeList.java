package net.minegasm.classic;

import net.minegasm.client.MinegasmClient;
import net.minegasm.config.HapticConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Shared bridge-endpoint list and edits for the classic bridge screens (one thin view per Minecraft
 * version), matching the modern per-endpoint manager (ADR-018). Every change writes the whole bridge
 * list straight to the config, preserving everything else; the runtime rebuilds a backend per endpoint
 * on restart.
 */
public final class BridgeList {

    private BridgeList() {
    }

    public static List<HapticConfig.Bridge> bridges(MinegasmClient client) {
        return client.config().raw().bridges();
    }

    /**
     * Whether {@code name} is already used by a bridge other than the one at {@code index}. The name is
     * the runtime identity, so the editors reject a duplicate before it can orphan a backend (review
     * P1-7). Case-insensitive so two names that differ only in case can't collide at the command line.
     */
    public static boolean nameTaken(MinegasmClient client, String name, int index) {
        String trimmed = name == null ? "" : name.trim();
        List<HapticConfig.Bridge> list = bridges(client);
        for (int i = 0; i < list.size(); i++) {
            if (i != index && list.get(i).name().equalsIgnoreCase(trimmed)) {
                return true;
            }
        }
        return false;
    }

    /** Persist the given list, preserving every other config section; never leaves the list empty. */
    public static void write(MinegasmClient client, List<HapticConfig.Bridge> bridges) {
        List<HapticConfig.Bridge> out = bridges;
        if (out.isEmpty()) {
            out = new ArrayList<HapticConfig.Bridge>(
                    Collections.singletonList(HapticConfig.Bridge.defaults()));
        }
        HapticConfig cfg = client.config().raw();
        client.updateConfig(new HapticConfig(cfg.schemaVersion(), cfg.profile(), cfg.global(),
                cfg.buttplug(), cfg.events(), cfg.outputPolicy(), cfg.devices(),
                cfg.positionCalibrations(), cfg.accumulation(), cfg.customIntensity(), out));
    }

    public static void toggle(MinegasmClient client, int index) {
        List<HapticConfig.Bridge> list = new ArrayList<HapticConfig.Bridge>(bridges(client));
        if (index < 0 || index >= list.size()) {
            return;
        }
        HapticConfig.Bridge b = list.get(index);
        list.set(index, new HapticConfig.Bridge(b.name(), !b.enabled(), b.url(), b.transport(),
                b.allowRemote(), b.id())); // preserve the id so a toggle doesn't reconnect the endpoint
        write(client, list);
    }

    public static void remove(MinegasmClient client, int index) {
        List<HapticConfig.Bridge> list = new ArrayList<HapticConfig.Bridge>(bridges(client));
        if (index >= 0 && index < list.size()) {
            list.remove(index);
        }
        write(client, list);
    }

    /** Append a new disabled loopback endpoint with a unique default name. */
    public static void add(MinegasmClient client) {
        List<HapticConfig.Bridge> list = new ArrayList<HapticConfig.Bridge>(bridges(client));
        list.add(new HapticConfig.Bridge("bridge-" + (list.size() + 1), false,
                "tcp://127.0.0.1:12347", "tcp", false));
        write(client, list);
    }

    /** Set an existing endpoint by index, or append when the index is past the end. */
    public static void save(MinegasmClient client, int index, String name, boolean enabled, String url,
                            boolean allowRemote) {
        List<HapticConfig.Bridge> list = new ArrayList<HapticConfig.Bridge>(bridges(client));
        if (index >= 0 && index < list.size()) {
            // Editing: keep the existing id so a rename doesn't reconnect the endpoint (review P1-7).
            String id = list.get(index).id();
            list.set(index, new HapticConfig.Bridge(name, enabled, url, "tcp", allowRemote, id));
        } else {
            list.add(new HapticConfig.Bridge(name, enabled, url, "tcp", allowRemote)); // new: fresh id
        }
        write(client, list);
    }
}
