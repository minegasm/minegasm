package net.minegasm.classic;

import net.minegasm.client.MinegasmClient;

import net.minecraft.client.KeyMapping;

import com.mojang.blaze3d.platform.InputConstants;

import org.lwjgl.glfw.GLFW;

/**
 * The panic and quick-connect key bindings for 1.16.5. The {@link KeyMapping} objects are created here
 * (vanilla, both loaders); each loader registers them its own way (Forge {@code ClientRegistry}, Fabric
 * {@code KeyBindingHelper}) and calls {@link #poll(MinegasmClient)} once per client tick.
 */
public final class Keybinds16 {

    public static final String CATEGORY = "key.categories.minegasm";

    public final KeyMapping panic = new KeyMapping("key.minegasm.panic",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CATEGORY);
    public final KeyMapping connect = new KeyMapping("key.minegasm.connect",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CATEGORY);

    /** Consume any queued presses and act. Panic works in-world or in menus (brief safety). */
    public void poll(MinegasmClient client) {
        while (panic.consumeClick()) {
            client.panic();
            Chat16.send("Haptic output stopped (panic).");
        }
        while (connect.consumeClick()) {
            if (!client.isConnected()) {
                Chat16.send("Connecting...");
                client.connect();
            }
        }
    }
}
