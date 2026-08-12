package net.minegasm.classic;

import net.minegasm.client.MinegasmClient;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import java.util.List;

/**
 * Scene-pack picker for 1.7.10, reached from the dashboard and from the settings recipe row. Lists the
 * two built-ins plus every loaded file pack and writes the chosen id straight to the config (ADR-017),
 * matching the modern pack screen. Selection logic is shared in {@link ScenePackList}.
 */
public final class ClassicScenePackScreen extends GuiScreen {

    private static final int ID_DONE = 1;
    private static final int ID_SCROLL_UP = 10;
    private static final int ID_SCROLL_DOWN = 11;
    private static final int ID_FIRST_PACK = 100;

    private final GuiScreen parent;
    private final MinegasmClient client;
    private List<ScenePackList.Entry> entries;
    private RowScroller scroller;

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
        int h = 20;
        int gap = 24;

        // The whole pack list scrolls, since the number of installed packs is unbounded; Done stays pinned.
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
            GuiButton b = new GuiButton(ID_FIRST_PACK + i, x, ry, rowW, h,
                    current ? e.label + " (selected)" : e.label);
            b.enabled = !current; // the current pack is shown but not re-selectable
            buttonList.add(b);
        }
        if (needsScroll) {
            int scrollX = x + w - 20;
            GuiButton up = new GuiButton(ID_SCROLL_UP, scrollX, viewportTop, 20, h, "^");
            up.enabled = scroller.canScrollUp();
            buttonList.add(up);
            GuiButton down = new GuiButton(ID_SCROLL_DOWN, scrollX, viewportBottom - 20, 20, h, "v");
            down.enabled = scroller.canScrollDown();
            buttonList.add(down);
        }

        buttonList.add(new GuiButton(ID_DONE, x, doneY, w, h, "Done"));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == ID_DONE) {
            mc.displayGuiScreen(parent);
            return;
        }
        if (button.id == ID_SCROLL_UP) {
            scroller.up();
            initGui();
            return;
        }
        if (button.id == ID_SCROLL_DOWN) {
            scroller.down();
            initGui();
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
