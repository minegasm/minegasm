package net.minegasm.neoforge;

import net.minegasm.client.MinegasmClient;
import net.minegasm.config.HapticConfig;
import net.minegasm.config.MinegasmMode;
import net.minegasm.config.PauseBehavior;
import net.minegasm.config.RecipePackId;
import net.minegasm.config.TestOutputLimits;

//? if >=26.1.2 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} elif >=1.20.1 {
/*import net.minecraft.client.gui.GuiGraphics;
*///?} else {
/*import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiComponent;
*///?}
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.net.URI;
import java.util.Locale;
import java.util.function.DoubleConsumer;

/** Staged editor for the core gameplay, safety, and connection settings. */
public final class MinegasmSettingsScreen extends Screen {
    private final Screen parent;
    private final MinegasmClient client;
    private double intensity;
    private double variation;
    private RecipePackId recipePack;
    private MinegasmMode mode;
    private boolean fatigue;
    private PauseBehavior pauseBehavior;
    private boolean stopOnWorldUnload;
    private boolean autoConnect;
    private boolean autoScan;
    private boolean allowRemote;
    private int testMaxPercent;
    private int testMaxDurationMs;
    private int unsafeTestMaxPercent;
    private int unsafeTestMaxDurationMs;
    private EditBox serverUrl;

    public MinegasmSettingsScreen(Screen parent, MinegasmClient client) {
        super(Component.translatable("minegasm.settings.title"));
        this.parent = parent;
        this.client = client;
        HapticConfig cfg = client.config().raw();
        intensity = cfg.global().intensity();
        variation = cfg.global().variation();
        recipePack = cfg.identity().recipePackId();
        mode = cfg.identity().mode();
        fatigue = cfg.global().fatigueProtection();
        pauseBehavior = cfg.global().pauseBehaviorMode();
        stopOnWorldUnload = cfg.global().stopOnWorldUnload();
        autoConnect = cfg.buttplug().autoConnect();
        autoScan = cfg.buttplug().autoScan();
        allowRemote = cfg.buttplug().allowRemoteServer();
        testMaxPercent = cfg.global().testMaxPercent();
        testMaxDurationMs = cfg.global().testMaxDurationMs();
        unsafeTestMaxPercent = cfg.global().unsafeTestMaxPercent();
        unsafeTestMaxDurationMs = cfg.global().unsafeTestMaxDurationMs();
    }

    @Override
    protected void init() {
        int totalWidth = Math.min(width - 16, 420);
        int gap = 8;
        int columnWidth = (totalWidth - gap) / 2;
        int left = (width - totalWidth) / 2;
        int right = left + columnWidth + gap;
        int h = 20;

        addRenderableWidget(new PercentSlider(left, 48, columnWidth, h,
                "minegasm.settings.intensity", intensity, value -> intensity = value));
        addRenderableWidget(new PercentSlider(left, 72, columnWidth, h,
                "minegasm.settings.variation", variation, value -> variation = value));

        addRenderableWidget(button(packLabel(), b -> {
            recipePack = recipePack == RecipePackId.BALANCED
                    ? RecipePackId.CLASSIC : RecipePackId.BALANCED;
            b.setMessage(packLabel());
        }, left, 96, columnWidth, h));

        addRenderableWidget(button(modeLabel(), b -> {
            MinegasmMode[] values = MinegasmMode.values();
            mode = values[(mode.ordinal() + 1) % values.length];
            b.setMessage(modeLabel());
        }, left, 120, columnWidth, h));

        addRenderableWidget(toggle(left, 144, columnWidth, "minegasm.settings.fatigue",
                () -> fatigue, value -> fatigue = value));
        addRenderableWidget(button(pauseBehaviorLabel(), b -> {
            PauseBehavior[] values = PauseBehavior.values();
            pauseBehavior = values[(pauseBehavior.ordinal() + 1) % values.length];
            b.setMessage(pauseBehaviorLabel());
        }, left, 168, columnWidth, h));
        addRenderableWidget(toggle(left, 192, columnWidth, "minegasm.settings.stop_unload",
                () -> stopOnWorldUnload, value -> stopOnWorldUnload = value));

        HapticConfig.Buttplug bp = client.config().raw().buttplug();
        serverUrl = new EditBox(font, right, 48, columnWidth, h,
                Component.translatable("minegasm.settings.server_url"));
        serverUrl.setMaxLength(256);
        serverUrl.setValue(bp.serverUrl());
        addRenderableWidget(serverUrl);
        addRenderableWidget(toggle(right, 72, columnWidth, "minegasm.settings.auto_connect",
                () -> autoConnect, value -> autoConnect = value));
        addRenderableWidget(toggle(right, 96, columnWidth, "minegasm.settings.auto_scan",
                () -> autoScan, value -> autoScan = value));
        addRenderableWidget(toggle(right, 120, columnWidth, "minegasm.settings.allow_remote",
                () -> allowRemote, value -> allowRemote = value));
        addRenderableWidget(button(normalTestLimitLabel(), b -> {
            cycleNormalTestLimit();
            b.setMessage(normalTestLimitLabel());
        }, right, 144, columnWidth, h));
        addRenderableWidget(button(unsafeTestLimitLabel(), b -> {
            cycleUnsafeTestLimit();
            b.setMessage(unsafeTestLimitLabel());
        }, right, 168, columnWidth, h));

        addRenderableWidget(button(Component.translatable("minegasm.settings.save"),
                b -> save(), right, height - 24, (columnWidth - 4) / 2, h));
        addRenderableWidget(button(Component.translatable("gui.cancel"), b -> onClose(),
                right + (columnWidth - 4) / 2 + 4, height - 24, (columnWidth - 4) / 2, h));
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

    // Button.builder(...) was added in 1.19.4; 1.19.2 constructs Button directly. One guarded factory
    // keeps every call site version-agnostic (message, action, then bounds as x/y/width/height).
    private Button button(Component message, Button.OnPress onPress, int x, int y, int width, int height) {
        //? if >=1.20.1 {
        return Button.builder(message, onPress).bounds(x, y, width, height).build();
        //?} else {
        /*return new Button(x, y, width, height, message, onPress);
        *///?}
    }

    private Component toggleLabel(String key, boolean value) {
        return Component.translatable(key,
                Component.translatable(value ? "options.on" : "options.off"));
    }

    private Component packLabel() {
        return Component.translatable("minegasm.settings.recipe_pack",
                recipePack.name().toLowerCase(Locale.ROOT));
    }

    private Component modeLabel() {
        return Component.translatable("minegasm.settings.mode", mode.name());
    }

    private Component pauseBehaviorLabel() {
        return Component.translatable("minegasm.settings.pause_behavior",
                Component.translatable("minegasm.pause_behavior."
                        + pauseBehavior.name().toLowerCase(Locale.ROOT)));
    }

    private Component normalTestLimitLabel() {
        return Component.translatable("minegasm.settings.test_limit",
                testMaxPercent + "% / " + testMaxDurationMs / 1_000.0 + "s");
    }

    private Component unsafeTestLimitLabel() {
        return Component.translatable("minegasm.settings.unsafe_limit",
                unsafeTestMaxPercent + "% / " + unsafeTestMaxDurationMs / 1_000.0 + "s");
    }

    private void cycleNormalTestLimit() {
        int[][] profiles = {{25, 400}, {50, 2_000}, {75, 5_000}, {100, 10_000}};
        int next = nextProfile(profiles, testMaxPercent, testMaxDurationMs);
        testMaxPercent = profiles[next][0];
        testMaxDurationMs = profiles[next][1];
        unsafeTestMaxPercent = Math.max(unsafeTestMaxPercent, testMaxPercent);
        unsafeTestMaxDurationMs = Math.max(unsafeTestMaxDurationMs, testMaxDurationMs);
    }

    private void cycleUnsafeTestLimit() {
        int[][] profiles = {{50, 2_000}, {75, 5_000}, {100, 10_000},
                {100, 30_000}, {100, 60_000}, {100, 300_000},
                {TestOutputLimits.MAX_PERCENT, TestOutputLimits.MAX_DURATION_MS}};
        int next = nextProfile(profiles, unsafeTestMaxPercent, unsafeTestMaxDurationMs);
        for (int i = 0; i < profiles.length; i++) {
            int candidate = (next + i) % profiles.length;
            if (profiles[candidate][0] >= testMaxPercent
                    && profiles[candidate][1] >= testMaxDurationMs) {
                unsafeTestMaxPercent = profiles[candidate][0];
                unsafeTestMaxDurationMs = profiles[candidate][1];
                return;
            }
        }
    }

    private static int nextProfile(int[][] profiles, int percent, int durationMs) {
        for (int i = 0; i < profiles.length; i++) {
            if (profiles[i][0] == percent && profiles[i][1] == durationMs) {
                return (i + 1) % profiles.length;
            }
        }
        return 0;
    }

    private void save() {
        String url = serverUrl.getValue().trim();
        try {
            URI parsed = URI.create(url);
            if (!("ws".equalsIgnoreCase(parsed.getScheme())
                    || "wss".equalsIgnoreCase(parsed.getScheme())) || parsed.getHost() == null) {
                throw new IllegalArgumentException("not a WebSocket URL");
            }
        } catch (IllegalArgumentException invalid) {
            serverUrl.setTextColor(0xFF5555);
            return;
        }

        HapticConfig cfg = client.config().raw();
        var g = cfg.global();
        var bp = cfg.buttplug();
        HapticConfig updated = new HapticConfig(cfg.schemaVersion(),
                new HapticConfig.Identity(recipePack.name().toLowerCase(Locale.ROOT), mode.name()),
                new HapticConfig.Global(g.enabled(), intensity, variation, fatigue,
                        pauseBehavior.name(), stopOnWorldUnload, g.panicKey(),
                        testMaxPercent, testMaxDurationMs,
                        unsafeTestMaxPercent, unsafeTestMaxDurationMs),
                new HapticConfig.Buttplug(url, autoConnect, autoScan, allowRemote,
                        bp.reconnect(), bp.client()),
                cfg.events(), cfg.outputPolicy(), cfg.devices(), cfg.positionCalibrations(),
                cfg.accumulation(), cfg.customIntensity());
        client.updateConfig(updated);
        onClose();
    }

    //? if >=26.1.2 {
    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                   float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int totalWidth = Math.min(width - 16, 420);
        int columnWidth = (totalWidth - 8) / 2;
        int left = (width - totalWidth) / 2;
        int right = left + columnWidth + 8;
        graphics.centeredText(font, title, width / 2, 16, 0xFFFFFFFF);
        graphics.centeredText(font, Component.translatable("minegasm.settings.gameplay"),
                left + columnWidth / 2, 34, 0xFFFFFFFF);
        graphics.centeredText(font, Component.translatable("minegasm.settings.connection"),
                right + columnWidth / 2, 34, 0xFFFFFFFF);
    }
    //?} elif >=1.20.1 {
    /*@Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        //? if <1.21.1 {
        /^this.renderBackground(graphics); // 1.20.1 Screen.render() paints no backdrop (1.21.1+'s does)
        ^///?}
        super.render(graphics, mouseX, mouseY, partialTick);
        int totalWidth = Math.min(width - 16, 420);
        int columnWidth = (totalWidth - 8) / 2;
        int left = (width - totalWidth) / 2;
        int right = left + columnWidth + 8;
        graphics.drawCenteredString(font, title, width / 2, 16, 0xFFFFFFFF);
        graphics.drawCenteredString(font, Component.translatable("minegasm.settings.gameplay"),
                left + columnWidth / 2, 34, 0xFFFFFFFF);
        graphics.drawCenteredString(font, Component.translatable("minegasm.settings.connection"),
                right + columnWidth / 2, 34, 0xFFFFFFFF);
    }
    *///?} else {
    /*@Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(poseStack); // pre-1.20 Screen.render() paints no backdrop either
        super.render(poseStack, mouseX, mouseY, partialTick);
        int totalWidth = Math.min(width - 16, 420);
        int columnWidth = (totalWidth - 8) / 2;
        int left = (width - totalWidth) / 2;
        int right = left + columnWidth + 8;
        // Pre-1.20 draw helpers are static on GuiComponent and take a PoseStack first.
        GuiComponent.drawCenteredString(poseStack, font, title, width / 2, 16, 0xFFFFFFFF);
        GuiComponent.drawCenteredString(poseStack, font, Component.translatable("minegasm.settings.gameplay"),
                left + columnWidth / 2, 34, 0xFFFFFFFF);
        GuiComponent.drawCenteredString(poseStack, font, Component.translatable("minegasm.settings.connection"),
                right + columnWidth / 2, 34, 0xFFFFFFFF);
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

    private static final class PercentSlider extends AbstractSliderButton {
        private final String key;
        private final DoubleConsumer consumer;

        PercentSlider(int x, int y, int width, int height, String key, double value,
                      DoubleConsumer consumer) {
            super(x, y, width, height, Component.empty(), value);
            this.key = key;
            this.consumer = consumer;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            if (key != null) {
                setMessage(Component.translatable(key, Math.round(value * 100) + "%"));
            }
        }

        @Override
        protected void applyValue() {
            if (consumer != null) {
                consumer.accept(value);
            }
        }
    }
}
