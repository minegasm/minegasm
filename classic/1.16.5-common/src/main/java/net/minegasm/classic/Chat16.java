package net.minegasm.classic;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.TextComponent;

/**
 * Minegasm chat feedback for 1.16.5, shared by both loaders. Prints on the client thread (via
 * {@code Minecraft.execute}) so provider-thread callbacks (async connect results) are safe.
 */
public final class Chat16 {

    static final String PREFIX = "§d[Minegasm]§r ";

    private Chat16() {
    }

    public static void send(final String message) {
        final Minecraft mc = Minecraft.getInstance();
        mc.execute(new Runnable() {
            @Override
            public void run() {
                if (mc.player != null) {
                    mc.player.displayClientMessage(new TextComponent(PREFIX + message), false);
                }
            }
        });
    }
}
