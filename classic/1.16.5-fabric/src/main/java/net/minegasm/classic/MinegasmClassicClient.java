package net.minegasm.classic;

import net.minegasm.client.MinegasmClient;
import net.minegasm.time.SystemClock;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v1.ClientCommandManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.loader.api.FabricLoader;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import java.nio.file.Path;

/**
 * Client-only Minegasm entrypoint for Fabric on Minecraft 1.16.5. Owns the shared, loader-independent
 * {@link MinegasmClient} and drives it from the Fabric client tick, using the shared {@link Sampler16},
 * {@link Keybinds16}, and {@link Commands16}. Key bindings register through Fabric's
 * {@code KeyBindingHelper}; the {@code /minegasm} (and {@code /mg}) client commands through Fabric's
 * {@code ClientCommandManager}. The config screen is reached from Mod Menu (see
 * {@code MinegasmModMenu}).
 */
public final class MinegasmClassicClient implements ClientModInitializer {

    private MinegasmClient client;
    private final Keybinds16 keybinds = new Keybinds16();
    private Sampler16 sampler;
    private long gameTick;

    @Override
    public void onInitializeClient() {
        Path configFile = FabricLoader.getInstance().getConfigDir().resolve("minegasm.json");
        this.client = new MinegasmClient(configFile, backend -> ClassicProviderFactory.create(backend),
                SystemClock.INSTANCE);
        this.sampler = new Sampler16(client);
        ClassicClientHolder.set(client);

        KeyBindingHelper.registerKeyBinding(keybinds.panic);
        KeyBindingHelper.registerKeyBinding(keybinds.connect);

        client.start();

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            gameTick++;
            keybinds.poll(client);
            client.onClientTickEnd(sampler.sample(mc, gameTick, System.nanoTime()));
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> client.shutdown());

        ClientCommandManager.DISPATCHER.register(command("minegasm"));
        ClientCommandManager.DISPATCHER.register(command("mg"));
    }

    private LiteralArgumentBuilder<net.fabricmc.fabric.api.client.command.v1.FabricClientCommandSource>
            command(String name) {
        return ClientCommandManager.literal(name)
                .executes(ctx -> {
                    Commands16.run(client, gameTick, "");
                    return 1;
                })
                .then(ClientCommandManager.argument("args", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            Commands16.run(client, gameTick, StringArgumentType.getString(ctx, "args"));
                            return 1;
                        }));
    }
}
