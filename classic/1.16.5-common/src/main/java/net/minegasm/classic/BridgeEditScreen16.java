package net.minegasm.classic;

import net.minegasm.client.MinegasmClient;
import net.minegasm.config.HapticConfig;

import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.TextComponent;

import com.mojang.blaze3d.vertex.PoseStack;

import java.net.URI;
import java.util.List;

/** Editor for one bridge endpoint on 1.16.5, matching the modern edit screen (ADR-018). */
public final class BridgeEditScreen16 extends Screen {

    private final Screen parent;
    private final MinegasmClient client;
    private final int index;
    private final boolean existing;
    private EditBox nameField;
    private EditBox urlField;
    private boolean enabled;
    private boolean allowRemote;

    public BridgeEditScreen16(Screen parent, MinegasmClient client, int index) {
        super(new TextComponent("Edit bridge"));
        this.parent = parent;
        this.client = client;
        List<HapticConfig.Bridge> bridges = BridgeList.bridges(client);
        this.existing = index >= 0 && index < bridges.size();
        this.index = index;
        if (existing) {
            HapticConfig.Bridge cur = bridges.get(index);
            this.enabled = cur.enabled();
            this.allowRemote = cur.allowRemote();
        }
    }

    @Override
    protected void init() {
        int w = Math.min(width - 16, 260);
        int x = (width - w) / 2;
        int h = 20;
        List<HapticConfig.Bridge> bridges = BridgeList.bridges(client);

        nameField = new EditBox(font, x, 52, w, 18, new TextComponent("Name"));
        nameField.setMaxLength(48);
        nameField.setValue(existing ? bridges.get(index).name() : "bridge-" + (bridges.size() + 1));
        addButton(nameField);

        urlField = new EditBox(font, x, 84, w, 18, new TextComponent("URL"));
        urlField.setMaxLength(256);
        urlField.setValue(existing ? bridges.get(index).url() : "tcp://127.0.0.1:12347");
        addButton(urlField);

        addButton(new Button(x, 108, w, h, enabledLabel(), b -> {
            enabled = !enabled;
            b.setMessage(enabledLabel());
        }));
        addButton(new Button(x, 132, w, h, allowRemoteLabel(), b -> {
            allowRemote = !allowRemote;
            b.setMessage(allowRemoteLabel());
        }));
        addButton(new Button(x, 160, w, h, new TextComponent("Save"), b -> save()));
        if (existing) {
            addButton(new Button(x, 184, w, h, new TextComponent("Remove"), b -> {
                BridgeList.remove(client, index);
                onClose();
            }));
            // Isolated test on this bridge only. Test and Cancel share the bottom row so nothing runs off a
            // short screen; the test is gated like Buttplug's: output on and this bridge's adapter connected.
            String bridgeName = bridges.get(index).name();
            boolean canTest = client.config().enabled()
                    && client.runtime().worker().isOutputEnabled()
                    && client.bridgeConnected(bridgeName);
            int half = (w - 4) / 2;
            Button test = addButton(new Button(x, height - 26, half, h, new TextComponent("Test output"),
                    b -> client.testBridgeOutput(bridgeName, 0.25f)));
            test.active = canTest;
            addButton(new Button(x + half + 4, height - 26, half, h, new TextComponent("Cancel"),
                    b -> onClose()));
        } else {
            addButton(new Button(x, height - 26, w, h, new TextComponent("Cancel"), b -> onClose()));
        }
    }

    private TextComponent enabledLabel() {
        return new TextComponent("Enabled: " + (enabled ? "ON" : "OFF"));
    }

    private TextComponent allowRemoteLabel() {
        return new TextComponent("Allow non-loopback: " + (allowRemote ? "ON" : "OFF"));
    }

    private void save() {
        String url = urlField.getValue().trim();
        try {
            URI u = URI.create(url);
            if (!"tcp".equalsIgnoreCase(u.getScheme()) || u.getHost() == null) {
                throw new IllegalArgumentException("not a tcp URL");
            }
        } catch (IllegalArgumentException invalid) {
            urlField.setTextColor(0xFF5555);
            return;
        }
        String name = nameField.getValue().trim();
        if (name.isEmpty()) {
            nameField.setTextColor(0xFF5555);
            return;
        }
        BridgeList.save(client, index, name, enabled, url, allowRemote);
        onClose();
    }

    @Override
    public void tick() {
        nameField.tick();
        urlField.tick();
    }

    @Override
    public void render(PoseStack pose, int mouseX, int mouseY, float partialTicks) {
        renderBackground(pose);
        GuiComponent.drawCenteredString(pose, font, "Edit Bridge", width / 2, 20, 0xFFFFFF);
        GuiComponent.drawCenteredString(pose, font, "Name, then endpoint URL (tcp://host:port)",
                width / 2, 31, 0xA0A0A0);
        super.render(pose, mouseX, mouseY, partialTicks);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
