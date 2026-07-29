package net.minegasm.classic;

import net.minegasm.buttplug.b4j.Buttplug4jProvider;
import net.minegasm.client.MinegasmClient;
import net.minegasm.time.SystemClock;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Path;
import java.util.function.BiFunction;

/**
 * Client-only Minegasm entrypoint for Forge on Minecraft 1.16.5. Post-flattening Forge: a plain
 * {@code @Mod} class constructed by FML, declared in {@code META-INF/mods.toml}, wiring onto the mod and
 * Forge event buses. It owns the shared {@link MinegasmClient} and drives it from the client tick using
 * the shared {@link Sampler16} and {@link Keybinds16}. The {@code /minegasm} client commands are added
 * by a Mixin (client commands have no Forge event on 1.16.5); the config screen is reached from the mods
 * list.
 *
 * <p>The mod is client-only, but the class can still load on a dedicated server, so the constructor
 * bails out there before touching any client-only type.
 */
@Mod("minegasm")
public final class MinegasmClassicMod {

    private MinegasmClient client;
    private Keybinds16 keybinds;
    private Sampler16 sampler;
    private long gameTick;

    public MinegasmClassicMod() {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return; // client-only mod; do nothing on a dedicated server
        }
        Path configFile = FMLPaths.CONFIGDIR.get().resolve("minegasm.json");
        this.client = new MinegasmClient(configFile, new Buttplug4jProvider("Minegasm"),
                SystemClock.INSTANCE);
        this.keybinds = new Keybinds16();
        this.sampler = new Sampler16(client);
        ClassicClientHolder.set(client);

        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onClientSetup);
        MinecraftForge.EVENT_BUS.register(this);
        Runtime.getRuntime().addShutdownHook(new Thread(client::shutdown, "minegasm-shutdown"));

        // Mods-list "Config" button -> the shared settings screen.
        ModLoadingContext.get().registerExtensionPoint(ExtensionPoint.CONFIGGUIFACTORY,
                () -> (BiFunction<Minecraft, Screen, Screen>) (mc, parent) -> new DashboardScreen16(parent));
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        ClientRegistry.registerKeyBinding(keybinds.panic);
        ClientRegistry.registerKeyBinding(keybinds.connect);
        event.enqueueWork(client::start);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || client == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        gameTick++;
        keybinds.poll(client);
        client.onClientTickEnd(sampler.sample(mc, gameTick, System.nanoTime()));
    }

    /** Exposed for the client-command Mixin and the config screen. */
    public MinegasmClient client() {
        return client;
    }

    public long gameTick() {
        return gameTick;
    }
}
