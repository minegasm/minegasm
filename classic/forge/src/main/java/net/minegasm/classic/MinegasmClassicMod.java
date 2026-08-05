package net.minegasm.classic;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

import net.minegasm.client.MinegasmClient;
import net.minegasm.time.SystemClock;

import java.nio.file.Path;

/**
 * Client-only Minegasm entrypoint for the 1.8.9-and-later Forge line (this one built for 1.12.2). Uses
 * {@code net.minecraftforge.fml} and the {@code @Mod.EventHandler} lifecycle. It owns the shared,
 * loader-independent {@link MinegasmClient} exactly as the modern loaders do, and hands all of the
 * Minecraft-facing work (client tick sampling, keybindings, commands, chat) to a per-version
 * {@link ClassicClientHandler}. That handler has the same fully-qualified name in both the 1.8.9 and
 * 1.12.2 source trees but a different body, because the client API diverges sharply between them
 * (player/world field names, hit-result and block-position packages, text-component types). The
 * Buttplug backend comes from {@link ClassicProviderFactory}: buttplug4j by default, or the bundled
 * Java-WebSocket native transport when configured (ADR-019).
 *
 * <p>1.7.10 gets a sibling entrypoint under {@code cpw.mods.fml}; 1.8.9 shares this
 * {@code net.minecraftforge.fml} shape. The version-agnostic command parsing lives in
 * {@link ClassicCommands} under {@code classic/common}.
 */
@Mod(modid = "minegasm", name = "Minegasm", version = MinegasmClassicMod.VERSION,
        clientSideOnly = true, acceptableRemoteVersions = "*",
        guiFactory = "net.minegasm.classic.ClassicGuiFactory")
public final class MinegasmClassicMod {

    static final String VERSION = "1.0.0-beta.2";

    private MinegasmClient client;
    private ClassicClientHandler handler;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        Path configFile = event.getModConfigurationDirectory().toPath().resolve("minegasm.json");
        this.client = new MinegasmClient(configFile, backend -> ClassicProviderFactory.create(backend),
                SystemClock.INSTANCE);
        ClassicClientHolder.set(client);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        this.handler = new ClassicClientHandler(client);
        this.handler.register();
        client.start();
    }

    public MinegasmClient client() {
        return client;
    }
}
