package net.minegasm.classic;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * Mod Menu integration for Fabric: supplies the config-screen button in Mod Menu's mod list. This is a
 * "modmenu" entrypoint (see fabric.mod.json), loaded only when Mod Menu is installed, so Mod Menu is an
 * optional dependency. The screen itself is the shared {@link DashboardScreen16}.
 */
public final class MinegasmModMenu implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return HubScreen16::new;
    }
}
