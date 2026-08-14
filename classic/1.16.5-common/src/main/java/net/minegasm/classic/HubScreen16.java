package net.minegasm.classic;

import net.minegasm.buttplug.ConnectionState;
import net.minegasm.client.MinegasmClient;
import net.minegasm.config.HapticConfig;

import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.TextComponent;

import com.mojang.blaze3d.vertex.PoseStack;

import java.util.List;
import java.util.Locale;

/**
 * The Minegasm entry screen for 1.16.5: a hub where every integration is a peer (ADR-018). Buttplug and
 * each bridge are sibling rows, each opening its own screen; global controls (master output, emergency
 * stop) and the shared screens live here. Mirrors the modern hub.
 */
public final class HubScreen16 extends Screen {

    private final Screen parent;
    private final MinegasmClient client;
    private ConnectionState observedState;
    private long observedGeneration = -1;
    private boolean observedEnabled;
    private int observedBridgeConn;
    private int observedSafety = -1;
    private RowScroller scroller;

    public HubScreen16(Screen parent) {
        super(new TextComponent("Minegasm"));
        this.parent = parent;
        this.client = ClassicClientHolder.get();
    }

    @Override
    protected void init() {
        int w = Math.min(width - 16, 280);
        int x = (width - w) / 2;
        int h = 20;
        int gap = 24;
        int half = (w - 4) / 2;

        boolean enabled = client.config().enabled();

        addButton(new Button(x, 44, half, h, new TextComponent("Haptics: " + onOff(enabled)),
                b -> toggleEnabled()));
        // Stop only when running; Resume only for a user panic; a watchdog stop is shown but not resumable
        // by this button, so a resume can never clear a live watchdog stall (review P1-2).
        net.minegasm.runtime.OutputStatus output = client.outputStatus();
        if (output.userResumable()) {
            addButton(new Button(x + half + 4, 44, half, h, new TextComponent("Resume"),
                    b -> resumeOutput()));
        } else if (!output.permitted()) {
            Button wd = addButton(new Button(x + half + 4, 44, half, h,
                    new TextComponent("Watchdog stopped"), b -> { }));
            wd.active = false;
        } else {
            addButton(new Button(x + half + 4, 44, half, h, new TextComponent("Stop"),
                    b -> stopOutput()));
        }

        // Fixed bottom block, anchored to the screen bottom so it never scrolls away.
        int doneY = height - 24;
        int customY = doneY - gap;
        int utilY = customY - gap;
        int addY = utilY - (gap + 6);

        // Integrations, all peers: Buttplug first, then each configured bridge. Only this list scrolls,
        // since bridges are the one unbounded set; everything else is pinned.
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
                addButton(new Button(x, ry, rowW, h, new TextComponent(buttplugLabel()),
                        b -> openButtplug()));
            } else {
                final int index = i - 1;
                addButton(new Button(x, ry, rowW, h, new TextComponent(bridgeLabel(bridges.get(index))),
                        b -> minecraft.setScreen(new BridgeEditScreen16(this, client, index))));
            }
        }
        if (needsScroll) {
            int scrollX = x + w - 20;
            Button up = addButton(new Button(scrollX, viewportTop, 20, h, new TextComponent("^"), b -> {
                scroller.up();
                rebuild();
            }));
            up.active = scroller.canScrollUp();
            Button down = addButton(new Button(scrollX, viewportBottom - 20, 20, h, new TextComponent("v"),
                    b -> {
                        scroller.down();
                        rebuild();
                    }));
            down.active = scroller.canScrollDown();
        }

        addButton(new Button(x, addY, w, h, new TextComponent("Add bridge"),
                b -> minecraft.setScreen(new BridgeEditScreen16(this, client, bridges.size()))));

        addButton(new Button(x, utilY, half, h, new TextComponent("Settings..."),
                b -> minecraft.setScreen(new SettingsScreen16(this, client))));
        addButton(new Button(x + half + 4, utilY, half, h, new TextComponent("Scene packs..."),
                b -> minecraft.setScreen(new ScenePackScreen16(this, client))));
        addButton(new Button(x, customY, half, h, new TextComponent("Customization..."),
                b -> minecraft.setScreen(new CustomizationScreen16(this, client))));
        if (client.hasLegacyConfig()) {
            addButton(new Button(x + half + 4, customY, half, h, new TextComponent("Legacy import..."),
                    b -> minecraft.setScreen(new LegacyImportScreen16(this, client))));
        }

        addButton(new Button(x, doneY, w, h, new TextComponent("Done"), b -> onClose()));

        observedState = client.status().state();
        observedGeneration = client.provider().devices().generation();
        observedEnabled = enabled;
        observedBridgeConn = bridgeConnMask();
        observedSafety = safetyCode();
    }

    @Override
    public void tick() {
        if (client.status().state() != observedState
                || client.provider().devices().generation() != observedGeneration
                || client.config().enabled() != observedEnabled
                || bridgeConnMask() != observedBridgeConn
                || safetyCode() != observedSafety) {
            rebuild();
        }
    }

    private void rebuild() {
        buttons.clear();
        children.clear();
        init();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (scroller != null && delta > 0 && scroller.canScrollUp()) {
            scroller.up();
            rebuild();
            return true;
        }
        if (scroller != null && delta < 0 && scroller.canScrollDown()) {
            scroller.down();
            rebuild();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    /** A rolling hash of each bridge's chain state, so the rows refresh as links come up or drop. */
    private int bridgeConnMask() {
        return BridgeStatus.hash(client);
    }

    private String buttplugLabel() {
        if (client.buttplugFaulted()) {
            net.minegasm.backend.BackendOutcome failure = client.buttplugFailure();
            return "Buttplug: FAULT" + (failure == null ? "" : " (" + failure + ")");
        }
        int devices = client.provider().devices().all().size();
        return "Buttplug: " + client.status().state().name().toLowerCase(Locale.ROOT)
                + ", " + devices + " devices";
    }

    private String bridgeLabel(HapticConfig.Bridge b) {
        return "Bridge " + b.name() + ": " + BridgeStatus.label(client, b);
    }

    private void toggleEnabled() {
        HapticConfig cfg = client.config().raw();
        HapticConfig.Global g = cfg.global();
        client.updateConfig(new HapticConfig(cfg.schemaVersion(), cfg.profile(),
                new HapticConfig.Global(!g.enabled(), g.intensity(), g.variation(),
                        g.fatigueProtection(), g.pauseBehavior(), g.stopOnWorldUnload(), g.panicKey(),
                        g.testMaxPercent(), g.testMaxDurationMs(),
                        g.unsafeTestMaxPercent(), g.unsafeTestMaxDurationMs()),
                cfg.buttplug(), cfg.events(), cfg.outputPolicy(), cfg.devices(),
                cfg.positionCalibrations(), cfg.accumulation(), cfg.customIntensity(), cfg.bridges()));
        buttons.clear();
        children.clear();
        init();
    }

    private void stopOutput() {
        client.panic();
        rebuild();
    }

    private void resumeOutput() {
        client.clearPanic(); // clears only the user-stop cause
        rebuild();
    }

    private int safetyCode() {
        net.minegasm.runtime.OutputStatus output = client.outputStatus();
        return (output.userStopped() ? 1 : 0)
                | (output.watchdogStopped() ? 2 : 0)
                | (output.disabled() ? 8 : 0)
                | (client.buttplugFaulted() ? 4 : 0);
    }

    private void openButtplug() {
        minecraft.setScreen(new DashboardScreen16(this));
    }

    @Override
    public void render(PoseStack pose, int mouseX, int mouseY, float partialTick) {
        renderBackground(pose);
        GuiComponent.drawCenteredString(pose, font, title, width / 2, 12, 0xFFFFFF);
        GuiComponent.drawCenteredString(pose, font,
                new TextComponent("Integrations, output, and settings"), width / 2, 26, 0xA0A0A0);
        if (!client.outputStatus().permitted()) {
            GuiComponent.drawCenteredString(pose, font,
                    new TextComponent("OUTPUT STOPPED: " + client.outputStatus().blockedReason()),
                    width / 2, 64, 0xFF5555);
        }
        GuiComponent.drawCenteredString(pose, font, new TextComponent("Integrations"),
                width / 2, 78, 0xFFFFFF);
        super.render(pose, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    private static String onOff(boolean value) {
        return value ? "ON" : "OFF";
    }
}
