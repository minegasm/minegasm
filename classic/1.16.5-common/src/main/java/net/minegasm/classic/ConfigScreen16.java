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

        // Left column: gameplay.
        Button enabledBtn = addButton(new Button(lx, y0, 150, 20, enabledLabel(), b -> {
            model.enabled = !model.enabled;
            b.setMessage(enabledLabel());
        }));
        addButton(new PctSlider(lx, y0 + dy, 150, 20, "Intensity: ", model.intensity, v -> model.intensity = v));
        addButton(new PctSlider(lx, y0 + 2 * dy, 150, 20, "Variation: ", model.variation, v -> model.variation = v));
        addButton(new Button(lx, y0 + 3 * dy, 150, 20, recipeLabel(), b -> {
            model.toggleRecipePack();
            b.setMessage(recipeLabel());
        }));
        addButton(new Button(lx, y0 + 4 * dy, 150, 20, modeLabel(), b -> {
            model.cycleMode();
            b.setMessage(modeLabel());
        }));
        addButton(new Button(lx, y0 + 5 * dy, 150, 20, fatigueLabel(), b -> {
            model.fatigueProtection = !model.fatigueProtection;
            b.setMessage(fatigueLabel());
        }));
        addButton(new Button(lx, y0 + 6 * dy, 150, 20, pauseLabel(), b -> {
            model.cyclePauseBehavior();
            b.setMessage(pauseLabel());
        }));

        // Right column: connection.
        addButton(new Button(rx, y0, 150, 20, autoConnectLabel(), b -> {
            model.autoConnect = !model.autoConnect;
            b.setMessage(autoConnectLabel());
        }));
        addButton(new Button(rx, y0 + dy, 150, 20, autoScanLabel(), b -> {
            model.autoScan = !model.autoScan;
            b.setMessage(autoScanLabel());
        }));
        addButton(new Button(rx, y0 + 2 * dy, 150, 20, allowRemoteLabel(), b -> {
            model.allowRemote = !model.allowRemote;
            b.setMessage(allowRemoteLabel());
        }));
        addButton(new Button(rx, y0 + 3 * dy, 150, 20, stopUnloadLabel(), b -> {
            model.stopOnWorldUnload = !model.stopOnWorldUnload;
            b.setMessage(stopUnloadLabel());
        }));

        serverLabelX = rx;
        serverLabelY = y0 + 4 * dy;
        serverField = new EditBox(font, rx, y0 + 4 * dy + 10, 150, 18, new TextComponent("Server"));
        serverField.setMaxLength(256);
        serverField.setValue(model.serverUrl);
        addButton(serverField);

        addButton(new Button(rx, y0 + 6 * dy, 73, 20, new TextComponent("Connect"), b -> {
            if (!client.isConnected()) {
                client.connect();
            }
        }));
        addButton(new Button(rx + 77, y0 + 6 * dy, 73, 20, new TextComponent("Test"), b -> {
            if (client.isConnected() && client.config().enabled()) {
                client.testPulse(0.5f, 400);
            }
        }));

        addButton(new Button(width / 2 - 100, height - 28, 200, 20, new TextComponent("Done"),
                b -> onClose()));
    }

    @Override
    public void tick() {
        serverField.tick();
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
        model.serverUrl = serverField.getValue().trim();
        model.apply(client);
        minecraft.setScreen(parent);
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
