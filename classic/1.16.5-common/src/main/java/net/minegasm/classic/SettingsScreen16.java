package net.minegasm.classic;

import net.minegasm.client.MinegasmClient;

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
 * The gameplay/connection settings sub-screen for 1.16.5, reached from the dashboard's Settings button.
 * Mirrors the modern settings screen: two columns (gameplay left, connection right) of the knobs, with
 * Save/Cancel. Unlike the dashboard, edits are deferred; Cancel discards them and Save applies the whole
 * config through {@link MinegasmClient#updateConfig}. Shared by both loaders.
 */
public final class SettingsScreen16 extends Screen {

    private final Screen parent;
    private final MinegasmClient client;
    private final ClassicConfigModel model;

    private Button unsafeTestBtn;
    private EditBox serverField;
    private int leftHeaderX;
    private int rightHeaderX;

    public SettingsScreen16(Screen parent, MinegasmClient client) {
        super(new TextComponent("Minegasm settings"));
        this.parent = parent;
        this.client = client;
        this.model = new ClassicConfigModel(client.config().raw());
    }

    @Override
    protected void init() {
        // The pack screen writes the selection immediately, so re-read it here rather than trust a
        // snapshot taken when this screen opened; otherwise Save would revert a pack picked meanwhile.
        model.recipePack = client.config().raw().profile().recipePack();
        int lx = width / 2 - 155;
        int rx = width / 2 + 5;
        leftHeaderX = lx + 75;
        rightHeaderX = rx + 75;
        int y0 = 40;
        int dy = 22;

        // Left column: gameplay.
        addButton(new PctSlider(lx, y0, 150, 20, "Intensity: ", model.intensity, v -> model.intensity = v));
        addButton(new PctSlider(lx, y0 + dy, 150, 20, "Variation: ", model.variation, v -> model.variation = v));
        addButton(new Button(lx, y0 + 2 * dy, 150, 20, recipeLabel(), b -> {
            // Stage the server URL (only read on Save) before leaving, so returning from the pack
            // screen does not discard an unsaved edit; sliders and toggles already write live.
            model.serverUrl = serverField.getValue();
            openScenePacks();
        }));
        addButton(new Button(lx, y0 + 3 * dy, 150, 20, modeLabel(), b -> {
            model.cycleMode();
            b.setMessage(modeLabel());
        }));
        addButton(new Button(lx, y0 + 4 * dy, 150, 20, fatigueLabel(), b -> {
            model.fatigueProtection = !model.fatigueProtection;
            b.setMessage(fatigueLabel());
        }));
        addButton(new Button(lx, y0 + 5 * dy, 150, 20, pauseLabel(), b -> {
            model.cyclePauseBehavior();
            b.setMessage(pauseLabel());
        }));
        addButton(new Button(lx, y0 + 6 * dy, 150, 20, stopUnloadLabel(), b -> {
            model.stopOnWorldUnload = !model.stopOnWorldUnload;
            b.setMessage(stopUnloadLabel());
        }));

        // Right column: connection.
        serverField = new EditBox(font, rx, y0, 150, 18, new TextComponent("Server"));
        serverField.setMaxLength(256);
        serverField.setValue(model.serverUrl);
        addButton(serverField);
        addButton(new Button(rx, y0 + dy, 150, 20, autoConnectLabel(), b -> {
            model.autoConnect = !model.autoConnect;
            b.setMessage(autoConnectLabel());
        }));
        addButton(new Button(rx, y0 + 2 * dy, 150, 20, autoScanLabel(), b -> {
            model.autoScan = !model.autoScan;
            b.setMessage(autoScanLabel());
        }));
        addButton(new Button(rx, y0 + 3 * dy, 150, 20, allowRemoteLabel(), b -> {
            model.allowRemote = !model.allowRemote;
            b.setMessage(allowRemoteLabel());
        }));
        addButton(new Button(rx, y0 + 4 * dy, 150, 20, normalTestLabel(), b -> {
            model.cycleNormalTestLimit();
            b.setMessage(normalTestLabel());
            unsafeTestBtn.setMessage(unsafeTestLabel());
        }));
        unsafeTestBtn = addButton(new Button(rx, y0 + 5 * dy, 150, 20, unsafeTestLabel(), b -> {
            model.cycleUnsafeTestLimit();
            b.setMessage(unsafeTestLabel());
        }));

        addButton(new Button(width / 2 - 100, height - 26, 98, 20, new TextComponent("Save"), b -> {
            String url = serverField.getValue().trim();
            if (!ClassicConfigModel.isValidServerUrl(url)) {
                serverField.setTextColor(0xFF5555); // reject a non-ws(s) URL, like the modern screen
                return;
            }
            model.serverUrl = url;
            model.apply(client);
            onClose();
        }));
        addButton(new Button(width / 2 + 2, height - 26, 98, 20, new TextComponent("Cancel"),
                b -> onClose()));
    }

    @Override
    public void tick() {
        serverField.tick();
    }

    @Override
    public void render(PoseStack pose, int mouseX, int mouseY, float partialTicks) {
        renderBackground(pose);
        GuiComponent.drawCenteredString(pose, font, "Minegasm settings", width / 2, 8, 0xFFFFFF);
        GuiComponent.drawCenteredString(pose, font, "Gameplay", leftHeaderX, 28, 0xC0C0C0);
        GuiComponent.drawCenteredString(pose, font, "Connection", rightHeaderX, 28, 0xC0C0C0);
        super.render(pose, mouseX, mouseY, partialTicks);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    // --- labels ----------------------------------------------------------------------------

    private Component recipeLabel() {
        return new TextComponent("Recipe: " + capitalize(model.recipePack));
    }

    private void openScenePacks() {
        minecraft.setScreen(new ScenePackScreen16(this, client));
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

    private Component stopUnloadLabel() {
        return new TextComponent("Stop on exit: " + onOff(model.stopOnWorldUnload));
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

    private Component normalTestLabel() {
        return new TextComponent("Test cap: " + model.testMaxPercent + "% / "
                + (model.testMaxDurationMs / 1000.0) + "s");
    }

    private Component unsafeTestLabel() {
        return new TextComponent("Unsafe cap: " + model.unsafeTestMaxPercent + "% / "
                + (model.unsafeTestMaxDurationMs / 1000.0) + "s");
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

    /** A 0..100% slider bound to a model field; applies to the model live (persisted only on Save). */
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
