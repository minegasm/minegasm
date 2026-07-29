package net.minegasm.classic;

import net.fabricmc.api.ClientModInitializer;

/**
 * Client-only Minegasm entrypoint for Fabric on Minecraft 1.16.5. Declared in {@code fabric.mod.json}
 * as the {@code client} entrypoint; Fabric constructs it and calls {@link #onInitializeClient()}. This
 * is currently a skeleton that only proves the toolchain; the observation/UI layer follows and will
 * share the vanilla-facing sampler with the Forge subproject.
 */
public final class MinegasmClassicClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
    }
}
