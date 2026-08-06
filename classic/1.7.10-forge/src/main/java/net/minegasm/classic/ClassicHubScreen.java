package net.minegasm.classic;

import net.minegasm.client.MinegasmClient;
import net.minegasm.config.HapticConfig;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import java.util.List;
import java.util.Locale;

/**
 * The Minegasm entry screen for 1.7.10: a hub where every integration is a peer (ADR-018). Buttplug and
 * each bridge are sibling rows, each opening its own screen; global controls (master output, emergency
 * stop) and the shared screens live here. Mirrors the modern and 1.16.5 hubs.
 */
public final class ClassicHubScreen extends GuiScreen {

    private static final int ID_ENABLED = 1;
    private static final int ID_PANIC = 2;
    private static final int ID_BUTTPLUG = 3;
    private static final int ID_ADD = 4;
    private static final int ID_SETTINGS = 5;
    private static final int ID_PACKS = 6;
    private static final int ID_CUSTOM = 7;
    private static final int ID_LEGACY = 8;
    private static final int ID_DONE = 9;
    private static final int BRIDGE_BASE = 100;

    private final GuiScreen parent;
    private final MinegasmClient client;

    public ClassicHubScreen(GuiScreen parent) {
        this.parent = parent;
        this.client = ClassicClientHolder.get();
    }

    @Override
    public void initGui() {
        buttonList.clear();
        int w = Math.min(width - 16, 280);
        int x = width / 2 - w / 2;
        int h = 20;
        int gap = 24;
        int half = (w - 4) / 2;

        buttonList.add(new GuiButton(ID_ENABLED, x, 44, half, h, enabledLabel()));
        buttonList.add(new GuiButton(ID_PANIC, x + half + 4, 44, half, h, panicLabel()));

        int y = 92;
        buttonList.add(new GuiButton(ID_BUTTPLUG, x, y, w, h, buttplugLabel()));
        y += gap;
        List<HapticConfig.Bridge> bridges = client.config().raw().bridges();
        for (int i = 0; i < bridges.size(); i++) {
            buttonList.add(new GuiButton(BRIDGE_BASE + i, x, y, w, h, bridgeLabel(bridges.get(i))));
            y += gap;
        }
        buttonList.add(new GuiButton(ID_ADD, x, y, w, h, "Add bridge"));
        y += gap + 6;

        buttonList.add(new GuiButton(ID_SETTINGS, x, y, half, h, "Settings..."));
        buttonList.add(new GuiButton(ID_PACKS, x + half + 4, y, half, h, "Scene packs..."));
        y += gap;
        buttonList.add(new GuiButton(ID_CUSTOM, x, y, half, h, "Customization..."));
        if (client.hasLegacyConfig()) {
            buttonList.add(new GuiButton(ID_LEGACY, x + half + 4, y, half, h, "Legacy import..."));
        }
        buttonList.add(new GuiButton(ID_DONE, x, height - 24, w, h, "Done"));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        switch (button.id) {
            case ID_ENABLED:
                toggleEnabled();
                initGui();
                break;
            case ID_PANIC:
                togglePanic();
                initGui();
                break;
            case ID_BUTTPLUG:
                mc.displayGuiScreen(new ClassicConfigScreen(this));
                break;
            case ID_ADD:
                mc.displayGuiScreen(new ClassicBridgeEditScreen(this,
                        client.config().raw().bridges().size()));
                break;
            case ID_SETTINGS:
                mc.displayGuiScreen(new ClassicSettingsScreen(this));
                break;
            case ID_PACKS:
                mc.displayGuiScreen(new ClassicScenePackScreen(this));
                break;
            case ID_CUSTOM:
                mc.displayGuiScreen(new CustomizationScreen(this));
                break;
            case ID_LEGACY:
                mc.displayGuiScreen(new ClassicLegacyImportScreen(this));
                break;
            case ID_DONE:
                mc.displayGuiScreen(parent);
                break;
            default:
                if (button.id >= BRIDGE_BASE) {
                    mc.displayGuiScreen(new ClassicBridgeEditScreen(this, button.id - BRIDGE_BASE));
                }
                break;
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, "Minegasm", width / 2, 12, 0xFFFFFF);
        drawCenteredString(fontRendererObj, "Integrations, output, and settings", width / 2, 26, 0xA0A0A0);
        drawCenteredString(fontRendererObj, "Integrations", width / 2, 78, 0xFFFFFF);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void toggleEnabled() {
        ClassicConfigModel model = new ClassicConfigModel(client.config().raw());
        model.enabled = !model.enabled;
        model.apply(client);
    }

    private void togglePanic() {
        if (client.runtime().worker().isOutputEnabled()) {
            client.panic();
        } else {
            client.clearPanic();
        }
    }

    private String enabledLabel() {
        return "Haptics: " + (client.config().enabled() ? "ON" : "OFF");
    }

    private String panicLabel() {
        return client.runtime().worker().isOutputEnabled() ? "Stop" : "Resume";
    }

    private String buttplugLabel() {
        int devices = client.provider().devices().all().size();
        return "Buttplug: " + client.status().state().name().toLowerCase(Locale.ROOT)
                + ", " + devices + " devices";
    }

    private String bridgeLabel(HapticConfig.Bridge b) {
        return "Bridge " + b.name() + " [" + (b.enabled() ? "ON" : "OFF") + "]";
    }
}
