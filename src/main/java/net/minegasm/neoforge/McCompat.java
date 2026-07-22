package net.minegasm.neoforge;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Version-only compatibility shim for vanilla Minecraft client APIs that moved across the
 * {@code 1.21.1}, {@code 26.1.2}, and {@code 26.2} lines: the screen setter and the toast-manager
 * accessor both migrated onto {@code Minecraft.gui} in 26.2, and the toast-manager accessor was
 * {@code Minecraft.getToasts()} on 1.21.1 before becoming {@code Minecraft.getToastManager()} on
 * 26.1.2. This touches only vanilla types, so it lives in shared {@code src} behind a Stonecutter
 * version guard and needs no loader guard — every loader entrypoint calls through here instead of
 * carrying its own copy of the guard, which is what let the loader entrypoints move back into shared
 * source as single files (docs/adr/ADR-013).
 */
public final class McCompat {

    private McCompat() {
    }

    /** Opens {@code screen} on the client, using the line-appropriate setter. */
    public static void setScreen(Minecraft mc, Screen screen) {
        //? if >=26.2 {
        mc.gui.setScreen(screen);
        //?} else {
        /*mc.setScreen(screen);
        *///?}
    }

    /**
     * Adds or updates a system toast, using the line-appropriate toast-manager accessor. The toast-id
     * token type also diverged: it is the instantiable {@code SystemToast.SystemToastId} on 1.21.1 and
     * the 26.x lines, but the {@code SystemToast.SystemToastIds} enum on 1.20.1, so the whole method is
     * duplicated per era rather than only the accessor line.
     */
    //? if >=1.21.1 {
    public static void showToast(Minecraft mc, SystemToast.SystemToastId id, Component title,
                                 Component detail) {
        //? if >=26.2 {
        SystemToast.addOrUpdate(mc.gui.toastManager(), id, title, detail);
        //?} else if >=26.1.2 {
        /*SystemToast.addOrUpdate(mc.getToastManager(), id, title, detail);
        *///?} else {
        /*SystemToast.addOrUpdate(mc.getToasts(), id, title, detail);
        *///?}
    }
    //?} else {
    /*public static void showToast(Minecraft mc, SystemToast.SystemToastIds id, Component title,
                                 Component detail) {
        SystemToast.addOrUpdate(mc.getToasts(), id, title, detail);
    }
    *///?}

    /** The screen currently open (or {@code null}), using the line-appropriate accessor. */
    public static Screen currentScreen(Minecraft mc) {
        //? if >=26.2 {
        return mc.gui.screen();
        //?} else {
        /*return mc.screen;
        *///?}
    }
}
