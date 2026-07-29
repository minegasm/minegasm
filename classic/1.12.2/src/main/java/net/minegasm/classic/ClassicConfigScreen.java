package net.minegasm.classic;

import net.minegasm.buttplug.ProviderStatus;
import net.minegasm.client.MinegasmClient;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraftforge.fml.client.config.GuiSlider;

import java.io.IOException;
import java.util.Locale;

/**
 * The in-game settings screen for Minecraft 1.12.2, opened from the mods list "Config" button (see
 * {@link ClassicGuiFactory}). It edits the shared, Minecraft-free {@link ClassicConfigModel} and exposes
 * the same settings the modern screen does: master enable, intensity, variation, recipe pack,
 * compatibility mode, fatigue protection, pause behavior, stop-on-world-unload, the Buttplug server URL,
 * auto-connect, auto-scan, and allow-remote, plus a live status line and connect/test actions. Like
 * modern it does not toggle individual gameplay events; the recipe pack and mode select those. Edits
 * apply through {@link MinegasmClient#updateConfig} on close, so untouched config is preserved.
 *
 * <p>1.8.9 and 1.7.10 have their own sibling screens; the GuiScreen/GuiButton/slider widgets differ
 * enough across the three lines that they are not shared.
 */
public final class ClassicConfigScreen extends GuiScreen {

    private static final int ID_ENABLED = 1;
    private static final int ID_INTENSITY = 2;
    private static final int ID_VARIATION = 3;
    private static final int ID_RECIPE = 4;
    private static final int ID_MODE = 5;
    private static final int ID_FATIGUE = 6;
    private static final int ID_PAUSE = 7;
    private static final int ID_AUTOCONNECT = 8;
    private static final int ID_AUTOSCAN = 9;
    private static final int ID_ALLOWREMOTE = 10;
    private static final int ID_STOPUNLOAD = 11;
    private static final int ID_CONNECT = 12;
    private static final int ID_TEST = 13;
    private static final int ID_DONE = 14;

    private final GuiScreen parent;
    private final MinegasmClient client;
    private final ClassicConfigModel model;

    private GuiButton enabledBtn;
    private GuiButton recipeBtn;
    private GuiButton modeBtn;
    private GuiButton fatigueBtn;
    private GuiButton pauseBtn;
    private GuiButton autoConnectBtn;
    private GuiButton autoScanBtn;
    private GuiButton allowRemoteBtn;
    private GuiButton stopUnloadBtn;
    private GuiSlider intensitySlider;
    private GuiSlider variationSlider;
    private GuiTextField serverField;
    private int serverFieldX;
    private int serverFieldY;

    public ClassicConfigScreen(GuiScreen parent) {
        this.parent = parent;
        this.client = ClassicClientHolder.get();
        this.model = new ClassicConfigModel(client.config().raw());
    }

    @Override
    public void initGui() {
        buttonList.clear();
        int lx = width / 2 - 155;
        int rx = width / 2 + 5;
        int y0 = 30;
        int dy = 22;

        enabledBtn = addButton(new GuiButton(ID_ENABLED, lx, y0, 150, 20, enabledLabel()));
        intensitySlider = new GuiSlider(ID_INTENSITY, lx, y0 + dy, 150, 20, "Intensity: ", "%",
                0, 100, Math.round(model.intensity * 100), false, true);
        addButton(intensitySlider);
        variationSlider = new GuiSlider(ID_VARIATION, lx, y0 + 2 * dy, 150, 20, "Variation: ", "%",
                0, 100, Math.round(model.variation * 100), false, true);
        addButton(variationSlider);
        recipeBtn = addButton(new GuiButton(ID_RECIPE, lx, y0 + 3 * dy, 150, 20, recipeLabel()));
        modeBtn = addButton(new GuiButton(ID_MODE, lx, y0 + 4 * dy, 150, 20, modeLabel()));
        fatigueBtn = addButton(new GuiButton(ID_FATIGUE, lx, y0 + 5 * dy, 150, 20, fatigueLabel()));
        pauseBtn = addButton(new GuiButton(ID_PAUSE, lx, y0 + 6 * dy, 150, 20, pauseLabel()));

        serverFieldX = rx;
        serverFieldY = y0 + 1;
        serverField = new GuiTextField(0, fontRenderer, serverFieldX, serverFieldY, 150, 18);
        serverField.setMaxStringLength(120);
        serverField.setText(model.serverUrl);

        autoConnectBtn = addButton(new GuiButton(ID_AUTOCONNECT, rx, y0 + dy, 150, 20, autoConnectLabel()));
        autoScanBtn = addButton(new GuiButton(ID_AUTOSCAN, rx, y0 + 2 * dy, 150, 20, autoScanLabel()));
        allowRemoteBtn = addButton(new GuiButton(ID_ALLOWREMOTE, rx, y0 + 3 * dy, 150, 20, allowRemoteLabel()));
        stopUnloadBtn = addButton(new GuiButton(ID_STOPUNLOAD, rx, y0 + 4 * dy, 150, 20, stopUnloadLabel()));
        addButton(new GuiButton(ID_CONNECT, rx, y0 + 5 * dy, 73, 20, "Connect"));
        addButton(new GuiButton(ID_TEST, rx + 77, y0 + 5 * dy, 73, 20, "Test"));

        addButton(new GuiButton(ID_DONE, width / 2 - 100, height - 26, 200, 20, "Done"));
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        switch (button.id) {
            case ID_ENABLED:
                model.enabled = !model.enabled;
                enabledBtn.displayString = enabledLabel();
                break;
            case ID_RECIPE:
                model.toggleRecipePack();
                recipeBtn.displayString = recipeLabel();
                break;
            case ID_MODE:
                model.cycleMode();
                modeBtn.displayString = modeLabel();
                break;
            case ID_FATIGUE:
                model.fatigueProtection = !model.fatigueProtection;
                fatigueBtn.displayString = fatigueLabel();
                break;
            case ID_PAUSE:
                model.cyclePauseBehavior();
                pauseBtn.displayString = pauseLabel();
                break;
            case ID_AUTOCONNECT:
                model.autoConnect = !model.autoConnect;
                autoConnectBtn.displayString = autoConnectLabel();
                break;
            case ID_AUTOSCAN:
                model.autoScan = !model.autoScan;
                autoScanBtn.displayString = autoScanLabel();
                break;
            case ID_ALLOWREMOTE:
                model.allowRemote = !model.allowRemote;
                allowRemoteBtn.displayString = allowRemoteLabel();
                break;
            case ID_STOPUNLOAD:
                model.stopOnWorldUnload = !model.stopOnWorldUnload;
                stopUnloadBtn.displayString = stopUnloadLabel();
                break;
            case ID_CONNECT:
                if (!client.isConnected()) {
                    client.connect();
                }
                break;
            case ID_TEST:
                if (client.isConnected() && client.config().enabled()) {
                    client.testPulse(0.5f, 400);
                }
                break;
            case ID_DONE:
                mc.displayGuiScreen(parent);
                break;
            default:
                break;
        }
    }

    @Override
    public void updateScreen() {
        serverField.updateCursorCounter();
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (serverField.isFocused()) {
            serverField.textboxKeyTyped(typedChar, keyCode);
            model.serverUrl = serverField.getText().trim();
            if (keyCode == 1) { // Escape still closes the screen
                super.keyTyped(typedChar, keyCode);
            }
        } else {
            super.keyTyped(typedChar, keyCode);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        serverField.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRenderer, "Minegasm", width / 2, 10, 0xFFFFFF);
        drawString(fontRenderer, "Server:", serverFieldX, serverFieldY - 10, 0xA0A0A0);
        super.drawScreen(mouseX, mouseY, partialTicks);
        serverField.drawTextBox();
        drawCenteredString(fontRenderer, statusLine(), width / 2, height - 40, 0x80FF80);
    }

    @Override
    public void onGuiClosed() {
        syncWidgetsIntoModel();
        model.apply(client);
    }

    private void syncWidgetsIntoModel() {
        if (intensitySlider != null) {
            model.intensity = intensitySlider.getValue() / 100.0;
        }
        if (variationSlider != null) {
            model.variation = variationSlider.getValue() / 100.0;
        }
        if (serverField != null) {
            model.serverUrl = serverField.getText().trim();
        }
    }

    // --- labels ----------------------------------------------------------------------------

    private String enabledLabel() {
        return "Haptics: " + onOff(model.enabled);
    }

    private String recipeLabel() {
        return "Recipe: " + capitalize(model.recipePack.name());
    }

    private String modeLabel() {
        return "Mode: " + capitalize(model.mode.name());
    }

    private String fatigueLabel() {
        return "Fatigue guard: " + onOff(model.fatigueProtection);
    }

    private String pauseLabel() {
        return "Pause: " + capitalize(model.pauseBehavior.name());
    }

    private String autoConnectLabel() {
        return "Auto-connect: " + onOff(model.autoConnect);
    }

    private String autoScanLabel() {
        return "Auto-scan: " + onOff(model.autoScan);
    }

    private String allowRemoteLabel() {
        return "Allow remote: " + onOff(model.allowRemote);
    }

    private String stopUnloadLabel() {
        return "Stop on exit: " + onOff(model.stopOnWorldUnload);
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
}
