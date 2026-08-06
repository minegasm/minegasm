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
        boolean panic = !client.runtime().worker().isOutputEnabled();

        addButton(new Button(x, 44, half, h, new TextComponent("Haptics: " + onOff(enabled)),
                b -> toggleEnabled()));
        addButton(new Button(x + half + 4, 44, half, h,
                new TextComponent(panic ? "Resume" : "Stop"), b -> togglePanic()));

        int y = 92;
        addButton(new Button(x, y, w, h, new TextComponent(buttplugLabel()), b -> openButtplug()));
        y += gap;

        List<HapticConfig.Bridge> bridges = client.config().raw().bridges();
        for (int i = 0; i < bridges.size(); i++) {
            final int index = i;
            addButton(new Button(x, y, w, h, new TextComponent(bridgeLabel(bridges.get(i))),
                    b -> minecraft.setScreen(new BridgeEditScreen16(this, client, index))));
            y += gap;
        }
        addButton(new Button(x, y, w, h, new TextComponent("Add bridge"),
                b -> minecraft.setScreen(new BridgeEditScreen16(this, client, bridges.size()))));
        y += gap + 6;

        addButton(new Button(x, y, half, h, new TextComponent("Settings..."),
                b -> minecraft.setScreen(new SettingsScreen16(this, client))));
        addButton(new Button(x + half + 4, y, half, h, new TextComponent("Scene packs..."),
                b -> minecraft.setScreen(new ScenePackScreen16(this, client))));
        y += gap;
        addButton(new Button(x, y, half, h, new TextComponent("Customization..."),
                b -> minecraft.setScreen(new CustomizationScreen16(this, client))));
        if (client.hasLegacyConfig()) {
            addButton(new Button(x + half + 4, y, half, h, new TextComponent("Legacy import..."),
                    b -> minecraft.setScreen(new LegacyImportScreen16(this, client))));
        }

        addButton(new Button(x, height - 24, w, h, new TextComponent("Done"), b -> onClose()));

        observedState = client.status().state();
        observedGeneration = client.provider().devices().generation();
        observedEnabled = enabled;
    }

    @Override
    public void tick() {
        if (client.status().state() != observedState
                || client.provider().devices().generation() != observedGeneration
                || client.config().enabled() != observedEnabled) {
            buttons.clear();
            children.clear();
            init();
        }
    }

    private String buttplugLabel() {
        int devices = client.provider().devices().all().size();
        return "Buttplug: " + client.status().state().name().toLowerCase(Locale.ROOT)
                + ", " + devices + " devices";
    }

    private String bridgeLabel(HapticConfig.Bridge b) {
        return "Bridge " + b.name() + " [" + (b.enabled() ? "ON" : "OFF") + "]";
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

    private void togglePanic() {
        if (client.runtime().worker().isOutputEnabled()) {
            client.panic();
        } else {
            client.clearPanic();
        }
        buttons.clear();
        children.clear();
        init();
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
