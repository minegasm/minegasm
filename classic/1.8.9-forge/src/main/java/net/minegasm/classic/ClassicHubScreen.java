package net.minegasm.classic;

import net.minegasm.client.MinegasmClient;
import net.minegasm.config.HapticConfig;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * The Minegasm entry screen for 1.8.9: a hub where every integration is a peer (ADR-018). Buttplug and
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
    private static final int ID_SCROLL_UP = 10;
    private static final int ID_SCROLL_DOWN = 11;
    private static final int BRIDGE_BASE = 100;

    private final GuiScreen parent;
    private final MinegasmClient client;
    private int observedBridgeConn;
    private RowScroller scroller;

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

        // Fixed bottom block, anchored to the screen bottom so it never scrolls away.
        int doneY = height - 24;
        int customY = doneY - gap;
        int utilY = customY - gap;
        int addY = utilY - (gap + 6);

        // Integrations, all peers: Buttplug first, then each configured bridge. Only this list scrolls,
        // since bridges are the one unbounded set; everything else is pinned. Up/down buttons page it.
        List<HapticConfig.Bridge> bridges = client.config().raw().bridges();
        int rowCount = 1 + bridges.size();
        int viewportTop = 92;
        int viewportBottom = addY - 6;
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
            if (i == 0) {
                buttonList.add(new GuiButton(ID_BUTTPLUG, x, ry, rowW, h, buttplugLabel()));
            } else {
                buttonList.add(new GuiButton(BRIDGE_BASE + (i - 1), x, ry, rowW, h,
                        bridgeLabel(bridges.get(i - 1))));
            }
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

        buttonList.add(new GuiButton(ID_ADD, x, addY, w, h, "Add bridge"));

        buttonList.add(new GuiButton(ID_SETTINGS, x, utilY, half, h, "Settings..."));
        buttonList.add(new GuiButton(ID_PACKS, x + half + 4, utilY, half, h, "Scene packs..."));
        buttonList.add(new GuiButton(ID_CUSTOM, x, customY, half, h, "Customization..."));
        if (client.hasLegacyConfig()) {
            buttonList.add(new GuiButton(ID_LEGACY, x + half + 4, customY, half, h, "Legacy import..."));
        }
        buttonList.add(new GuiButton(ID_DONE, x, doneY, w, h, "Done"));
        observedBridgeConn = bridgeConnMask();
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
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
            case ID_SCROLL_UP:
                scroller.up();
                initGui();
                break;
            case ID_SCROLL_DOWN:
                scroller.down();
                initGui();
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

    @Override
    public void updateScreen() {
        // Rebuild when a bridge's adapter link comes up or drops, so the connection labels stay live.
        if (bridgeConnMask() != observedBridgeConn) {
            initGui();
        }
    }

    /** A bit per configured bridge that is connected to its adapter, so the rows refresh as links come up. */
    private int bridgeConnMask() {
        return BridgeStatus.hash(client);
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
        return "Bridge " + b.name() + ": " + BridgeStatus.label(client, b);
    }
}
