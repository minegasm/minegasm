package net.minegasm.classic;

import net.minegasm.client.MinegasmClient;
import net.minegasm.config.LegacyMinegasmImporter;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Read-only preview of importing the legacy {@code minegasm-client.toml}, with an explicit Apply, for
 * Minecraft 1.8.9. Mirrors the modern legacy-import screen; the legacy file is never modified.
 */
public final class ClassicLegacyImportScreen extends GuiScreen {

    private static final int ID_APPLY = 1;
    private static final int ID_CANCEL = 2;

    private final GuiScreen parent;
    private final MinegasmClient client;
    private final LegacyMinegasmImporter.ImportPreview preview;
    private final List<String> rows = new ArrayList<String>();

    public ClassicLegacyImportScreen(GuiScreen parent) {
        this.parent = parent;
        this.client = ClassicClientHolder.get();
        this.preview = client.previewLegacyImport();
        preview.summary().forEach((k, v) -> rows.add(k + ": " + v));
    }

    @Override
    public void initGui() {
        buttonList.clear();
        int cx = width / 2;
        int y = height - 52;
        buttonList.add(new GuiButton(ID_APPLY, cx - 102, y, 100, 20, "Apply"));
        buttonList.add(new GuiButton(ID_CANCEL, cx + 2, y, 100, 20, "Cancel"));
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        switch (button.id) {
            case ID_APPLY:
                client.applyLegacyImport(preview);
                mc.displayGuiScreen(parent);
                break;
            case ID_CANCEL:
                mc.displayGuiScreen(parent);
                break;
            default:
                break;
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, "Import legacy Minegasm config", width / 2, 18, 0xFFFFFF);
        drawCenteredString(fontRendererObj, "From minegasm-client.toml", width / 2, 34, 0xA0A0A0);
        int y = 52;
        int limit = Math.min(rows.size(), Math.max(0, (height - 116) / 10));
        for (int i = 0; i < limit; i++) {
            drawCenteredString(fontRendererObj, rows.get(i), width / 2, y + i * 10, 0xD0D0D0);
        }
        if (rows.size() > limit) {
            drawCenteredString(fontRendererObj, "... and " + (rows.size() - limit) + " more",
                    width / 2, y + limit * 10, 0xA0A0A0);
        }
        drawCenteredString(fontRendererObj, "The legacy file is left unchanged.",
                width / 2, height - 68, 0xA0A0A0);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}
