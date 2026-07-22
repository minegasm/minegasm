//? if fabric {
/*package net.minegasm.fabric;

import net.minegasm.client.MinegasmClient;
import net.minegasm.neoforge.MinegasmConfigScreen;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/^*
 * Optional ModMenu integration for the Fabric loader: gives {@link MinegasmConfigScreen} a config
 * entry in ModMenu's mods list. Wired via the {@code modmenu} entrypoint in {@code fabric.mod.json},
 * which Fabric invokes only when ModMenu is installed — so ModMenu is a compile-only dependency
 * (see {@code build.gradle.kts}) and the mod runs identically without it (open the screen with the
 * {@code key.minegasm.config} keybinding instead).
 *
 * <p>Lives in the shared root {@code src} behind a whole-file {@code //? if fabric} Stonecutter guard
 * like {@link MinegasmMod}: no {@code com.terraformersmc.*} import ever reaches a NeoForge or Forge
 * compile. ModMenu instantiates this class on its own — separately from the
 * {@link net.fabricmc.api.ClientModInitializer} — so the live {@link MinegasmClient} is reached
 * through {@link MinegasmMod#activeClient()} rather than an instance field. The
 * {@code ModMenuApi}/{@code ConfigScreenFactory} surface used here is identical across the ModMenu
 * builds for both supported Minecraft lines (18.0.0 and 20.0.0), so no version guard is needed.
 ^/
public final class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> {
            MinegasmClient client = MinegasmMod.activeClient();
            return client == null ? null : new MinegasmConfigScreen(parent, client);
        };
    }
}
*///?}
