package net.minegasm.classic;

import net.minegasm.client.MinegasmClient;
import net.minegasm.config.HapticConfig;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import java.io.IOException;
import java.util.List;

/**
 * Bridge-endpoint manager for 1.12.2: one row per endpoint with a toggle and a remove, plus Add. Name
 * and URL are edited in the config file on this loader; the modern and 1.16.5 screens edit them in-game.
 */
public final class ClassicBridgesScreen extends GuiScreen {

    private static final int ID_ADD = 1;
    private static final int ID_DONE = 2;
    private static final int TOGGLE_BASE = 100;
    private static final int REMOVE_BASE = 200;

    private final GuiScreen parent;
    private final MinegasmClient client;

    public ClassicBridgesScreen(GuiScreen parent) {
        this.parent = parent;
        this.client = ClassicClientHolder.get();
    }

    @Override
    public void initGui() {
        buttonList.clear();
        int w = Math.min(width - 16, 300);
        int x = width / 2 - w / 2;
        int y = 40;
        int h = 20;
        List<HapticConfig.Bridge> bridges = BridgeList.bridges(client);
        for (int i = 0; i < bridges.size(); i++) {
            HapticConfig.Bridge b = bridges.get(i);
            addButton(new GuiButton(TOGGLE_BASE + i, x, y, w - 54, h,
                    b.name() + " [" + (b.enabled() ? "ON" : "OFF") + "]"));
            addButton(new GuiButton(REMOVE_BASE + i, x + w - 50, y, 50, h, "Remove"));
            y += 24;
        }
        addButton(new GuiButton(ID_ADD, x, y, w, h, "Add bridge"));
        addButton(new GuiButton(ID_DONE, x, height - 26, w, h, "Done"));
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == ID_DONE) {
            mc.displayGuiScreen(parent);
        } else if (button.id == ID_ADD) {
            BridgeList.add(client);
            refresh();
        } else if (button.id >= REMOVE_BASE) {
            BridgeList.remove(client, button.id - REMOVE_BASE);
            refresh();
        } else if (button.id >= TOGGLE_BASE) {
            BridgeList.toggle(client, button.id - TOGGLE_BASE);
            refresh();
        }
    }

    private void refresh() {
        buttonList.clear();
        initGui();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRenderer, "Minegasm bridges", width / 2, 8, 0xFFFFFF);
        drawCenteredString(fontRenderer, "Local adapters; edit name/URL in the config file",
                width / 2, 22, 0xA0A0A0);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}
