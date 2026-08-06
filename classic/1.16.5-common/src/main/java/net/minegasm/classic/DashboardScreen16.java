package net.minegasm.classic;

import net.minegasm.buttplug.ConnectionState;
import net.minegasm.buttplug.ProviderStatus;
import net.minegasm.client.MinegasmClient;
import net.minegasm.config.HapticConfig;
import net.minegasm.device.HapticDevice;

import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.TextComponent;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.vertex.PoseStack;

import org.lwjgl.opengl.GL11;

import java.util.List;
import java.util.Locale;

/**
 * The main in-game control screen for Minecraft 1.16.5, shared by the Forge and Fabric subprojects. It
 * mirrors the modern dashboard: master enable, connect/disconnect, scan/refresh, test, an always-visible
 * stop/resume, a live device list and provider-error history on the right, and buttons through to the
 * settings sub-screen and the legacy import. Actions run live against the shared {@link MinegasmClient};
 * the deferred knobs live on {@link SettingsScreen16}. The adapter toggle switches the Buttplug backend
 * between buttplug4j and the native WebSocket provider (ADR-019), the same control the modern dashboard
 * has.
 */
public final class DashboardScreen16 extends Screen {

    private final Screen parent;
    private final MinegasmClient client;

    private DeviceList16 deviceList;
    private ErrorList16 errorList;
    private int deviceHeadingX;
    private int errorHeadingX;
    private int errorHeadingY;
    private int listX;
    private int listWidth;
    private int deviceTop;
    private int deviceHeight;
    private int errorTop;
    private int errorHeight;

    private long observedGeneration = -1;
    private ConnectionState observedState;
    private boolean observedEnabled;
    private int observedErrorCount;

    public DashboardScreen16(Screen parent) {
        super(new TextComponent("Minegasm"));
        this.parent = parent;
        this.client = ClassicClientHolder.get();
    }

    @Override
    protected void init() {
        int totalWidth = Math.min(width - 16, 420);
        int columnGap = 8;
        int columnWidth = (totalWidth - columnGap) / 2;
        int leftX = (width - totalWidth) / 2;
        int rightX = leftX + columnWidth + columnGap;
        int half = (columnWidth - 4) / 2;
        int h = 20;
        int gap = 24;
        int y = 42;

        boolean enabled = client.config().enabled();
        List<HapticDevice> devices = client.provider().devices().all();
        List<String> errors = client.errorHistory();

        listX = rightX;
        listWidth = columnWidth;
        deviceTop = 52;
        deviceHeight = 64;
        errorTop = 132;
        errorHeight = Math.max(28, height - 144);
        deviceList = new DeviceList16(minecraft, rightX, deviceTop, columnWidth, deviceHeight, devices);
        errorList = new ErrorList16(minecraft, rightX, errorTop, columnWidth, errorHeight, errors);
        children.add(deviceList);
        children.add(errorList);
        deviceHeadingX = rightX + columnWidth / 2;
        errorHeadingX = rightX;
        errorHeadingY = 120;

        Button clearErrors = addButton(new Button(rightX + columnWidth - 44, 117, 44, 14,
                new TextComponent("Clear"), b -> {
            client.clearErrorHistory();
            rebuild();
        }));
        clearErrors.active = !errors.isEmpty();

        // Left column: live controls.
        addButton(new Button(leftX, y, columnWidth, h,
                new TextComponent("Haptics: " + onOff(enabled)), b -> toggleEnabled()));
        y += gap;
        addButton(new Button(leftX, y, columnWidth, h,
                new TextComponent(adapterLabel()), b -> toggleAdapter()));
        y += gap;

        ProviderStatus status = client.status();
        ConnectionState state = status.state();
        boolean connected = state != ConnectionState.DISCONNECTED;
        boolean busy = state == ConnectionState.CONNECTING || state == ConnectionState.NEGOTIATING
                || state == ConnectionState.STOPPING;

        Button connection = addButton(new Button(leftX, y, columnWidth, h,
                new TextComponent(connected ? "Disconnect" : "Connect"), b -> toggleConnection()));
        connection.active = !busy;
        y += gap;

        boolean scanning = state == ConnectionState.SCANNING;
        Button scan = addButton(new Button(leftX, y, half, h,
                new TextComponent(scanning ? "Stop scan" : "Scan"), b -> toggleScanning()));
        scan.active = connected && !busy;
        Button refresh = addButton(new Button(leftX + half + 4, y, half, h,
                new TextComponent("Refresh"), b -> refreshDevices()));
        refresh.active = connected && !busy;
        y += gap;

        boolean panic = !client.runtime().worker().isOutputEnabled();
        Button test = addButton(new Button(leftX, y, half, h,
                new TextComponent("Test"), b -> client.testPulse(0.25f)));
        test.active = enabled && connected && devices.size() > 0 && !panic;
        addButton(new Button(leftX + half + 4, y, half, h,
                new TextComponent(panic ? "Resume" : "Stop"), b -> togglePanic()));
        y += gap;

        addButton(new Button(leftX, y, half, h,
                new TextComponent("Settings..."), b -> openSettings()));
        addButton(new Button(leftX + half + 4, y, half, h,
                new TextComponent("Scene packs..."), b -> openScenePacks()));
        y += gap;

        addButton(new Button(leftX, y, half, h,
                new TextComponent("Customization..."), b -> openCustomization()));
        addButton(new Button(leftX + half + 4, y, half, h,
                new TextComponent("Device editor..."), b -> openDeviceEditor()));
        y += gap;

        if (client.hasLegacyConfig()) {
            addButton(new Button(leftX, y, columnWidth, h,
                    new TextComponent("Import legacy config..."), b -> openLegacyImport()));
        }

        addButton(new Button(leftX, height - 24, columnWidth, h,
                new TextComponent("Done"), b -> onClose()));

        observedGeneration = client.provider().devices().generation();
        observedState = state;
        observedEnabled = enabled;
        observedErrorCount = errors.size();
    }

    @Override
    public void tick() {
        if (client.provider().devices().generation() != observedGeneration
                || client.status().state() != observedState
                || client.config().enabled() != observedEnabled
                || client.errorHistory().size() != observedErrorCount) {
            rebuild();
        }
    }

    @Override
    public void render(PoseStack pose, int mouseX, int mouseY, float partialTick) {
        renderBackground(pose);
        // These lists are placed manually with their background/overlays off, so nothing masks rows that
        // exceed the panel (a long error history would otherwise bleed up over the heading and buttons).
        // Hard-clip each to its panel with a GL scissor, which vanilla lists get from the overlays.
        withScissor(listX, deviceTop, listWidth, deviceHeight,
                () -> deviceList.render(pose, mouseX, mouseY, partialTick));
        withScissor(listX, errorTop, listWidth, errorHeight,
                () -> errorList.render(pose, mouseX, mouseY, partialTick));
        super.render(pose, mouseX, mouseY, partialTick);

        GuiComponent.drawCenteredString(pose, font, title, width / 2, 12, 0xFFFFFF);
        ProviderStatus status = client.status();
        String state = status.state().name().toLowerCase(Locale.ROOT);
        GuiComponent.drawCenteredString(pose, font, new TextComponent("State: " + state),
                width / 2, 26, 0xA0A0A0);
        GuiComponent.drawCenteredString(pose, font,
                new TextComponent("Devices (" + client.provider().devices().all().size() + ")"),
                deviceHeadingX, 42, 0xFFFFFF);
        GuiComponent.drawString(pose, font,
                new TextComponent("Errors (" + client.errorHistory().size() + ")"),
                errorHeadingX, errorHeadingY, 0xFFFFFF);
    }

    /**
     * Run {@code render} with a GL scissor box locked to the given screen-space rectangle, so anything it
     * draws outside those bounds is clipped. Scissor coordinates are framebuffer pixels with the origin at
     * the bottom-left, so the rect is scaled by the GUI factor and its Y flipped.
     */
    private void withScissor(int x, int y, int w, int h, Runnable render) {
        Window window = minecraft.getWindow();
        double scale = window.getGuiScale();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor((int) (x * scale),
                (int) (window.getHeight() - (y + h) * scale),
                (int) (w * scale), (int) (h * scale));
        try {
            render.run();
        } finally {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
        }
    }

    private void rebuild() {
        buttons.clear();
        children.clear();
        init();
    }

    private void toggleEnabled() {
        HapticConfig cfg = client.config().raw();
        HapticConfig.Global g = cfg.global();
        HapticConfig updated = new HapticConfig(cfg.schemaVersion(), cfg.profile(),
                new HapticConfig.Global(!g.enabled(), g.intensity(), g.variation(),
                        g.fatigueProtection(), g.pauseBehavior(), g.stopOnWorldUnload(), g.panicKey(),
                        g.testMaxPercent(), g.testMaxDurationMs(),
                        g.unsafeTestMaxPercent(), g.unsafeTestMaxDurationMs()),
                cfg.buttplug(), cfg.events(), cfg.outputPolicy(), cfg.devices(),
                cfg.positionCalibrations(), cfg.accumulation(), cfg.customIntensity(), cfg.bridges());
        client.updateConfig(updated);
        rebuild();
    }

    /** Flip the Buttplug backend between buttplug4j and native, preserving everything else. The change
     *  takes effect on the next launch, so the button shows a restart hint once toggled. */
    private void toggleAdapter() {
        // Swap the backend live (no restart): the client stops the old one and connects the new.
        String next = "native".equalsIgnoreCase(client.backend()) ? "buttplug4j" : "native";
        client.setBackend(next);
        rebuild();
    }

    private String adapterLabel() {
        return "Adapter: " + ("native".equalsIgnoreCase(client.backend()) ? "native" : "buttplug4j");
    }

    private void toggleConnection() {
        if (client.isConnected()) {
            client.disconnect();
        } else {
            client.connect().whenComplete((result, error) -> refreshAfterAsync());
        }
        rebuild();
    }

    private void toggleScanning() {
        boolean scanning = client.status().state() == ConnectionState.SCANNING;
        (scanning ? client.stopScanning() : client.startScanning())
                .whenComplete((result, error) -> refreshAfterAsync());
        rebuild();
    }

    private void refreshDevices() {
        client.refreshDevices().whenComplete((result, error) -> refreshAfterAsync());
    }

    private void togglePanic() {
        if (client.runtime().worker().isOutputEnabled()) {
            client.panic();
        } else {
            client.clearPanic();
        }
        rebuild();
    }

    private void refreshAfterAsync() {
        if (minecraft != null) {
            minecraft.execute(this::rebuild);
        }
    }

    private void openSettings() {
        minecraft.setScreen(new SettingsScreen16(this, client));
    }

    private void openScenePacks() {
        minecraft.setScreen(new ScenePackScreen16(this, client));
    }

    private void openCustomization() {
        minecraft.setScreen(new CustomizationScreen16(this, client));
    }

    private void openDeviceEditor() {
        minecraft.setScreen(new DeviceEditorScreen16(this, client));
    }

    private void openLegacyImport() {
        minecraft.setScreen(new LegacyImportScreen16(this, client));
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    private static String onOff(boolean value) {
        return value ? "ON" : "OFF";
    }
}
