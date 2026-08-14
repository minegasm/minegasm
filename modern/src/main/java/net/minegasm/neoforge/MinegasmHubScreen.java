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
    private int observedBridgeConn;
    private int observedSafety = -1;
    private RowScroller scroller;

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

        addRenderableWidget(button(
                Component.translatable(enabled ? "minegasm.output.on" : "minegasm.output.off"),
                b -> toggleEnabled(), x, 44, half, h));
        // Stop only when running; Resume only for a user panic. A watchdog stop shows its own state and is
        // not resumable by this button, so a resume can never clear a live watchdog stall (review P1-2).
        net.minegasm.runtime.OutputStatus output = client.outputStatus();
        if (output.userResumable()) {
            addRenderableWidget(button(Component.translatable("minegasm.safety.resume"),
                    b -> resumeOutput(), x + half + 4, 44, half, h));
        } else if (!output.permitted()) {
            Button wd = addRenderableWidget(button(Component.translatable("minegasm.safety.watchdog"),
                    b -> {}, x + half + 4, 44, half, h));
            wd.active = false;
        } else {
            addRenderableWidget(button(Component.translatable("minegasm.safety.stop"),
                    b -> stopOutput(), x + half + 4, 44, half, h));
        }

        // Fixed bottom block (add-bridge, utilities, done), anchored to the screen bottom so it never
        // scrolls away no matter how many bridges are configured.
        int doneY = this.height - 24;
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
            scroller.resize(visibleRows, rowCount); // resize (not recreate) so a live rebuild keeps position
        }
        boolean needsScroll = rowCount > visibleRows;
        int rowW = needsScroll ? w - 20 : w;

        for (int i = 0; i < rowCount; i++) {
            if (!scroller.isVisible(i)) {
                continue;
            }
            int ry = viewportTop + (i - scroller.first()) * gap;
            if (i == 0) {
                addRenderableWidget(button(buttplugLabel(), b -> openButtplug(), x, ry, rowW, h));
            } else {
                final int index = i - 1;
                addRenderableWidget(button(bridgeLabel(bridges.get(index)),
                        b -> openBridge(index), x, ry, rowW, h));
            }
        }
        if (needsScroll) {
            int scrollX = x + w - 20;
            Button up = addRenderableWidget(button(Component.literal("^"), b -> {
                scroller.up();
                rebuildWidgets();
            }, scrollX, viewportTop, 20, h));
            up.active = scroller.canScrollUp();
            Button down = addRenderableWidget(button(Component.literal("v"), b -> {
                scroller.down();
                rebuildWidgets();
            }, scrollX, viewportBottom - 20, 20, h));
            down.active = scroller.canScrollDown();
        }

        addRenderableWidget(button(Component.translatable("minegasm.hub.add_bridge"),
                b -> openBridge(bridges.size()), x, addY, w, h));

        addRenderableWidget(button(Component.translatable("minegasm.settings.button"),
                b -> openSettings(), x, utilY, half, h));
        addRenderableWidget(button(Component.translatable("minegasm.packs.button"),
                b -> openScenePacks(), x + half + 4, utilY, half, h));
        addRenderableWidget(button(Component.translatable("minegasm.customization.button"),
                b -> openCustomization(), x, customY, half, h));
        if (client.hasLegacyConfig()) {
            addRenderableWidget(button(Component.translatable("minegasm.legacy.button"),
                    b -> openLegacyImport(), x + half + 4, customY, half, h));
        }

        addRenderableWidget(button(Component.translatable("gui.done"), b -> onClose(), x, doneY, w, h));

        observedState = client.status().state();
        observedGeneration = client.provider().devices().generation();
        observedEnabled = enabled;
        observedBridgeConn = bridgeConnMask();
        observedSafety = safetyCode();
    }

    @Override
    public void tick() {
        super.tick();
        if (client.status().state() != observedState
                || client.provider().devices().generation() != observedGeneration
                || client.config().enabled() != observedEnabled
                || bridgeConnMask() != observedBridgeConn
                || safetyCode() != observedSafety) {
            rebuildWidgets();
        }
    }

    /** A rolling hash of each bridge's chain state, so the rows refresh as links come up or drop. */
    private int bridgeConnMask() {
        int hash = 1;
        for (HapticConfig.Bridge b : client.config().raw().bridges()) {
            int state = !b.enabled() ? 0
                    : client.bridgeFaulted(b.name()) ? 1
                    : !client.bridgeConnected(b.name()) ? 2
                    : 3 + client.bridgeDownstream(b.name()).ordinal();
            hash = hash * 31 + state;
        }
        return hash;
    }

    private Component buttplugLabel() {
        if (client.buttplugFaulted()) {
            net.minegasm.backend.BackendOutcome failure = client.buttplugFailure();
            return Component.literal("Buttplug: FAULT"
                    + (failure == null ? "" : " (" + failure + ")"));
        }
        int devices = client.provider().devices().all().size();
        return Component.translatable("minegasm.hub.buttplug",
                Component.translatable("minegasm.connection.state."
                        + client.status().state().name().toLowerCase(Locale.ROOT)),
                devices);
    }

    private Component bridgeLabel(HapticConfig.Bridge b) {
        String stateKey;
        if (!b.enabled()) {
            stateKey = "minegasm.hub.bridge_off";
        } else if (client.bridgeFaulted(b.name())) {
            net.minegasm.backend.BackendOutcome failure = client.bridgeFailure(b.name());
            return Component.literal("Bridge " + b.name() + ": FAULT"
                    + (failure == null ? "" : " (" + failure + ")"));
        } else if (!client.bridgeConnected(b.name())) {
            stateKey = "minegasm.hub.bridge_waiting";
        } else {
            switch (client.bridgeDownstream(b.name())) {
                case READY:
                    stateKey = "minegasm.hub.bridge_ready";
                    break;
                case UNAVAILABLE:
                    stateKey = "minegasm.hub.bridge_downstream_off";
                    break;
                default:
                    stateKey = "minegasm.hub.bridge_connected";
            }
        }
        return Component.translatable("minegasm.hub.bridge", b.name(), Component.translatable(stateKey));
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

    private void stopOutput() {
        client.panic();
        rebuildWidgets();
    }

    private void resumeOutput() {
        client.clearPanic(); // clears only the user-stop cause
        rebuildWidgets();
    }

    /** A code for the safety state so the tick can rebuild the button when it changes. */
    private int safetyCode() {
        net.minegasm.runtime.OutputStatus output = client.outputStatus();
        return (output.userStopped() ? 1 : 0)
                | (output.watchdogStopped() ? 2 : 0)
                | (output.disabled() ? 8 : 0)
                | (client.buttplugFaulted() ? 4 : 0);
    }

    // Wheel scrolls the integration list, page-at-a-time via the scroller. The 4-arg overload with a
    // horizontal scrollX component was added in 1.21.1; 1.19.2 and 1.20.1 take a single delta.
    //? if >=1.21.1 {
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scroller != null && scrollY > 0 && scroller.canScrollUp()) {
            scroller.up();
            rebuildWidgets();
            return true;
        }
        if (scroller != null && scrollY < 0 && scroller.canScrollDown()) {
            scroller.down();
            rebuildWidgets();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
    //?} else {
    /*@Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (scroller != null && delta > 0 && scroller.canScrollUp()) {
            scroller.up();
            rebuildWidgets();
            return true;
        }
        if (scroller != null && delta < 0 && scroller.canScrollDown()) {
            scroller.down();
            rebuildWidgets();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }
    *///?}

    /** Whether the banner warns output is stopped: a user panic or watchdog stall, not a chosen disable. */
    private boolean outputStopped() {
        return client.outputStatus().bannerStopped();
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
        if (outputStopped()) {
            graphics.centeredText(this.font,
                    Component.translatable("minegasm.hub.stopped", client.outputStatus().blockedReason()),
                    this.width / 2, 66, 0xFFFF5555);
        }
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
        if (outputStopped()) {
            graphics.drawCenteredString(this.font,
                    Component.translatable("minegasm.hub.stopped", client.outputStatus().blockedReason()),
                    this.width / 2, 66, 0xFFFF5555);
        }
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
        if (outputStopped()) {
            GuiComponent.drawCenteredString(poseStack, this.font,
                    Component.translatable("minegasm.hub.stopped", client.outputStatus().blockedReason()),
                    this.width / 2, 66, 0xFFFF5555);
        }
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
