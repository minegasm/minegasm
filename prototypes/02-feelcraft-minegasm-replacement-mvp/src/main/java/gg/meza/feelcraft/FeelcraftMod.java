package gg.meza.feelcraft;

import gg.meza.feelcraft.neoforge.NeoForgeClientEvents;
import gg.meza.feelcraft.runtime.HapticRuntime;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

@Mod(FeelcraftMod.MOD_ID)
public final class FeelcraftMod {
    public static final String MOD_ID = "feelcraft";

    public FeelcraftMod(IEventBus modEventBus, ModContainer modContainer) {
        HapticRuntime.bootstrap();
        NeoForge.EVENT_BUS.register(new NeoForgeClientEvents(HapticRuntime.instance()));
    }
}
