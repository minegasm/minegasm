package net.minegasm.neoforge;

import net.minegasm.buttplug.ConnectionState;
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
import java.util.Locale;

/**
 * The Minegasm entry screen: a hub where every integration is a peer (ADR-018). Buttplug and each local
 * bridge appear as sibling rows, each opening its own screen; nothing is privileged. Global controls
 * (master output, emergency stop) and the shared screens (settings, packs, customization) live here
 * because they are not per-integration.
 */
public final class MinegasmHubScreen extends Screen {

    private final Screen parent;
    private final MinegasmClient client;
    private ConnectionState observedState;
    private long observedGeneration = -1;
    private boolean observedEnabled;

    public MinegasmHubScreen(Screen parent, MinegasmClient client) {
        super(Component.translatable("minegasm.title"));
        this.parent = parent;
        this.client = client;
    }

    @Override
    protected void init() {
        int w = Math.min(this.width - 16, 280);
        int x = (this.width - w) / 2;
        int h = 20;
        int gap = 24;
        int half = (w - 4) / 2;

        boolean enabled = client.config().enabled();
        boolean panic = !client.runtime().worker().isOutputEnabled();

        addRenderableWidget(button(
                Component.translatable(enabled ? "minegasm.output.on" : "minegasm.output.off"),
                b -> toggleEnabled(), x, 44, half, h));
        addRenderableWidget(button(
                Component.translatable(panic ? "minegasm.safety.resume" : "minegasm.safety.stop"),
                b -> togglePanic(), x + half + 4, 44, half, h));

        // Integrations, all peers. Buttplug first, then each configured bridge.
        int y = 92;
        addRenderableWidget(button(buttplugLabel(), b -> openButtplug(), x, y, w, h));
        y += gap;

        List<HapticConfig.Bridge> bridges = client.config().raw().bridges();
        for (int i = 0; i < bridges.size(); i++) {
            final int index = i;
            addRenderableWidget(button(bridgeLabel(bridges.get(i)), b -> openBridge(index), x, y, w, h));
            y += gap;
        }
        addRenderableWidget(button(Component.translatable("minegasm.hub.add_bridge"),
                b -> openBridge(bridges.size()), x, y, w, h));
        y += gap + 6;

        addRenderableWidget(button(Component.translatable("minegasm.settings.button"),
                b -> openSettings(), x, y, half, h));
        addRenderableWidget(button(Component.translatable("minegasm.packs.button"),
                b -> openScenePacks(), x + half + 4, y, half, h));
        y += gap;
        addRenderableWidget(button(Component.translatable("minegasm.customization.button"),
                b -> openCustomization(), x, y, half, h));
        if (client.hasLegacyConfig()) {
            addRenderableWidget(button(Component.translatable("minegasm.legacy.button"),
                    b -> openLegacyImport(), x + half + 4, y, half, h));
        }

        addRenderableWidget(button(Component.translatable("gui.done"),
                b -> onClose(), x, this.height - 24, w, h));

        observedState = client.status().state();
        observedGeneration = client.provider().devices().generation();
        observedEnabled = enabled;
    }

    @Override
    public void tick() {
        super.tick();
        if (client.status().state() != observedState
                || client.provider().devices().generation() != observedGeneration
                || client.config().enabled() != observedEnabled) {
            rebuildWidgets();
        }
    }

    private Component buttplugLabel() {
        int devices = client.provider().devices().all().size();
        return Component.translatable("minegasm.hub.buttplug",
                Component.translatable("minegasm.connection.state."
                        + client.status().state().name().toLowerCase(Locale.ROOT)),
                devices);
    }

    private Component bridgeLabel(HapticConfig.Bridge b) {
        return Component.translatable("minegasm.hub.bridge", b.name(),
                Component.translatable(b.enabled() ? "options.on" : "options.off"));
    }

    private void toggleEnabled() {
        HapticConfig cfg = client.config().raw();
        var g = cfg.global();
        client.updateConfig(new HapticConfig(cfg.schemaVersion(), cfg.profile(),
                new HapticConfig.Global(!g.enabled(), g.intensity(), g.variation(),
                        g.fatigueProtection(), g.pauseBehavior(), g.stopOnWorldUnload(), g.panicKey(),
                        g.testMaxPercent(), g.testMaxDurationMs(),
                        g.unsafeTestMaxPercent(), g.unsafeTestMaxDurationMs()),
                cfg.buttplug(), cfg.events(), cfg.outputPolicy(), cfg.devices(),
                cfg.positionCalibrations(), cfg.accumulation(), cfg.customIntensity(), cfg.bridges()));
        rebuildWidgets();
    }

    private void togglePanic() {
        if (client.runtime().worker().isOutputEnabled()) {
            client.panic();
        } else {
            client.clearPanic();
        }
        rebuildWidgets();
    }

    private void openButtplug() {
        setScreen(new MinegasmConfigScreen(this, client));
    }

    private void openBridge(int index) {
        setScreen(new MinegasmBridgeEditScreen(this, client, index));
    }

    private void openSettings() {
        setScreen(new MinegasmSettingsScreen(this, client));
    }

    private void openScenePacks() {
        setScreen(new MinegasmScenePackScreen(this, client));
    }

    private void openCustomization() {
        setScreen(new MinegasmCustomizationScreen(this, client));
    }

    private void openLegacyImport() {
        setScreen(new LegacyImportScreen(this, client));
    }

    private void setScreen(Screen screen) {
        //? if >=26.2 {
        this.minecraft.gui.setScreen(screen);
        //?} else {
        /*this.minecraft.setScreen(screen);
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
        graphics.centeredText(this.font, Component.translatable("minegasm.hub.subtitle"),
                this.width / 2, 31, 0xFFA0A0A0);
        graphics.centeredText(this.font, Component.translatable("minegasm.hub.integrations"),
                this.width / 2, 78, 0xFFFFFFFF);
    }
    //?} elif >=1.20.1 {
    /*@Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        //? if <1.21.1 {
        /^this.renderBackground(graphics);
        ^///?}
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFFFF);
        graphics.drawCenteredString(this.font, Component.translatable("minegasm.hub.subtitle"),
                this.width / 2, 31, 0xFFA0A0A0);
        graphics.drawCenteredString(this.font, Component.translatable("minegasm.hub.integrations"),
                this.width / 2, 78, 0xFFFFFFFF);
    }
    *///?} else {
    /*@Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(poseStack);
        super.render(poseStack, mouseX, mouseY, partialTick);
        GuiComponent.drawCenteredString(poseStack, this.font, this.title, this.width / 2, 20, 0xFFFFFFFF);
        GuiComponent.drawCenteredString(poseStack, this.font,
                Component.translatable("minegasm.hub.subtitle"), this.width / 2, 31, 0xFFA0A0A0);
        GuiComponent.drawCenteredString(poseStack, this.font,
                Component.translatable("minegasm.hub.integrations"), this.width / 2, 78, 0xFFFFFFFF);
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
