package net.minegasm.neoforge;

import net.minegasm.client.MinegasmClient;
import net.minegasm.config.LegacyMinegasmImporter;

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

import java.util.ArrayList;
import java.util.List;

/** Read-only legacy import preview with an explicit apply action. */
public final class LegacyImportScreen extends Screen {
    private final Screen parent;
    private final MinegasmClient client;
    private final LegacyMinegasmImporter.ImportPreview preview;
    private final List<Component> rows = new ArrayList<>();

    public LegacyImportScreen(Screen parent, MinegasmClient client) {
        super(Component.translatable("minegasm.legacy.title"));
        this.parent = parent;
        this.client = client;
        this.preview = client.previewLegacyImport();
        preview.summary().forEach((key, value) ->
                rows.add(Component.literal(key + ": " + value)));
    }

    @Override
    protected void init() {
        int cx = width / 2;
        int y = height - 52;
        addRenderableWidget(button(Component.translatable("minegasm.legacy.apply"), b -> {
            client.applyLegacyImport(preview);
            onClose();
        }, cx - 102, y, 100, 20));
        addRenderableWidget(button(Component.translatable("gui.cancel"), b -> onClose(),
                cx + 2, y, 100, 20));
    }

    // Button.builder(...) was added in 1.19.4; 1.19.2 constructs Button directly. One guarded factory
    // keeps every call site version-agnostic (message, action, then bounds as x/y/width/height).
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
        graphics.centeredText(font, title, width / 2, 18, 0xFFFFFFFF);
        graphics.centeredText(font, Component.translatable("minegasm.legacy.source"),
                width / 2, 34, 0xFFA0A0A0);
        int y = 52;
        int limit = Math.min(rows.size(), Math.max(0, (height - 116) / 10));
        for (int i = 0; i < limit; i++) {
            graphics.centeredText(font, rows.get(i), width / 2, y + i * 10, 0xFFD0D0D0);
        }
        if (rows.size() > limit) {
            graphics.centeredText(font,
                    Component.translatable("minegasm.legacy.more", rows.size() - limit),
                    width / 2, y + limit * 10, 0xFFA0A0A0);
        }
        graphics.centeredText(font, Component.translatable("minegasm.legacy.unchanged"),
                width / 2, height - 68, 0xFFA0A0A0);
    }
    //?} elif >=1.20.1 {
    /*@Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        //? if <1.21.1 {
        /^this.renderBackground(graphics); // 1.20.1 Screen.render() paints no backdrop (1.21.1+'s does)
        ^///?}
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 18, 0xFFFFFFFF);
        graphics.drawCenteredString(font, Component.translatable("minegasm.legacy.source"),
                width / 2, 34, 0xFFA0A0A0);
        int y = 52;
        int limit = Math.min(rows.size(), Math.max(0, (height - 116) / 10));
        for (int i = 0; i < limit; i++) {
            graphics.drawCenteredString(font, rows.get(i), width / 2, y + i * 10, 0xFFD0D0D0);
        }
        if (rows.size() > limit) {
            graphics.drawCenteredString(font,
                    Component.translatable("minegasm.legacy.more", rows.size() - limit),
                    width / 2, y + limit * 10, 0xFFA0A0A0);
        }
        graphics.drawCenteredString(font, Component.translatable("minegasm.legacy.unchanged"),
                width / 2, height - 68, 0xFFA0A0A0);
    }
    *///?} else {
    /*@Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(poseStack); // pre-1.20 Screen.render() paints no backdrop either
        super.render(poseStack, mouseX, mouseY, partialTick);
        // Pre-1.20 draw helpers are static on GuiComponent and take a PoseStack first.
        GuiComponent.drawCenteredString(poseStack, font, title, width / 2, 18, 0xFFFFFFFF);
        GuiComponent.drawCenteredString(poseStack, font, Component.translatable("minegasm.legacy.source"),
                width / 2, 34, 0xFFA0A0A0);
        int y = 52;
        int limit = Math.min(rows.size(), Math.max(0, (height - 116) / 10));
        for (int i = 0; i < limit; i++) {
            GuiComponent.drawCenteredString(poseStack, font, rows.get(i), width / 2, y + i * 10, 0xFFD0D0D0);
        }
        if (rows.size() > limit) {
            GuiComponent.drawCenteredString(poseStack, font,
                    Component.translatable("minegasm.legacy.more", rows.size() - limit),
                    width / 2, y + limit * 10, 0xFFA0A0A0);
        }
        GuiComponent.drawCenteredString(poseStack, font, Component.translatable("minegasm.legacy.unchanged"),
                width / 2, height - 68, 0xFFA0A0A0);
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
