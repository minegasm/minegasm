package net.minegasm.classic;

import net.minegasm.client.MinegasmClient;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;

import cpw.mods.fml.client.config.GuiSlider;

import java.util.Locale;

/**
 * The gameplay/connection settings sub-screen for Minecraft 1.7.10, reached from the dashboard's Settings
 * button. Mirrors the modern settings screen: two columns (gameplay left, connection right) of the knobs
 * the recipe pack and mode select over, plus the test-output caps, with Save/Cancel. Unlike the dashboard,
 * edits are deferred; Cancel discards them and Save applies the whole config through
 * {@link MinegasmClient#updateConfig}, preserving everything the screen does not model.
 *
 * <p>Sibling of the 1.8.9 and 1.12.2 settings screens. On 1.7.10 the callbacks do not throw
 * {@code IOException}, {@code GuiTextField} takes no id, and the slider is {@code cpw.mods.fml}'s.
 */
public final class ClassicSettingsScreen extends GuiScreen {

    private static final int ID_INTENSITY = 1;
    private static final int ID_VARIATION = 2;
    private static final int ID_RECIPE = 3;
    private static final int ID_MODE = 4;
    private static final int ID_FATIGUE = 5;
    private static final int ID_PAUSE = 6;
    private static final int ID_STOPUNLOAD = 7;
    private static final int ID_AUTOCONNECT = 8;
    private static final int ID_AUTOSCAN = 9;
    private static final int ID_ALLOWREMOTE = 10;
    private static final int ID_NORMALTEST = 11;
    private static final int ID_UNSAFETEST = 12;
    private static final int ID_SAVE = 13;
    private static final int ID_CANCEL = 14;
    private static final int ID_BRIDGE = 15;
    private static final int ID_RESET = 16;

    private final GuiScreen parent;
    private final MinegasmClient client;
    private final ClassicConfigModel model;

    private GuiButton recipeBtn;
    private GuiButton modeBtn;
    private GuiButton fatigueBtn;
    private GuiButton pauseBtn;
    private GuiButton stopUnloadBtn;
    private GuiButton autoConnectBtn;
    private GuiButton autoScanBtn;
    private GuiButton allowRemoteBtn;
    private GuiButton normalTestBtn;
    private GuiButton unsafeTestBtn;
    private GuiButton bridgeBtn;
    private GuiButton resetBtn;
    private boolean resetArmed;
    private GuiSlider intensitySlider;
    private GuiSlider variationSlider;
    private GuiTextField serverField;
    private int serverFieldX;
    private int serverFieldY;

    public ClassicSettingsScreen(GuiScreen parent) {
        this.parent = parent;
        this.client = ClassicClientHolder.get();
        this.model = new ClassicConfigModel(client.config().raw());
    }

    @Override
    @SuppressWarnings("unchecked")
    public void initGui() {
        buttonList.clear();
        // The pack screen writes the selection immediately, so re-read it here rather than trust a
        // snapshot taken when this screen opened; otherwise Save would revert a pack picked meanwhile.
        model.recipePack = client.config().raw().profile().recipePack();
        int lx = width / 2 - 155;
        int rx = width / 2 + 5;
        int y0 = 40;
        int dy = 22;

        intensitySlider = new GuiSlider(ID_INTENSITY, lx, y0, 150, 20, "Intensity: ", "%",
                0, 100, Math.round(model.intensity * 100), false, true);
        variationSlider = new GuiSlider(ID_VARIATION, lx, y0 + dy, 150, 20, "Variation: ", "%",
                0, 100, Math.round(model.variation * 100), false, true);
        buttonList.add(intensitySlider);
        buttonList.add(variationSlider);
        recipeBtn = new GuiButton(ID_RECIPE, lx, y0 + 2 * dy, 150, 20, recipeLabel());
        modeBtn = new GuiButton(ID_MODE, lx, y0 + 3 * dy, 150, 20, modeLabel());
        fatigueBtn = new GuiButton(ID_FATIGUE, lx, y0 + 4 * dy, 150, 20, fatigueLabel());
        pauseBtn = new GuiButton(ID_PAUSE, lx, y0 + 5 * dy, 150, 20, pauseLabel());
        stopUnloadBtn = new GuiButton(ID_STOPUNLOAD, lx, y0 + 6 * dy, 150, 20, stopUnloadLabel());
        buttonList.add(recipeBtn);
        buttonList.add(modeBtn);
        buttonList.add(fatigueBtn);
        buttonList.add(pauseBtn);
        buttonList.add(stopUnloadBtn);

        serverFieldX = rx;
        // Server field is the first row of the connection column, like the modern screen. It shows the
        // current URL, so there is no separate "Server:" label (which is what crowded the header).
        serverFieldY = y0;
        serverField = new GuiTextField(fontRendererObj, serverFieldX, serverFieldY, 150, 18);
        serverField.setMaxStringLength(120);
        serverField.setText(model.serverUrl);
        autoConnectBtn = new GuiButton(ID_AUTOCONNECT, rx, y0 + dy, 150, 20, autoConnectLabel());
        autoScanBtn = new GuiButton(ID_AUTOSCAN, rx, y0 + 2 * dy, 150, 20, autoScanLabel());
        allowRemoteBtn = new GuiButton(ID_ALLOWREMOTE, rx, y0 + 3 * dy, 150, 20, allowRemoteLabel());
        normalTestBtn = new GuiButton(ID_NORMALTEST, rx, y0 + 4 * dy, 150, 20, normalTestLabel());
        unsafeTestBtn = new GuiButton(ID_UNSAFETEST, rx, y0 + 5 * dy, 150, 20, unsafeTestLabel());
        bridgeBtn = new GuiButton(ID_BRIDGE, rx, y0 + 6 * dy, 150, 20, bridgeLabel());
        buttonList.add(autoConnectBtn);
        buttonList.add(autoScanBtn);
        buttonList.add(allowRemoteBtn);
        buttonList.add(normalTestBtn);
        buttonList.add(unsafeTestBtn);
        buttonList.add(bridgeBtn);

        // Reset fills the left column; Save/Cancel split the right, so nothing crowds the reset button.
        resetBtn = new GuiButton(ID_RESET, lx, height - 26, 150, 20, resetLabel());
        buttonList.add(resetBtn);
        buttonList.add(new GuiButton(ID_SAVE, rx, height - 26, 73, 20, "Save"));
        buttonList.add(new GuiButton(ID_CANCEL, rx + 77, height - 26, 73, 20, "Cancel"));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        switch (button.id) {
            case ID_RECIPE:
                // Stage the deferred slider/URL edits before leaving, so opening the pack screen and
                // returning does not discard them (initGui rebuilds these from the model, and only Save
                // otherwise writes them there).
                model.intensity = intensitySlider.getValueInt() / 100.0;
                model.variation = variationSlider.getValueInt() / 100.0;
                model.serverUrl = serverField.getText();
                mc.displayGuiScreen(new ClassicScenePackScreen(this));
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
            case ID_STOPUNLOAD:
                model.stopOnWorldUnload = !model.stopOnWorldUnload;
                stopUnloadBtn.displayString = stopUnloadLabel();
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
            case ID_NORMALTEST:
                model.cycleNormalTestLimit();
                normalTestBtn.displayString = normalTestLabel();
                unsafeTestBtn.displayString = unsafeTestLabel();
                break;
            case ID_UNSAFETEST:
                model.cycleUnsafeTestLimit();
                unsafeTestBtn.displayString = unsafeTestLabel();
                break;
            case ID_BRIDGE:
                model.bridgeEnabled = !model.bridgeEnabled;
                bridgeBtn.displayString = bridgeLabel();
                break;
            case ID_SAVE:
                String url = serverField.getText().trim();
                if (!ClassicConfigModel.isValidServerUrl(url)) {
                    serverField.setTextColor(0xFF5555); // reject a non-ws(s) URL, like the modern screen
                    return;
                }
                model.intensity = intensitySlider.getValueInt() / 100.0;
                model.variation = variationSlider.getValueInt() / 100.0;
                model.serverUrl = url;
                model.apply(client);
                mc.displayGuiScreen(parent);
                break;
            case ID_RESET:
                if (resetArmed) {
                    client.resetToDefaults();
                    mc.displayGuiScreen(new ClassicSettingsScreen(parent));
                } else {
                    resetArmed = true;
                    resetBtn.displayString = resetLabel();
                }
                break;
            case ID_CANCEL:
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
    protected void keyTyped(char typedChar, int keyCode) {
        if (serverField.isFocused()) {
            serverField.textboxKeyTyped(typedChar, keyCode);
            if (keyCode == 1) { // Escape still closes the screen
                super.keyTyped(typedChar, keyCode);
            }
        } else {
            super.keyTyped(typedChar, keyCode);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        serverField.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        int lx = width / 2 - 155;
        int rx = width / 2 + 5;
        drawCenteredString(fontRendererObj, "Minegasm settings", width / 2, 8, 0xFFFFFF);
        drawCenteredString(fontRendererObj, "Gameplay", lx + 75, 28, 0xC0C0C0);
        drawCenteredString(fontRendererObj, "Connection", rx + 75, 28, 0xC0C0C0);
        super.drawScreen(mouseX, mouseY, partialTicks);
        serverField.drawTextBox();
    }

    // --- labels ----------------------------------------------------------------------------

    private String recipeLabel() {
        return "Recipe: " + capitalize(model.recipePack);
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

    private String stopUnloadLabel() {
        return "Stop on exit: " + onOff(model.stopOnWorldUnload);
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

    private String normalTestLabel() {
        return "Test cap: " + model.testMaxPercent + "% / " + (model.testMaxDurationMs / 1000.0) + "s";
    }

    private String unsafeTestLabel() {
        return "Unsafe cap: " + model.unsafeTestMaxPercent + "% / "
                + (model.unsafeTestMaxDurationMs / 1000.0) + "s";
    }

    private String bridgeLabel() {
        // The bridge backend is built at startup, so a changed value needs a restart to take effect.
        boolean changed = model.bridgeEnabled != client.config().raw().bridge().enabled();
        return "Bridge: " + onOff(model.bridgeEnabled) + (changed ? " (restart)" : "");
    }

    private String resetLabel() {
        return resetArmed ? "Confirm reset?" : "Reset to defaults";
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
