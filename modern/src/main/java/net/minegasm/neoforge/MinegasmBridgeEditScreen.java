package net.minegasm.neoforge;

import net.minegasm.client.MinegasmClient;
import net.minegasm.config.HapticConfig;

//? if >=26.1.2 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} elif >=1.20.1 {
/*import net.minecraft.client.gui.GuiGraphics;
*///?} else {
/*import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiComponent;
*///?}
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * Editor for one bridge endpoint (ADR-018 multi-endpoint). Edits an existing endpoint by index, or adds
 * a new one when the index is past the end. Writes the whole bridge list back through
 * {@link MinegasmClient#updateConfig}, like the sibling screens; the runtime rebuilds its backends from
 * the list on restart.
 */
public final class MinegasmBridgeEditScreen extends Screen {

    private final Screen parent;
    private final MinegasmClient client;
    private final int index;
    private final boolean existing;
    private final String transport;
    private EditBox nameBox;
    private EditBox urlBox;
    private boolean enabled;
    private boolean allowRemote;
    private int fieldsX;

    // Test feedback: record the ordinal of the test this screen fired, then wait for the bridge's settle
    // count to reach it so the line moves from "running" to how that exact test finished. Waiting on the
    // ordinal (not a baseline outcome) means rapid repeated clicks always report the latest test, never an
    // earlier superseded one.
    private long myTestFire;
    private boolean awaitingTest;
    private net.minegasm.backend.BackendOutcome testResult;

    public MinegasmBridgeEditScreen(Screen parent, MinegasmClient client, int index) {
        super(Component.translatable("minegasm.bridges.edit_title"));
        this.parent = parent;
        this.client = client;
        List<HapticConfig.Bridge> bridges = client.config().raw().bridges();
        this.existing = index >= 0 && index < bridges.size();
        this.index = index;
        if (existing) {
            HapticConfig.Bridge b = bridges.get(index);
            this.enabled = b.enabled();
            this.allowRemote = b.allowRemote();
            this.transport = b.transport();
        } else {
            this.enabled = false;
            this.allowRemote = false;
            this.transport = "tcp";
        }
    }

    @Override
    protected void init() {
        int width = Math.min(this.width - 16, 300);
        int x = (this.width - width) / 2;
        int h = 20;
        fieldsX = x;
        List<HapticConfig.Bridge> bridges = client.config().raw().bridges();

        nameBox = new EditBox(font, x, 52, width, h, Component.translatable("minegasm.bridges.name"));
        nameBox.setMaxLength(48);
        nameBox.setValue(existing ? bridges.get(index).name() : "bridge-" + (bridges.size() + 1));
        addRenderableWidget(nameBox);

        urlBox = new EditBox(font, x, 84, width, h, Component.translatable("minegasm.bridges.url"));
        urlBox.setMaxLength(256);
        urlBox.setValue(existing ? bridges.get(index).url() : "tcp://127.0.0.1:12347");
        addRenderableWidget(urlBox);

        addRenderableWidget(toggle(x, 108, width, "minegasm.bridges.enabled", () -> enabled,
                v -> enabled = v));
        addRenderableWidget(toggle(x, 132, width, "minegasm.bridges.allow_remote", () -> allowRemote,
                v -> allowRemote = v));

        addRenderableWidget(button(Component.translatable("minegasm.bridges.save"), b -> save(),
                x, 160, width, h));
        if (existing) {
            addRenderableWidget(button(Component.translatable("minegasm.bridges.remove"), b -> remove(),
                    x, 184, width, h));
            // Isolated test on this bridge only, so you can check its adapter without a Buttplug device.
            // Test and Cancel share the bottom row so the button can never run off a short screen; the
            // test is gated like Buttplug's: only when output is on and this bridge's adapter is connected.
            String bridgeName = bridges.get(index).name();
            boolean canTest = client.config().enabled()
                    && client.isOutputPermitted()
                    && client.bridgeConnected(bridgeName);
            int half = (width - 4) / 2;
            Button test = addRenderableWidget(button(Component.translatable("minegasm.devices.test_output"),
                    b -> fireBridgeTest(), x, this.height - 24, half, h));
            test.active = canTest;
            addRenderableWidget(button(Component.translatable("gui.cancel"), b -> onClose(),
                    x + half + 4, this.height - 24, half, h));
        } else {
            addRenderableWidget(button(Component.translatable("gui.cancel"), b -> onClose(),
                    x, this.height - 24, width, h));
        }
    }

    @Override
    public void tick() {
        super.tick();
        pollTestResult();
    }

    /** Fire an isolated test on this bridge and start watching for the settled result of this exact test. */
    private void fireBridgeTest() {
        String name = client.config().raw().bridges().get(index).name();
        long before = client.bridgeTestFireCount(name);
        client.testBridgeOutput(name, 0.25f);
        long after = client.bridgeTestFireCount(name);
        // Only wait if the click actually dispatched a test; a blocked one leaves the fire count unchanged.
        if (after != before) {
            myTestFire = after;
            awaitingTest = true;
            testResult = null;
        }
    }

    /** Wait for the test this screen fired (and every test before it) to settle, then show its result. */
    private void pollTestResult() {
        if (!awaitingTest || !existing) {
            return;
        }
        String name = client.config().raw().bridges().get(index).name();
        if (client.bridgeTestSettleCount(name) >= myTestFire) {
            testResult = client.bridgeTestOutcome(name);
            awaitingTest = false;
        }
    }

    /** The test-feedback line, or null when no test has run this session. */
    private Component testFeedbackText() {
        if (awaitingTest) {
            return Component.translatable("minegasm.devices.test.running");
        }
        return testResult == null ? null
                : Component.translatable("minegasm.devices.test.feedback",
                        MinegasmClient.testResultLabel(testResult));
    }

    /** ARGB colour matching the feedback state: grey pending, green delivered, yellow superseded, red bad. */
    private int testFeedbackColor() {
        if (awaitingTest || testResult == null) {
            return 0xFFA0A0A0;
        }
        switch (testResult.state()) {
            case DELIVERED:
                return 0xFF55FF55;
            case SUPERSEDED:
                return 0xFFFFFF55;
            default:
                return 0xFFFF5555; // failed or timed out
        }
    }

    private void save() {
        String url = urlBox.getValue().trim();
        try {
            URI parsed = URI.create(url);
            if (!"tcp".equalsIgnoreCase(parsed.getScheme()) || parsed.getHost() == null) {
                throw new IllegalArgumentException("not a tcp URL");
            }
        } catch (IllegalArgumentException invalid) {
            urlBox.setTextColor(0xFF5555);
            return;
        }
        String name = nameBox.getValue().trim();
        if (name.isEmpty() || nameTaken(name)) {
            nameBox.setTextColor(0xFF5555); // empty or already used by another bridge
            return;
        }
        HapticConfig cfg = client.config().raw();
        List<HapticConfig.Bridge> bridges = new ArrayList<>(cfg.bridges());
        if (existing) {
            // Keep the existing id so a rename doesn't reconnect the endpoint (review P1-7).
            String id = bridges.get(index).id();
            bridges.set(index, new HapticConfig.Bridge(name, enabled, url, transport, allowRemote, id));
        } else {
            bridges.add(new HapticConfig.Bridge(name, enabled, url, transport, allowRemote)); // fresh id
        }
        writeBridges(cfg, bridges);
        onClose();
    }

    /** Whether the name is already used by a bridge other than the one being edited (review P1-7). */
    private boolean nameTaken(String name) {
        List<HapticConfig.Bridge> bridges = client.config().raw().bridges();
        for (int i = 0; i < bridges.size(); i++) {
            if ((!existing || i != index) && bridges.get(i).name().equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    private void remove() {
        HapticConfig cfg = client.config().raw();
        List<HapticConfig.Bridge> bridges = new ArrayList<>(cfg.bridges());
        if (existing) {
            bridges.remove(index);
        }
        if (bridges.isEmpty()) {
            bridges.add(HapticConfig.Bridge.defaults()); // keep one row so the list is never empty
        }
        writeBridges(cfg, bridges);
        onClose();
    }

    private void writeBridges(HapticConfig cfg, List<HapticConfig.Bridge> bridges) {
        client.updateConfig(new HapticConfig(cfg.schemaVersion(), cfg.profile(), cfg.global(),
                cfg.buttplug(), cfg.events(), cfg.outputPolicy(), cfg.devices(),
                cfg.positionCalibrations(), cfg.accumulation(), cfg.customIntensity(), bridges));
    }

    private Button toggle(int x, int y, int width, String key,
                          java.util.function.BooleanSupplier getter,
                          java.util.function.Consumer<Boolean> setter) {
        return button(toggleLabel(key, getter.getAsBoolean()), b -> {
            boolean value = !getter.getAsBoolean();
            setter.accept(value);
            b.setMessage(toggleLabel(key, value));
        }, x, y, width, 20);
    }

    private Component toggleLabel(String key, boolean value) {
        return Component.translatable(key, Component.translatable(value ? "options.on" : "options.off"));
    }

    // Button.builder(...) was added in 1.19.4; 1.19.2 constructs Button directly.
    private Button button(Component message, Button.OnPress onPress, int x, int y, int width, int height) {
        //? if >=1.20.1 {
        return Button.builder(message, onPress).bounds(x, y, width, height).build();
        //?} else {
        /*return new Button(x, y, width, height, message, onPress);
        *///?}
    }

    //? if >=26.1.2 {
    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                   float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(this.font, this.title, this.width / 2, 20, 0xFFFFFFFF);
        graphics.centeredText(this.font, Component.translatable("minegasm.bridges.edit_subtitle"),
                this.width / 2, 31, 0xFFA0A0A0);
        Component testLine = testFeedbackText();
        if (testLine != null) {
            graphics.text(this.font, testLine, fieldsX, this.height - 38, testFeedbackColor());
        }
    }
    //?} elif >=1.20.1 {
    /*@Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        //? if <1.21.1 {
        /^this.renderBackground(graphics);
        ^///?}
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFFFF);
        graphics.drawCenteredString(this.font, Component.translatable("minegasm.bridges.edit_subtitle"),
                this.width / 2, 31, 0xFFA0A0A0);
        Component testLine = testFeedbackText();
        if (testLine != null) {
            graphics.drawString(this.font, testLine, fieldsX, this.height - 38, testFeedbackColor());
        }
    }
    *///?} else {
    /*@Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(poseStack);
        super.render(poseStack, mouseX, mouseY, partialTick);
        GuiComponent.drawCenteredString(poseStack, this.font, this.title, this.width / 2, 20, 0xFFFFFFFF);
        GuiComponent.drawCenteredString(poseStack, this.font,
                Component.translatable("minegasm.bridges.edit_subtitle"), this.width / 2, 31, 0xFFA0A0A0);
        Component testLine = testFeedbackText();
        if (testLine != null) {
            GuiComponent.drawString(poseStack, this.font, testLine, fieldsX, this.height - 38,
                    testFeedbackColor());
        }
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
