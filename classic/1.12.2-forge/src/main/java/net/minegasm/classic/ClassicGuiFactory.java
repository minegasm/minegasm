package net.minegasm.classic;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fml.client.IModGuiFactory;

import java.util.Collections;
import java.util.Set;

/**
 * Wires the Minecraft 1.12.2 "Config" button in the mods list to {@link ClassicConfigScreen}. The
 * {@code net.minecraftforge.fml.client.IModGuiFactory} shape here (1.12.2) is the newer one:
 * {@link #hasConfigGui()} plus {@link #createConfigGui(GuiScreen)}, unlike the 1.7.10/1.8.9
 * {@code mainConfigGuiClass()} form. Referenced by fully-qualified name from the shared
 * {@code @Mod(guiFactory = ...)} in {@code classic/forge}; the 1.8.9 tree carries its own class of the
 * same name against the older interface.
 */
public final class ClassicGuiFactory implements IModGuiFactory {

    @Override
    public void initialize(Minecraft minecraft) {
    }

    @Override
    public boolean hasConfigGui() {
        return true;
    }

    @Override
    public GuiScreen createConfigGui(GuiScreen parent) {
        return new ClassicHubScreen(parent);
    }

    @Override
    public Set<RuntimeOptionCategoryElement> runtimeGuiCategories() {
        return Collections.emptySet();
    }
}
