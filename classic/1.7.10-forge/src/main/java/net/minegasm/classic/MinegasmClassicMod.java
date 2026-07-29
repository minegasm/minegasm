package net.minegasm.classic;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

import net.minegasm.buttplug.b4j.Buttplug4jProvider;
import net.minegasm.client.MinegasmClient;
import net.minegasm.time.SystemClock;

import java.nio.file.Path;

/**
 * Client-only Minegasm entrypoint for Minecraft 1.7.10 Forge. This line predates the 1.8 package move,
 * so it uses {@code cpw.mods.fml} rather than {@code net.minecraftforge.fml} (the shape 1.8.9 and
 * 1.12.2 share). It owns the same shared, loader-independent {@link MinegasmClient} the other builds
 * do, connecting through buttplug4j. 1.7.10's {@code @Mod} has no {@code clientSideOnly} flag, so
 * {@code acceptableRemoteVersions = "*"} keeps this client-only mod from forcing a server-side install.
 */
@Mod(modid = "minegasm", name = "Minegasm", version = MinegasmClassicMod.VERSION,
        acceptableRemoteVersions = "*", guiFactory = "net.minegasm.classic.ClassicGuiFactory")
public final class MinegasmClassicMod {

    static final String VERSION = "1.0.0-beta.2";

    private MinegasmClient client;
    private ClassicClientHandler handler;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        Path configFile = event.getModConfigurationDirectory().toPath().resolve("minegasm.json");
        this.client = new MinegasmClient(configFile, new Buttplug4jProvider("Minegasm"),
                SystemClock.INSTANCE);
        ClassicClientHolder.set(client);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        // 1.7.10's @Mod has no clientSideOnly flag, so Forge still loads this mod on a dedicated server.
        // ClassicClientHandler's fields construct KeyBinding and other @SideOnly(CLIENT) types, so touch
        // it only on the client; on a server this stays inert rather than crashing mod init.
        if (!FMLCommonHandler.instance().getSide().isClient()) {
            return;
        }
        this.handler = new ClassicClientHandler(client);
        this.handler.register();
        client.start();
    }

    public MinegasmClient client() {
        return client;
    }
}
