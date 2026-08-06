package net.minegasm.neoforge;

import net.minegasm.client.MinegasmClient;
import net.minegasm.config.HapticConfig;

//? if >=26.1.2 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} elif >=1.20.1 {
/*import net.minecraft.client.gui.GuiGraphics;
*///?} else {
/*import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiComponent;
*///?}
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Bridge-endpoint manager (ADR-018 multi-endpoint): lists every configured bridge, one row per endpoint
 * showing its name, on/off state, and URL. A row opens {@link MinegasmBridgeEditScreen}; the Add button
 * appends a new one. Any number run at once, each its own backend.
 */
public final class MinegasmBridgesScreen extends Screen {

    private final Screen parent;
    private final MinegasmClient client;

    public MinegasmBridgesScreen(Screen parent, MinegasmClient client) {
        super(Component.translatable("minegasm.bridges.title"));
        this.parent = parent;
        this.client = client;
    }

    @Override
    protected void init() {
        int width = Math.min(this.width - 16, 300);
        int x = (this.width - width) / 2;
        int y = 44;
        int h = 20;
        int gap = 24;

        List<HapticConfig.Bridge> bridges = client.config().raw().bridges();
        for (int i = 0; i < bridges.size(); i++) {
            final int index = i;
            addRenderableWidget(button(rowLabel(bridges.get(i)), b -> openEdit(index), x, y, width, h));
            y += gap;
        }

        addRenderableWidget(button(Component.translatable("minegasm.bridges.add"),
                b -> openEdit(bridges.size()), x, y, width, h));
        addRenderableWidget(button(Component.translatable("gui.done"), b -> onClose(),
                x, this.height - 24, width, h));
    }

    private Component rowLabel(HapticConfig.Bridge b) {
        return Component.translatable("minegasm.bridges.row", b.name(),
                Component.translatable(b.enabled() ? "options.on" : "options.off"), b.url());
    }

    private void openEdit(int index) {
        //? if >=26.2 {
        this.minecraft.gui.setScreen(new MinegasmBridgeEditScreen(this, client, index));
        //?} else {
        /*this.minecraft.setScreen(new MinegasmBridgeEditScreen(this, client, index));
        *///?}
    }

    private Button button(Component message, Button.OnPress onPress, int x, int y, int width, int height) {
        //? if >=1.20.1 {
        return Button.builder(message, onPress).bounds(x, y, width, height).build();
        //?} else {
        /*return new Button(x, y, width, height, message, onPress);
        *///?}
    }

    //? if >=26.1.2 {
    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                   float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(this.font, this.title, this.width / 2, 20, 0xFFFFFFFF);
        graphics.centeredText(this.font, Component.translatable("minegasm.bridges.subtitle"),
                this.width / 2, 31, 0xFFA0A0A0);
    }
    //?} elif >=1.20.1 {
    /*@Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        //? if <1.21.1 {
        /^this.renderBackground(graphics);
        ^///?}
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFFFF);
        graphics.drawCenteredString(this.font, Component.translatable("minegasm.bridges.subtitle"),
                this.width / 2, 31, 0xFFA0A0A0);
    }
    *///?} else {
    /*@Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(poseStack);
        super.render(poseStack, mouseX, mouseY, partialTick);
        GuiComponent.drawCenteredString(poseStack, this.font, this.title, this.width / 2, 20, 0xFFFFFFFF);
        GuiComponent.drawCenteredString(poseStack, this.font,
                Component.translatable("minegasm.bridges.subtitle"), this.width / 2, 31, 0xFFA0A0A0);
    }
    *///?}

    @Override
    public void onClose() {
        //? if >=26.2 {
        this.minecraft.gui.setScreen(parent);
        //?} else {
        /*this.minecraft.setScreen(parent);
        *///?}
    }
}
