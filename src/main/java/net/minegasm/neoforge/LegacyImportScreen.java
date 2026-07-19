package net.minegasm.neoforge;

import net.minegasm.client.MinegasmClient;
import net.minegasm.config.LegacyMinegasmImporter;

import net.minecraft.client.gui.GuiGraphicsExtractor;
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
        addRenderableWidget(Button.builder(Component.translatable("minegasm.legacy.apply"), b -> {
            client.applyLegacyImport(preview);
            onClose();
        }).bounds(cx - 102, y, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
                .bounds(cx + 2, y, 100, 20).build());
    }

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

    @Override
    public void onClose() {
        //? if >=26.2 {
        this.minecraft.gui.setScreen(parent);
        //?} else {
        /*this.minecraft.setScreen(parent);
        *///?}
    }
}
