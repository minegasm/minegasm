package net.minegasm.classic;

import net.minegasm.client.MinegasmClient;
import net.minegasm.config.HapticConfig;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;

import java.io.IOException;
import java.net.URI;
import java.util.List;

/** Editor for one bridge endpoint on 1.12.2, matching the 1.16.5 and modern editors (ADR-018). */
public final class ClassicBridgeEditScreen extends GuiScreen {

    private static final int ID_ENABLED = 1;
    private static final int ID_ALLOWREMOTE = 2;
    private static final int ID_SAVE = 3;
    private static final int ID_REMOVE = 4;
    private static final int ID_CANCEL = 5;
    private static final int ID_TEST = 6;

    private final GuiScreen parent;
    private final MinegasmClient client;
    private final int index;
    private final boolean existing;
    private GuiTextField nameField;
    private GuiTextField urlField;
    private GuiButton enabledBtn;
    private GuiButton allowRemoteBtn;
    private boolean enabled;
    private boolean allowRemote;
    private int fieldsX;

    public ClassicBridgeEditScreen(GuiScreen parent, int index) {
        this.parent = parent;
        this.client = ClassicClientHolder.get();
        List<HapticConfig.Bridge> bridges = BridgeList.bridges(client);
        this.existing = index >= 0 && index < bridges.size();
        this.index = index;
        if (existing) {
            HapticConfig.Bridge b = bridges.get(index);
            this.enabled = b.enabled();
            this.allowRemote = b.allowRemote();
        }
    }

    @Override
    public void initGui() {
        buttonList.clear();
        int w = Math.min(width - 16, 240);
        int x = width / 2 - w / 2;
        fieldsX = x;
        List<HapticConfig.Bridge> bridges = BridgeList.bridges(client);

        nameField = new GuiTextField(0, fontRenderer, x, 52, w, 18);
        nameField.setMaxStringLength(48);
        nameField.setText(existing ? bridges.get(index).name() : "bridge-" + (bridges.size() + 1));

        urlField = new GuiTextField(1, fontRenderer, x, 84, w, 18);
        urlField.setMaxStringLength(256);
        urlField.setText(existing ? bridges.get(index).url() : "tcp://127.0.0.1:12347");

        enabledBtn = addButton(new GuiButton(ID_ENABLED, x, 108, w, 20, enabledLabel()));
        allowRemoteBtn = addButton(new GuiButton(ID_ALLOWREMOTE, x, 132, w, 20, allowRemoteLabel()));
        addButton(new GuiButton(ID_SAVE, x, 160, w, 20, "Save"));
        if (existing) {
            addButton(new GuiButton(ID_REMOVE, x, 184, w, 20, "Remove"));
            int half = (w - 4) / 2;
            GuiButton test = addButton(new GuiButton(ID_TEST, x, height - 26, half, 20, "Test output"));
            test.enabled = client.config().enabled()
                    && client.runtime().worker().isOutputEnabled()
                    && client.bridgeConnected(BridgeList.bridges(client).get(index).name());
            addButton(new GuiButton(ID_CANCEL, x + half + 4, height - 26, half, 20, "Cancel"));
        } else {
            addButton(new GuiButton(ID_CANCEL, x, height - 26, w, 20, "Cancel"));
        }
    }

    private String enabledLabel() {
        return "Enabled: " + (enabled ? "ON" : "OFF");
    }

    private String allowRemoteLabel() {
        return "Allow non-loopback: " + (allowRemote ? "ON" : "OFF");
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        switch (button.id) {
            case ID_ENABLED:
                enabled = !enabled;
                enabledBtn.displayString = enabledLabel();
                break;
            case ID_ALLOWREMOTE:
                allowRemote = !allowRemote;
                allowRemoteBtn.displayString = allowRemoteLabel();
                break;
            case ID_SAVE:
                save();
                break;
            case ID_REMOVE:
                BridgeList.remove(client, index);
                mc.displayGuiScreen(parent);
                break;
            case ID_TEST:
                client.testBridgeOutput(BridgeList.bridges(client).get(index).name(), 0.25f);
                break;
            case ID_CANCEL:
                mc.displayGuiScreen(parent);
                break;
            default:
                break;
        }
    }

    private void save() {
        String url = urlField.getText().trim();
        try {
            URI u = URI.create(url);
            if (!"tcp".equalsIgnoreCase(u.getScheme()) || u.getHost() == null) {
                throw new IllegalArgumentException("not a tcp URL");
            }
        } catch (IllegalArgumentException invalid) {
            urlField.setTextColor(0xFF5555);
            return;
        }
        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            nameField.setTextColor(0xFF5555);
            return;
        }
        BridgeList.save(client, index, name, enabled, url, allowRemote);
        mc.displayGuiScreen(parent);
    }

    @Override
    public void updateScreen() {
        nameField.updateCursorCounter();
        urlField.updateCursorCounter();
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (nameField.isFocused()) {
            nameField.textboxKeyTyped(typedChar, keyCode);
            if (keyCode == 1) {
                super.keyTyped(typedChar, keyCode);
            }
        } else if (urlField.isFocused()) {
            urlField.textboxKeyTyped(typedChar, keyCode);
            if (keyCode == 1) {
                super.keyTyped(typedChar, keyCode);
            }
        } else {
            super.keyTyped(typedChar, keyCode);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        nameField.mouseClicked(mouseX, mouseY, mouseButton);
        urlField.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRenderer, "Edit Bridge", width / 2, 8, 0xFFFFFF);
        drawString(fontRenderer, "Name", fieldsX, 42, 0xA0A0A0);
        drawString(fontRenderer, "Endpoint URL (tcp://host:port)", fieldsX, 74, 0xA0A0A0);
        super.drawScreen(mouseX, mouseY, partialTicks);
        nameField.drawTextBox();
        urlField.drawTextBox();
    }
}
