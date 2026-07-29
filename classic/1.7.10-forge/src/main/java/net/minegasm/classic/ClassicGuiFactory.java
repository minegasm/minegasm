package net.minegasm.classic;

import cpw.mods.fml.client.IModGuiFactory;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

import java.util.Collections;
import java.util.Set;

/**
 * Wires the Minecraft 1.7.10 "Config" button in the mods list to {@link ClassicConfigScreen}. Uses the
 * {@code cpw.mods.fml.client.IModGuiFactory} of this pre-1.8 line: FML instantiates the class returned by
 * {@link #mainConfigGuiClass()} through its {@code (GuiScreen parent)} constructor. Named from the
 * 1.7.10 {@code @Mod(guiFactory = ...)}.
 */
public final class ClassicGuiFactory implements IModGuiFactory {

    @Override
    public void initialize(Minecraft minecraft) {
    }

    @Override
    public Class<? extends GuiScreen> mainConfigGuiClass() {
        return ClassicConfigScreen.class;
    }

    @Override
    public Set<IModGuiFactory.RuntimeOptionCategoryElement> runtimeGuiCategories() {
        return Collections.emptySet();
    }

    @Override
    public IModGuiFactory.RuntimeOptionGuiHandler getHandlerFor(
            IModGuiFactory.RuntimeOptionCategoryElement element) {
        return null;
    }
}
