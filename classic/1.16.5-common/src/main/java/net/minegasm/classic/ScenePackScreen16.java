package net.minegasm.classic;

import net.minegasm.client.MinegasmClient;

import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.TextComponent;

import com.mojang.blaze3d.vertex.PoseStack;

/**
 * Scene-pack picker for 1.16.5, reached from the dashboard and from the settings recipe row. Lists the
 * two built-ins plus every loaded file pack and writes the chosen id straight to the config (ADR-017),
 * matching the modern pack screen. Selection logic is shared in {@link ScenePackList}.
 */
public final class ScenePackScreen16 extends Screen {

    private final Screen parent;
    private final MinegasmClient client;

    public ScenePackScreen16(Screen parent, MinegasmClient client) {
        super(new TextComponent("Scene packs"));
        this.parent = parent;
        this.client = client;
    }

    @Override
    protected void init() {
        int w = Math.min(width - 16, 300);
        int x = (width - w) / 2;
        int y = 44;
        int h = 20;
        int gap = 24;

        String selected = ScenePackList.selected(client);
        for (ScenePackList.Entry e : ScenePackList.options(client)) {
            boolean current = e.id.equals(selected);
            Button b = addButton(new Button(x, y, w, h,
                    new TextComponent(current ? e.label + " (selected)" : e.label),
                    btn -> select(e.id)));
            b.active = !current; // the current pack is shown but not re-selectable
            y += gap;
        }

        addButton(new Button(x, height - 24, w, h, new TextComponent("Done"), b -> onClose()));
    }

    private void select(String id) {
        ScenePackList.select(client, id);
        buttons.clear();
        children.clear();
        init();
    }

    @Override
    public void render(PoseStack pose, int mouseX, int mouseY, float partialTicks) {
        renderBackground(pose);
        GuiComponent.drawCenteredString(pose, font, "Minegasm Scene Packs", width / 2, 20, 0xFFFFFF);
        GuiComponent.drawCenteredString(pose, font,
                client.scenePackCount() + " loaded from the scene-packs folder",
                width / 2, 31, 0xA0A0A0);
        super.render(pose, mouseX, mouseY, partialTicks);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
