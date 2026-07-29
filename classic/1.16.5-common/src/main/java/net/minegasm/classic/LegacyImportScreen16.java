package net.minegasm.classic;

import net.minegasm.client.MinegasmClient;
import net.minegasm.config.LegacyMinegasmImporter;

import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.TextComponent;

import com.mojang.blaze3d.vertex.PoseStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Read-only preview of importing the legacy {@code minegasm-client.toml}, with an explicit Apply, for
 * 1.16.5. Mirrors the modern {@code LegacyImportScreen}; the legacy file is never modified.
 */
public final class LegacyImportScreen16 extends Screen {

    private final Screen parent;
    private final MinegasmClient client;
    private final LegacyMinegasmImporter.ImportPreview preview;
    private final List<String> rows = new ArrayList<String>();

    public LegacyImportScreen16(Screen parent, MinegasmClient client) {
        super(new TextComponent("Import legacy Minegasm config"));
        this.parent = parent;
        this.client = client;
        this.preview = client.previewLegacyImport();
        preview.summary().forEach((k, v) -> rows.add(k + ": " + v));
    }

    @Override
    protected void init() {
        int cx = width / 2;
        int y = height - 52;
        addButton(new Button(cx - 102, y, 100, 20, new TextComponent("Apply"), b -> {
            client.applyLegacyImport(preview);
            onClose();
        }));
        addButton(new Button(cx + 2, y, 100, 20, new TextComponent("Cancel"), b -> onClose()));
    }

    @Override
    public void render(PoseStack pose, int mouseX, int mouseY, float partialTick) {
        renderBackground(pose);
        GuiComponent.drawCenteredString(pose, font, title, width / 2, 18, 0xFFFFFF);
        GuiComponent.drawCenteredString(pose, font, new TextComponent("From minegasm-client.toml"),
                width / 2, 34, 0xA0A0A0);
        int y = 52;
        int limit = Math.min(rows.size(), Math.max(0, (height - 116) / 10));
        for (int i = 0; i < limit; i++) {
            GuiComponent.drawCenteredString(pose, font, new TextComponent(rows.get(i)),
                    width / 2, y + i * 10, 0xD0D0D0);
        }
        if (rows.size() > limit) {
            GuiComponent.drawCenteredString(pose, font,
                    new TextComponent("... and " + (rows.size() - limit) + " more"),
                    width / 2, y + limit * 10, 0xA0A0A0);
        }
        GuiComponent.drawCenteredString(pose, font,
                new TextComponent("The legacy file is left unchanged."), width / 2, height - 68, 0xA0A0A0);
        super.render(pose, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
