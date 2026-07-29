package net.minegasm.classic;

import net.minegasm.client.MinegasmClient;

/**
 * A static handle to the live {@link MinegasmClient} for the config screen. Legacy Forge instantiates a
 * mod's config {@code GuiScreen} reflectively through its {@code mainConfigGuiClass()} with only a parent
 * screen, so there is no constructor seam to pass the client in. Each entrypoint publishes its client
 * here at init, and the screen reads it back. Minecraft-free, so it is shared across all three versions.
 */
public final class ClassicClientHolder {

    private static volatile MinegasmClient client;

    private ClassicClientHolder() {
    }

    public static void set(MinegasmClient value) {
        client = value;
    }

    public static MinegasmClient get() {
        return client;
    }
}
