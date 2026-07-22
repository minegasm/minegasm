//? if fabric && >=26.1.2 {
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
 * builds for the 26.1.2 and 26.2 lines (18.0.0 and 20.0.0). Excluded below 26.1.2: 1.21.1's only
 * available ModMenu build (11.0.4) predates mojmap-native publishing, so it needs Loom's mod-jar
 * remapping, which this project's plain {@code compileOnly} wiring for ModMenu (build.gradle.kts)
 * does not perform; the config screen stays reachable there via the keybinding only.
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
