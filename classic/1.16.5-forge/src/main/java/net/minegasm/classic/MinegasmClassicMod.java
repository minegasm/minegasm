package net.minegasm.classic;

import net.minecraftforge.fml.common.Mod;

/**
 * Client-only Minegasm entrypoint for Minecraft 1.16.5 Forge. Unlike the pre-1.13 Classic versions
 * (which use {@code @Mod.EventHandler} lifecycle + mcmod.info), 1.16.5 is the post-flattening Forge:
 * the mod is a plain {@code @Mod}-annotated class constructed by FML, it declares itself in
 * {@code META-INF/mods.toml}, and it registers on the mod/Forge event buses. This class is currently a
 * skeleton that only proves the toolchain; the observation/UI layer follows.
 */
@Mod("minegasm")
public final class MinegasmClassicMod {

    public MinegasmClassicMod() {
    }
}
