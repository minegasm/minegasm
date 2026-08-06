package net.minegasm.classic;

import net.minegasm.client.MinegasmClient;
import net.minegasm.config.HapticConfig;

import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.TextComponent;

import com.mojang.blaze3d.vertex.PoseStack;

import java.util.List;

/** Bridge-endpoint manager for 1.16.5: one row per endpoint, opening the editor; Add appends one. */
public final class BridgesScreen16 extends Screen {

    private final Screen parent;
    private final MinegasmClient client;

    public BridgesScreen16(Screen parent, MinegasmClient client) {
        super(new TextComponent("Bridges"));
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

        List<HapticConfig.Bridge> bridges = BridgeList.bridges(client);
        for (int i = 0; i < bridges.size(); i++) {
            final int index = i;
            addButton(new Button(x, y, w, h, rowLabel(bridges.get(i)),
                    b -> minecraft.setScreen(new BridgeEditScreen16(this, client, index))));
            y += gap;
        }
        final int addIndex = bridges.size();
        addButton(new Button(x, y, w, h, new TextComponent("Add bridge"),
                b -> minecraft.setScreen(new BridgeEditScreen16(this, client, addIndex))));
        addButton(new Button(x, height - 24, w, h, new TextComponent("Done"), b -> onClose()));
    }

    private TextComponent rowLabel(HapticConfig.Bridge b) {
        return new TextComponent(b.name() + " [" + (b.enabled() ? "ON" : "OFF") + "] - " + b.url());
    }

    @Override
    public void render(PoseStack pose, int mouseX, int mouseY, float partialTicks) {
        renderBackground(pose);
        GuiComponent.drawCenteredString(pose, font, "Minegasm Bridges", width / 2, 20, 0xFFFFFF);
        GuiComponent.drawCenteredString(pose, font, "Local adapters that receive governed scenes",
                width / 2, 31, 0xA0A0A0);
        super.render(pose, mouseX, mouseY, partialTicks);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
