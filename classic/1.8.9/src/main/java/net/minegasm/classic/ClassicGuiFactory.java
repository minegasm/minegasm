package net.minegasm.classic;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fml.client.IModGuiFactory;

import java.util.Collections;
import java.util.Set;

/**
 * Wires the Minecraft 1.8.9 "Config" button in the mods list to {@link ClassicConfigScreen}. This line's
 * {@code IModGuiFactory} is the older form: it hands back the config screen {@link Class} via
 * {@link #mainConfigGuiClass()}, which FML instantiates reflectively through the screen's
 * {@code (GuiScreen parent)} constructor, rather than the 1.12.2 {@code createConfigGui(parent)} form.
 * Referenced by fully-qualified name from the shared {@code @Mod(guiFactory = ...)} in
 * {@code classic/forge}; 1.12.2 carries its own class of the same name against the newer interface.
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
