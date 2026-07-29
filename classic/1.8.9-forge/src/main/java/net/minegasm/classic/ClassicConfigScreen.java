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
 * The in-game settings screen for Minecraft 1.8.9, opened from the mods list "Config" button (see
 * {@link ClassicGuiFactory}). It edits the shared {@link ClassicConfigModel} and exposes the same
 * settings the modern screen does: master enable, intensity, variation, recipe pack, compatibility mode,
 * fatigue protection, pause behavior, stop-on-world-unload, the Buttplug server URL, auto-connect,
 * auto-scan, and allow-remote, plus a status line and connect/test. Like modern it does not toggle
 * individual events; the recipe pack and mode select those. Edits apply on close.
 *
 * <p>Sibling of the 1.12.2 and 1.7.10 screens. On 1.8.9 the font accessor is {@code fontRendererObj} and
 * buttons are added straight to {@code buttonList} (no {@code addButton}).
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
    private GuiButton testBtn;
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
        int y0 = 40;
        int dy = 22;

        enabledBtn = new GuiButton(ID_ENABLED, lx, y0, 150, 20, enabledLabel());
        intensitySlider = new GuiSlider(ID_INTENSITY, lx, y0 + dy, 150, 20, "Intensity: ", "%",
                0, 100, Math.round(model.intensity * 100), false, true);
        variationSlider = new GuiSlider(ID_VARIATION, lx, y0 + 2 * dy, 150, 20, "Variation: ", "%",
                0, 100, Math.round(model.variation * 100), false, true);
        recipeBtn = new GuiButton(ID_RECIPE, lx, y0 + 3 * dy, 150, 20, recipeLabel());
        modeBtn = new GuiButton(ID_MODE, lx, y0 + 4 * dy, 150, 20, modeLabel());
        fatigueBtn = new GuiButton(ID_FATIGUE, lx, y0 + 5 * dy, 150, 20, fatigueLabel());
        pauseBtn = new GuiButton(ID_PAUSE, lx, y0 + 6 * dy, 150, 20, pauseLabel());
        buttonList.add(enabledBtn);
        buttonList.add(intensitySlider);
        buttonList.add(variationSlider);
        buttonList.add(recipeBtn);
        buttonList.add(modeBtn);
        buttonList.add(fatigueBtn);
        buttonList.add(pauseBtn);

        autoConnectBtn = new GuiButton(ID_AUTOCONNECT, rx, y0, 150, 20, autoConnectLabel());
        autoScanBtn = new GuiButton(ID_AUTOSCAN, rx, y0 + dy, 150, 20, autoScanLabel());
        allowRemoteBtn = new GuiButton(ID_ALLOWREMOTE, rx, y0 + 2 * dy, 150, 20, allowRemoteLabel());
        stopUnloadBtn = new GuiButton(ID_STOPUNLOAD, rx, y0 + 3 * dy, 150, 20, stopUnloadLabel());
        buttonList.add(autoConnectBtn);
        buttonList.add(autoScanBtn);
        buttonList.add(allowRemoteBtn);
        buttonList.add(stopUnloadBtn);

        serverFieldX = rx;
        serverFieldY = y0 + 4 * dy + 10; // leaves room for the "Server:" label above it
        serverField = new GuiTextField(0, fontRendererObj, serverFieldX, serverFieldY, 150, 18);
        serverField.setMaxStringLength(120);
        serverField.setText(model.serverUrl);

        buttonList.add(new GuiButton(ID_CONNECT, rx, y0 + 6 * dy, 73, 20, "Connect"));
        testBtn = new GuiButton(ID_TEST, rx + 77, y0 + 6 * dy, 73, 20, "Test");
        buttonList.add(testBtn);

        buttonList.add(new GuiButton(ID_DONE, width / 2 - 100, height - 26, 200, 20, "Done"));
        refreshActionButtons();
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        switch (button.id) {
            case ID_ENABLED:
                model.enabled = !model.enabled;
                enabledBtn.displayString = enabledLabel();
                applyNow();
                break;
            case ID_RECIPE:
                model.toggleRecipePack();
                recipeBtn.displayString = recipeLabel();
                applyNow();
                break;
            case ID_MODE:
                model.cycleMode();
                modeBtn.displayString = modeLabel();
                applyNow();
                break;
            case ID_FATIGUE:
                model.fatigueProtection = !model.fatigueProtection;
                fatigueBtn.displayString = fatigueLabel();
                applyNow();
                break;
            case ID_PAUSE:
                model.cyclePauseBehavior();
                pauseBtn.displayString = pauseLabel();
                applyNow();
                break;
            case ID_AUTOCONNECT:
                model.autoConnect = !model.autoConnect;
                autoConnectBtn.displayString = autoConnectLabel();
                applyNow();
                break;
            case ID_AUTOSCAN:
                model.autoScan = !model.autoScan;
                autoScanBtn.displayString = autoScanLabel();
                applyNow();
                break;
            case ID_ALLOWREMOTE:
                model.allowRemote = !model.allowRemote;
                allowRemoteBtn.displayString = allowRemoteLabel();
                applyNow();
                break;
            case ID_STOPUNLOAD:
                model.stopOnWorldUnload = !model.stopOnWorldUnload;
                stopUnloadBtn.displayString = stopUnloadLabel();
                applyNow();
                break;
            case ID_CONNECT:
                applyNow();
                if (!client.isConnected()) {
                    client.connect();
                }
                break;
            case ID_TEST:
                applyNow();
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
        int lx = width / 2 - 155;
        int rx = width / 2 + 5;
        drawCenteredString(fontRendererObj, "Minegasm", width / 2, 8, 0xFFFFFF);
        drawCenteredString(fontRendererObj, "Gameplay", lx + 75, 28, 0xC0C0C0);
        drawCenteredString(fontRendererObj, "Connection", rx + 75, 28, 0xC0C0C0);
        drawString(fontRendererObj, "Server:", serverFieldX, serverFieldY - 10, 0xA0A0A0);
        super.drawScreen(mouseX, mouseY, partialTicks);
        serverField.drawTextBox();
        drawCenteredString(fontRendererObj, statusLine(), width / 2, height - 40, 0x80FF80);
    }

    @Override
    public void onGuiClosed() {
        applyNow();
    }

    /**
     * Read the widget-owned values into the model, persist the whole config through the client, and
     * refresh the action buttons. Called after every change so the settings take effect immediately (the
     * modern screen behaves the same way); without this, Test would read the pre-edit config and quietly
     * do nothing right after the player enabled haptics.
     */
    private void applyNow() {
        syncWidgetsIntoModel();
        model.apply(client);
        refreshActionButtons();
    }

    /** Grey out Test when the live config cannot produce a pulse, so the state is visible. */
    private void refreshActionButtons() {
        if (testBtn != null) {
            testBtn.enabled = client.config().enabled() && client.isConnected();
        }
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
