package gg.meza.feelcraft.neoforge;

import gg.meza.feelcraft.runtime.HapticRuntime;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/** NeoForge-only MVP event adapter. Keep Minecraft API usage out of haptic core. */
public final class NeoForgeClientEvents {
    private final HapticRuntime runtime;
    private final MinecraftSampler sampler;

    public NeoForgeClientEvents(HapticRuntime runtime) {
        this.runtime = runtime;
        this.sampler = new MinecraftSampler(runtime);
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent.Post event) {
        sampler.sampleEndTick(Minecraft.getInstance());
        runtime.endClientTick();
    }

    @SubscribeEvent
    public void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        runtime.stopAll("client_logout");
    }
}
