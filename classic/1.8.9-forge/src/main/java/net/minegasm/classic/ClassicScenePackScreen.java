package net.minegasm.classic;

import net.minegasm.client.MinegasmClient;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import java.io.IOException;
import java.util.List;

/**
 * Scene-pack picker for 1.8.9, reached from the dashboard and from the settings recipe row. Lists the
 * two built-ins plus every loaded file pack and writes the chosen id straight to the config (ADR-017),
 * matching the modern pack screen. Selection logic is shared in {@link ScenePackList}.
 */
public final class ClassicScenePackScreen extends GuiScreen {

    private static final int ID_DONE = 1;
    private static final int ID_FIRST_PACK = 100;

    private final GuiScreen parent;
    private final MinegasmClient client;
    private List<ScenePackList.Entry> entries;

    public ClassicScenePackScreen(GuiScreen parent) {
        this.parent = parent;
        this.client = ClassicClientHolder.get();
    }

    @Override
    public void initGui() {
        buttonList.clear();
        entries = ScenePackList.options(client);
        String selected = ScenePackList.selected(client);
        int w = Math.min(width - 16, 300);
        int x = (width - w) / 2;
        int y = 44;
        int h = 20;
        int gap = 24;
        for (int i = 0; i < entries.size(); i++) {
            ScenePackList.Entry e = entries.get(i);
            boolean current = e.id.equals(selected);
            GuiButton b = new GuiButton(ID_FIRST_PACK + i, x, y, w, h,
                    current ? e.label + " (selected)" : e.label);
            b.enabled = !current; // the current pack is shown but not re-selectable
            buttonList.add(b);
            y += gap;
        }
        buttonList.add(new GuiButton(ID_DONE, x, height - 24, w, h, "Done"));
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == ID_DONE) {
            mc.displayGuiScreen(parent);
            return;
        }
        int idx = button.id - ID_FIRST_PACK;
        if (idx >= 0 && idx < entries.size()) {
            ScenePackList.select(client, entries.get(idx).id);
            initGui(); // rebuild to move the highlight
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, "Minegasm Scene Packs", width / 2, 20, 0xFFFFFF);
        drawCenteredString(fontRendererObj, client.scenePackCount()
                + " loaded from the scene-packs folder", width / 2, 31, 0xA0A0A0);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}
