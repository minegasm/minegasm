package net.minegasm.classic;

import net.minegasm.client.MinegasmClient;
import net.minegasm.config.HapticConfig;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import java.io.IOException;
import java.util.List;

/**
 * Bridge-endpoint manager for 1.8.9: one row per endpoint, opening {@link ClassicBridgeEditScreen};
 * Add opens the editor for a new one. Matches the 1.16.5 and modern managers (ADR-018).
 */
public final class ClassicBridgesScreen extends GuiScreen {

    private static final int ID_ADD = 1;
    private static final int ID_DONE = 2;
    private static final int ROW_BASE = 100;

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
            buttonList.add(new GuiButton(ROW_BASE + i, x, y, w, h, rowLabel(bridges.get(i))));
            y += 24;
        }
        buttonList.add(new GuiButton(ID_ADD, x, y, w, h, "Add bridge"));
        buttonList.add(new GuiButton(ID_DONE, x, height - 26, w, h, "Done"));
    }

    private String rowLabel(HapticConfig.Bridge b) {
        return b.name() + " [" + (b.enabled() ? "ON" : "OFF") + "] - " + b.url();
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == ID_DONE) {
            mc.displayGuiScreen(parent);
        } else if (button.id == ID_ADD) {
            mc.displayGuiScreen(new ClassicBridgeEditScreen(this, BridgeList.bridges(client).size()));
        } else if (button.id >= ROW_BASE) {
            mc.displayGuiScreen(new ClassicBridgeEditScreen(this, button.id - ROW_BASE));
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, "Minegasm bridges", width / 2, 8, 0xFFFFFF);
        drawCenteredString(fontRendererObj, "Local adapters that receive governed scenes",
                width / 2, 22, 0xA0A0A0);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}
