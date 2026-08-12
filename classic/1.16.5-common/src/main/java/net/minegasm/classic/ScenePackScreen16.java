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
    private RowScroller scroller;

    public ScenePackScreen16(Screen parent, MinegasmClient client) {
        super(new TextComponent("Scene packs"));
        this.parent = parent;
        this.client = client;
    }

    @Override
    protected void init() {
        int w = Math.min(width - 16, 300);
        int x = (width - w) / 2;
        int h = 20;
        int gap = 24;

        // The whole pack list scrolls, since the number of installed packs is unbounded; Done stays pinned.
        String selected = ScenePackList.selected(client);
        java.util.List<ScenePackList.Entry> entries = ScenePackList.options(client);
        int doneY = height - 24;
        int viewportTop = 44;
        int viewportBottom = doneY - 6;
        int rowCount = entries.size();
        int visibleRows = Math.max(1, (viewportBottom - viewportTop) / gap);
        if (scroller == null) {
            scroller = new RowScroller(visibleRows, rowCount);
        } else {
            scroller.resize(visibleRows, rowCount);
        }
        boolean needsScroll = rowCount > visibleRows;
        int rowW = needsScroll ? w - 20 : w;

        for (int i = 0; i < rowCount; i++) {
            if (!scroller.isVisible(i)) {
                continue;
            }
            int ry = viewportTop + (i - scroller.first()) * gap;
            ScenePackList.Entry e = entries.get(i);
            boolean current = e.id.equals(selected);
            Button b = addButton(new Button(x, ry, rowW, h,
                    new TextComponent(current ? e.label + " (selected)" : e.label),
                    btn -> select(e.id)));
            b.active = !current; // the current pack is shown but not re-selectable
        }
        if (needsScroll) {
            int scrollX = x + w - 20;
            Button up = addButton(new Button(scrollX, viewportTop, 20, h, new TextComponent("^"), b -> {
                scroller.up();
                rebuild();
            }));
            up.active = scroller.canScrollUp();
            Button down = addButton(new Button(scrollX, viewportBottom - 20, 20, h, new TextComponent("v"),
                    b -> {
                        scroller.down();
                        rebuild();
                    }));
            down.active = scroller.canScrollDown();
        }

        addButton(new Button(x, doneY, w, h, new TextComponent("Done"), b -> onClose()));
    }

    private void select(String id) {
        ScenePackList.select(client, id);
        rebuild();
    }

    private void rebuild() {
        buttons.clear();
        children.clear();
        init();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (scroller != null && delta > 0 && scroller.canScrollUp()) {
            scroller.up();
            rebuild();
            return true;
        }
        if (scroller != null && delta < 0 && scroller.canScrollDown()) {
            scroller.down();
            rebuild();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
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
