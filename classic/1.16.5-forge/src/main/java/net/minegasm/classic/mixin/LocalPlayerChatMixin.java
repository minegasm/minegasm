package net.minegasm.classic.mixin;

import net.minegasm.classic.ClassicClientHolder;
import net.minegasm.classic.Commands16;
import net.minegasm.client.MinegasmClient;

import net.minecraft.client.player.LocalPlayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Locale;

/**
 * Client-side {@code /minegasm} (and {@code /mg}) commands for Forge 1.16.5. That line has no Forge event
 * for client-only commands, so this Mixin intercepts {@link LocalPlayer#chat(String)} (which sends chat
 * and commands to the server): if the message is a Minegasm command, it is parsed locally through the
 * shared {@link Commands16} and the send to the server is cancelled. Fabric uses its client-command API
 * instead and needs no Mixin.
 *
 * <p>Forge 1.16.5 does not bundle Mixin, so the mod declares a mandatory dependency on MixinBootstrap,
 * which registers the Mixin service that loads this config.
 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerChatMixin {

    @Inject(method = "chat(Ljava/lang/String;)V", at = @At("HEAD"), cancellable = true)
    private void minegasm$interceptClientCommand(String message, CallbackInfo ci) {
        if (message == null) {
            return;
        }
        String trimmed = message.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        boolean minegasm = lower.equals("/minegasm") || lower.startsWith("/minegasm ");
        boolean mg = lower.equals("/mg") || lower.startsWith("/mg ");
        if (!minegasm && !mg) {
            return;
        }
        MinegasmClient client = ClassicClientHolder.get();
        if (client == null) {
            return;
        }
        int sp = trimmed.indexOf(' ');
        String args = sp < 0 ? "" : trimmed.substring(sp + 1);
        Commands16.run(client, 0L, args);
        ci.cancel();
    }
}
