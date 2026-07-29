package net.minegasm.classic;

import net.minegasm.buttplug.ProviderStatus;
import net.minegasm.client.MinegasmClient;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;

import com.mojang.blaze3d.vertex.PoseStack;

import java.util.Locale;
import java.util.function.DoubleConsumer;

/**
 * The in-game settings screen for Minecraft 1.16.5, shared by the Forge and Fabric subprojects (it uses
 * only vanilla widgets). It edits the shared, Minecraft-free {@link ClassicConfigModel} and exposes the
 * same settings the modern screen does (enable, intensity, variation, recipe pack, mode, fatigue, pause,
 * auto-connect/scan, allow-remote, stop-on-exit, server URL), with a status line and connect/test.
 * Reached from Mod Menu on Fabric and the mods-list config button on Forge; both construct it with the
 * parent screen. Edits apply through {@link MinegasmClient#updateConfig} on close.
 */
public final class ConfigScreen16 extends Screen {

    private final Screen parent;
    private final MinegasmClient client;
    private final ClassicConfigModel model;

    private EditBox serverField;
    private Button connectBtn;
    private Button panicBtn;
    private Button testBtn;
    private int serverLabelX;
    private int serverLabelY;

    public ConfigScreen16(Screen parent) {
        super(new TextComponent("Minegasm"));
        this.parent = parent;
        this.client = ClassicClientHolder.get();
        this.model = new ClassicConfigModel(client.config().raw());
    }

    @Override
    protected void init() {
        int lx = width / 2 - 155;
        int rx = width / 2 + 5;
        int y0 = 40;
        int dy = 22;

        // Left column: gameplay. Every change applies immediately (applyNow), like the modern screen,
        // so Test/Connect read the current config rather than a stale one.
        addButton(new Button(lx, y0, 150, 20, enabledLabel(), b -> {
            model.enabled = !model.enabled;
            b.setMessage(enabledLabel());
            applyNow();
        }));
        addButton(new PctSlider(lx, y0 + dy, 150, 20, "Intensity: ", model.intensity, v -> model.intensity = v));
        addButton(new PctSlider(lx, y0 + 2 * dy, 150, 20, "Variation: ", model.variation, v -> model.variation = v));
        addButton(new Button(lx, y0 + 3 * dy, 150, 20, recipeLabel(), b -> {
            model.toggleRecipePack();
            b.setMessage(recipeLabel());
            applyNow();
        }));
        addButton(new Button(lx, y0 + 4 * dy, 150, 20, modeLabel(), b -> {
            model.cycleMode();
            b.setMessage(modeLabel());
            applyNow();
        }));
        addButton(new Button(lx, y0 + 5 * dy, 150, 20, fatigueLabel(), b -> {
            model.fatigueProtection = !model.fatigueProtection;
            b.setMessage(fatigueLabel());
            applyNow();
        }));
        addButton(new Button(lx, y0 + 6 * dy, 150, 20, pauseLabel(), b -> {
            model.cyclePauseBehavior();
            b.setMessage(pauseLabel());
            applyNow();
        }));

        // Right column: connection.
        addButton(new Button(rx, y0, 150, 20, autoConnectLabel(), b -> {
            model.autoConnect = !model.autoConnect;
            b.setMessage(autoConnectLabel());
            applyNow();
        }));
        addButton(new Button(rx, y0 + dy, 150, 20, autoScanLabel(), b -> {
            model.autoScan = !model.autoScan;
            b.setMessage(autoScanLabel());
            applyNow();
        }));
        addButton(new Button(rx, y0 + 2 * dy, 150, 20, allowRemoteLabel(), b -> {
            model.allowRemote = !model.allowRemote;
            b.setMessage(allowRemoteLabel());
            applyNow();
        }));
        addButton(new Button(rx, y0 + 3 * dy, 150, 20, stopUnloadLabel(), b -> {
            model.stopOnWorldUnload = !model.stopOnWorldUnload;
            b.setMessage(stopUnloadLabel());
            applyNow();
        }));

        serverLabelX = rx;
        serverLabelY = y0 + 4 * dy;
        serverField = new EditBox(font, rx, y0 + 4 * dy + 10, 150, 18, new TextComponent("Server"));
        serverField.setMaxLength(256);
        serverField.setValue(model.serverUrl);
        addButton(serverField);

        int by = height - 28;
        connectBtn = addButton(new Button(lx, by, 74, 20, new TextComponent(connectLabel()), b -> {
            applyNow();
            if (client.isConnected()) {
                client.disconnect();
            } else {
                client.connect();
            }
            refreshActionButtons();
        }));
        panicBtn = addButton(new Button(lx + 78, by, 74, 20, new TextComponent(panicLabel()), b -> {
            if (client.runtime().worker().isOutputEnabled()) {
                client.panic();
            } else {
                client.clearPanic();
            }
            refreshActionButtons();
        }));
        testBtn = addButton(new Button(lx + 156, by, 74, 20, new TextComponent("Test"), b -> {
            applyNow();
            if (client.isConnected() && client.config().enabled()) {
                client.testPulse(0.5f, 400);
            }
        }));
        addButton(new Button(lx + 234, by, 74, 20, new TextComponent("Done"), b -> onClose()));
        refreshActionButtons();
    }

    @Override
    public void tick() {
        serverField.tick();
        refreshActionButtons(); // keep Connect/Stop labels and Test state in sync with async changes
    }

    @Override
    public void render(PoseStack pose, int mouseX, int mouseY, float partialTicks) {
        renderBackground(pose);
        int lx = width / 2 - 155;
        int rx = width / 2 + 5;
        GuiComponent.drawCenteredString(pose, font, "Minegasm", width / 2, 8, 0xFFFFFF);
        GuiComponent.drawCenteredString(pose, font, "Gameplay", lx + 75, 28, 0xC0C0C0);
        GuiComponent.drawCenteredString(pose, font, "Connection", rx + 75, 28, 0xC0C0C0);
        GuiComponent.drawString(pose, font, "Server:", serverLabelX, serverLabelY, 0xA0A0A0);
        super.render(pose, mouseX, mouseY, partialTicks);
        GuiComponent.drawCenteredString(pose, font, statusLine(), width / 2, height - 42, 0x80FF80);
    }

    @Override
    public void onClose() {
        applyNow();
        minecraft.setScreen(parent);
    }

    /**
     * Read the server field into the model (the sliders already write live) and persist the whole config
     * through the client, then refresh the action buttons. Called after every change so settings take
     * effect immediately; without this, Test would read the pre-edit config and quietly do nothing right
     * after the player enabled haptics.
     */
    private void applyNow() {
        model.serverUrl = serverField.getValue().trim();
        model.apply(client);
        refreshActionButtons();
    }

    /**
     * Keep the action bar in sync with live state: Connect/Disconnect and Stop/Resume reflect the
     * connection and panic state, and Test is greyed out whenever it cannot produce a pulse (disabled,
     * disconnected, no device, or panic-latched), so nothing silently does nothing.
     */
    private void refreshActionButtons() {
        boolean connected = client.isConnected();
        boolean panicked = !client.runtime().worker().isOutputEnabled();
        if (connectBtn != null) {
            connectBtn.setMessage(new TextComponent(connectLabel()));
        }
        if (panicBtn != null) {
            panicBtn.setMessage(new TextComponent(panicLabel()));
        }
        if (testBtn != null) {
            testBtn.active = client.config().enabled() && connected
                    && client.status().deviceCount() > 0 && !panicked;
        }
    }

    private String connectLabel() {
        return client.isConnected() ? "Disconnect" : "Connect";
    }

    private String panicLabel() {
        return client.runtime().worker().isOutputEnabled() ? "Stop" : "Resume";
    }

    // --- labels ----------------------------------------------------------------------------

    private Component enabledLabel() {
        return new TextComponent("Haptics: " + onOff(model.enabled));
    }

    private Component recipeLabel() {
        return new TextComponent("Recipe: " + capitalize(model.recipePack.name()));
    }

    private Component modeLabel() {
        return new TextComponent("Mode: " + capitalize(model.mode.name()));
    }

    private Component fatigueLabel() {
        return new TextComponent("Fatigue guard: " + onOff(model.fatigueProtection));
    }

    private Component pauseLabel() {
        return new TextComponent("Pause: " + capitalize(model.pauseBehavior.name()));
    }

    private Component autoConnectLabel() {
        return new TextComponent("Auto-connect: " + onOff(model.autoConnect));
    }

    private Component autoScanLabel() {
        return new TextComponent("Auto-scan: " + onOff(model.autoScan));
    }

    private Component allowRemoteLabel() {
        return new TextComponent("Allow remote: " + onOff(model.allowRemote));
    }

    private Component stopUnloadLabel() {
        return new TextComponent("Stop on exit: " + onOff(model.stopOnWorldUnload));
    }

    private String statusLine() {
        ProviderStatus status = client.status();
        return status.state().name().toLowerCase(Locale.ROOT) + " | " + status.deviceCount()
                + (status.deviceCount() == 1 ? " device" : " devices");
    }

    private static String onOff(boolean value) {
        return value ? "ON" : "OFF";
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        return s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1).toLowerCase(Locale.ROOT);
    }

    /** A 0..100% slider bound to a model field; applies live as it is dragged. */
    private final class PctSlider extends AbstractSliderButton {
        private final String prefix;
        private final DoubleConsumer setter;

        PctSlider(int x, int y, int w, int h, String prefix, double value, DoubleConsumer setter) {
            super(x, y, w, h, new TextComponent(""), value);
            this.prefix = prefix;
            this.setter = setter;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(new TextComponent(prefix + Math.round(value * 100) + "%"));
        }

        @Override
        protected void applyValue() {
            setter.accept(value);
        }
    }
}
