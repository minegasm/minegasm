package net.minegasm.neoforge;

import net.minegasm.buttplug.ConnectionState;
import net.minegasm.buttplug.ProviderStatus;
import net.minegasm.client.MinegasmClient;
import net.minegasm.config.HapticConfig;

//? if >=26.1.2 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * A compact in-game control screen (brief §11.2): master enable, connect/disconnect, scan, a status
 * line, and an always-visible stop. It is intentionally small — the full device/gameplay/calibration
 * screens are follow-up work (Epic 6); this covers the MVP-critical controls and never mutates engine
 * state directly, only through {@link MinegasmClient#updateConfig} (brief §5.1). Validate the
 * Screen/Button API against the pinned 26.x build.
 */
public final class MinegasmConfigScreen extends Screen {

    private final Screen parent;
    private final MinegasmClient client;
    private boolean adapterRestartRequired;
    private long observedRegistryGeneration = -1;
    private ConnectionState observedConnectionState;
    private boolean observedEnabled;
    private int observedErrorCount;

    public MinegasmConfigScreen(Screen parent, MinegasmClient client) {
        super(Component.translatable("minegasm.title"));
        this.parent = parent;
        this.client = client;
    }

    @Override
    protected void init() {
        int totalWidth = Math.min(this.width - 16, 420);
        int columnGap = 8;
        int columnWidth = (totalWidth - columnGap) / 2;
        int leftX = (this.width - totalWidth) / 2;
        int rightX = leftX + columnWidth + columnGap;
        int y = 42;
        int h = 20;
        int gap = 24;
        int half = (columnWidth - 4) / 2;

        boolean enabled = client.config().enabled();
        var devices = client.provider().devices().all();
        var errors = client.errorHistory();
        addRenderableWidget(new DeviceListWidget(this.minecraft, rightX, 52,
                columnWidth, 64, devices));
        addRenderableWidget(new ErrorListWidget(this.minecraft, rightX, 132,
                columnWidth, Math.max(28, this.height - 144), errors));
        Button clearErrors = addRenderableWidget(Button.builder(
                Component.translatable("minegasm.errors.clear"), b -> {
                    client.clearErrorHistory();
                    rebuildWidgets();
                }).bounds(rightX + columnWidth - 44, 117, 44, 14).build());
        clearErrors.active = !errors.isEmpty();

        addRenderableWidget(Button.builder(
                Component.translatable(enabled ? "minegasm.output.on" : "minegasm.output.off"),
                b -> toggleEnabled()).bounds(leftX, y, columnWidth, h).build());
        y += gap;

        String adapter = client.config().raw().buttplug().client();
        addRenderableWidget(Button.builder(Component.translatable(
                        adapterRestartRequired ? "minegasm.adapter.next_short" : "minegasm.adapter.short",
                        Component.translatable("minegasm.adapter." + adapter.toLowerCase(Locale.ROOT))),
                b -> toggleAdapter()).bounds(leftX, y, columnWidth, h).build());
        y += gap;

        ProviderStatus status = client.status();
        int deviceCount = devices.size();
        ConnectionState state = status.state();
        boolean connected = state != ConnectionState.DISCONNECTED;
        boolean busy = state == ConnectionState.CONNECTING || state == ConnectionState.NEGOTIATING
                || state == ConnectionState.STOPPING;

        Button connection = addRenderableWidget(Button.builder(Component.translatable(connected
                        ? "minegasm.connection.disconnect" : "minegasm.connection.connect"),
                b -> toggleConnection()).bounds(leftX, y, columnWidth, h).build());
        connection.active = !busy;
        y += gap;

        boolean scanning = state == ConnectionState.SCANNING;
        Button scan = addRenderableWidget(Button.builder(
                Component.translatable(scanning
                        ? "minegasm.connection.stop_scan" : "minegasm.connection.scan"),
                b -> toggleScanning()).bounds(leftX, y, half, h).build());
        scan.active = connected && !busy;

        Button refresh = addRenderableWidget(Button.builder(
                Component.translatable("minegasm.devices.refresh"),
                b -> refreshDevices()).bounds(leftX + half + 4, y, half, h).build());
        refresh.active = connected && !busy;
        y += gap;

        boolean panic = !client.runtime().worker().isOutputEnabled();
        Button test = addRenderableWidget(Button.builder(
                Component.translatable("minegasm.devices.test_output"),
                b -> client.testPulse(0.25f)).bounds(leftX, y, half, h).build());
        test.active = enabled && connected && deviceCount > 0 && !panic;

        addRenderableWidget(Button.builder(
                Component.translatable(panic
                        ? "minegasm.safety.resume" : "minegasm.safety.stop"),
                b -> togglePanic()).bounds(leftX + half + 4, y, half, h).build());
        y += gap;

        addRenderableWidget(Button.builder(Component.translatable("minegasm.settings.button"),
                b -> openSettings()).bounds(leftX, y, columnWidth, h).build());
        y += gap;

        if (client.hasLegacyConfig()) {
            addRenderableWidget(Button.builder(Component.translatable("minegasm.legacy.button"),
                    b -> openLegacyImport()).bounds(leftX, y, columnWidth, h).build());
        }

        addRenderableWidget(Button.builder(
                Component.translatable("gui.done"),
                b -> onClose()).bounds(leftX, this.height - 24, columnWidth, h).build());

        observedRegistryGeneration = client.provider().devices().generation();
        observedConnectionState = state;
        observedEnabled = enabled;
        observedErrorCount = errors.size();
    }

    @Override
    public void tick() {
        super.tick();
        var snapshot = client.provider().devices();
        if (snapshot.generation() != observedRegistryGeneration
                || client.status().state() != observedConnectionState
                || client.config().enabled() != observedEnabled
                || client.errorHistory().size() != observedErrorCount) {
            rebuildWidgets();
        }
    }

    private void toggleEnabled() {
        HapticConfig cfg = client.config().raw();
        var g = cfg.global();
        var updated = new HapticConfig(cfg.schemaVersion(), cfg.identity(),
                new HapticConfig.Global(!g.enabled(), g.intensity(), g.variation(),
                        g.fatigueProtection(), g.pauseBehavior(), g.stopOnWorldUnload(), g.panicKey(),
                        g.testMaxPercent(), g.testMaxDurationMs(),
                        g.unsafeTestMaxPercent(), g.unsafeTestMaxDurationMs()),
                cfg.buttplug(), cfg.events(), cfg.outputPolicy(), cfg.devices(),
                cfg.positionCalibrations(), cfg.accumulation(), cfg.customIntensity());
        client.updateConfig(updated);
        rebuildWidgets();
    }

    private void toggleConnection() {
        if (client.isConnected()) {
            client.disconnect();
        } else {
            client.connect().whenComplete((result, error) -> refreshAfterAsyncAction());
        }
        rebuildWidgets();
    }

    private void toggleAdapter() {
        HapticConfig cfg = client.config().raw();
        var bp = cfg.buttplug();
        String adapter = "native".equalsIgnoreCase(bp.client()) ? "buttplug4j" : "native";
        var updated = new HapticConfig(cfg.schemaVersion(), cfg.identity(), cfg.global(),
                new HapticConfig.Buttplug(bp.serverUrl(), bp.autoConnect(), bp.autoScan(),
                        bp.allowRemoteServer(), bp.reconnect(), adapter),
                cfg.events(), cfg.outputPolicy(), cfg.devices(), cfg.positionCalibrations(),
                cfg.accumulation(), cfg.customIntensity());
        client.updateConfig(updated);
        adapterRestartRequired = true;
        rebuildWidgets();
    }

    private void openLegacyImport() {
        //? if >=26.2 {
        this.minecraft.gui.setScreen(new LegacyImportScreen(this, client));
        //?} else {
        /*this.minecraft.setScreen(new LegacyImportScreen(this, client));
        *///?}
    }

    private void openSettings() {
        //? if >=26.2 {
        this.minecraft.gui.setScreen(new MinegasmSettingsScreen(this, client));
        //?} else {
        /*this.minecraft.setScreen(new MinegasmSettingsScreen(this, client));
        *///?}
    }

    private void toggleScanning() {
        var operation = client.status().state() == ConnectionState.SCANNING
                ? client.stopScanning() : client.startScanning();
        operation.whenComplete((result, error) -> refreshAfterAsyncAction());
        rebuildWidgets();
    }

    private void refreshDevices() {
        client.refreshDevices().whenComplete((result, error) -> refreshAfterAsyncAction());
    }

    private void togglePanic() {
        if (client.runtime().worker().isOutputEnabled()) {
            client.panic();
        } else {
            client.clearPanic();
        }
        rebuildWidgets();
    }

    private void refreshAfterAsyncAction() {
        if (this.minecraft != null) {
            this.minecraft.execute(() -> rebuildWidgets());
        }
    }

    //? if >=26.1.2 {
    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                   float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(this.font, this.title, this.width / 2, 20, 0xFFFFFFFF);

        ProviderStatus status = client.status();
        Component state = Component.translatable("minegasm.connection.state",
                Component.translatable("minegasm.connection.state."
                        + status.state().name().toLowerCase(Locale.ROOT)));
        graphics.centeredText(this.font, state, this.width / 2, 31, 0xFFA0A0A0);

        int totalWidth = Math.min(this.width - 16, 420);
        int columnWidth = (totalWidth - 8) / 2;
        int rightX = (this.width - totalWidth) / 2 + columnWidth + 8;
        int rightCenter = rightX + columnWidth / 2;
        graphics.centeredText(this.font,
                Component.translatable("minegasm.devices.heading",
                        client.provider().devices().all().size()),
                rightCenter, 42, 0xFFFFFFFF);
        graphics.text(this.font,
                Component.translatable("minegasm.errors.heading", client.errorHistory().size()),
                rightX, 120, 0xFFFFFFFF);

    }
    //?} else {
    /*@Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        //? if <1.21.1 {
        /^this.renderBackground(graphics); // 1.20.1 Screen.render() paints no backdrop (1.21.1+'s does)
        ^///?}
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFFFF);

        ProviderStatus status = client.status();
        Component state = Component.translatable("minegasm.connection.state",
                Component.translatable("minegasm.connection.state."
                        + status.state().name().toLowerCase(Locale.ROOT)));
        graphics.drawCenteredString(this.font, state, this.width / 2, 31, 0xFFA0A0A0);

        int totalWidth = Math.min(this.width - 16, 420);
        int columnWidth = (totalWidth - 8) / 2;
        int rightX = (this.width - totalWidth) / 2 + columnWidth + 8;
        int rightCenter = rightX + columnWidth / 2;
        graphics.drawCenteredString(this.font,
                Component.translatable("minegasm.devices.heading",
                        client.provider().devices().all().size()),
                rightCenter, 42, 0xFFFFFFFF);
        graphics.drawString(this.font,
                Component.translatable("minegasm.errors.heading", client.errorHistory().size()),
                rightX, 120, 0xFFFFFFFF);

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
