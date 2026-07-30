package net.minegasm.neoforge;

import net.minegasm.client.MinegasmClient;
import net.minegasm.config.MinegasmMode;
import net.minegasm.config.editor.CustomizationModel;
import net.minegasm.core.GameEventKind;
import net.minegasm.core.OutputKind;

//? if >=26.1.2 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} elif >=1.20.1 {
/*import net.minecraft.client.gui.GuiGraphics;
*///?} else {
/*import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiComponent;
*///?}
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

/**
 * Editor for the config members no other screen exposes: per-event enable/multiplier, per-output-kind
 * enable, the Buttplug reconnect policy, and (mode-gated) accumulation / custom-intensity parameters.
 * Backed by {@link CustomizationModel}, which owns the rebuild-on-save discipline. Content routinely
 * exceeds one screenful, so rows page a screenful at a time via {@link RowScroller} rather than a fixed
 * two-column layout like {@link MinegasmSettingsScreen}.
 */
public final class MinegasmCustomizationScreen extends Screen {

    private static final int ROW_HEIGHT = 22;

    private final Screen parent;
    private final MinegasmClient client;
    private final CustomizationModel model;
    private final MinegasmMode mode;

    private List<Row> rows = new ArrayList<>();
    private List<VisibleLabel> visibleLabels = new ArrayList<>();
    private RowScroller scroller;

    public MinegasmCustomizationScreen(Screen parent, MinegasmClient client) {
        super(Component.translatable("minegasm.customization.title"));
        this.parent = parent;
        this.client = client;
        this.model = new CustomizationModel(client.config().raw());
        this.mode = client.config().raw().profile().mode();
    }

    @Override
    protected void init() {
        rows = buildRows();
        visibleLabels = new ArrayList<>();

        int totalWidth = Math.min(width - 16, 480);
        int left = (width - totalWidth) / 2;
        int scrollColumnWidth = 20;
        int contentWidth = totalWidth - scrollColumnWidth - 4;
        int viewportTop = 40;
        int viewportBottom = height - 32;
        int visibleRowCount = Math.max(1, (viewportBottom - viewportTop) / ROW_HEIGHT);

        if (scroller == null) {
            scroller = new RowScroller(visibleRowCount, rows.size());
        } else {
            scroller.resize(visibleRowCount, rows.size());
        }

        for (int i = 0; i < rows.size(); i++) {
            if (!scroller.isVisible(i)) {
                continue;
            }
            int slot = i - scroller.first();
            int y = viewportTop + slot * ROW_HEIGHT;
            Row row = rows.get(i);
            visibleLabels.add(new VisibleLabel(row.label(), left, y + 6));
            if (row.build != null) {
                row.build.build(left + 152, y, contentWidth - 152, ROW_HEIGHT - 4);
            }
        }

        int scrollX = left + totalWidth - scrollColumnWidth;
        Button up = addRenderableWidget(button(Component.literal("^"), b -> {
            scroller.up();
            rebuildWidgets();
        }, scrollX, viewportTop, scrollColumnWidth, 20));
        up.active = scroller.canScrollUp();

        Button down = addRenderableWidget(button(Component.literal("v"), b -> {
            scroller.down();
            rebuildWidgets();
        }, scrollX, viewportBottom - 20, scrollColumnWidth, 20));
        down.active = scroller.canScrollDown();

        int half = (totalWidth - 4) / 2;
        addRenderableWidget(button(Component.translatable("minegasm.settings.save"),
                b -> save(), left, height - 24, half, 20));
        addRenderableWidget(button(Component.translatable("gui.cancel"), b -> onClose(),
                left + half + 4, height - 24, half, 20));
    }

    private List<Row> buildRows() {
        List<Row> list = new ArrayList<>();

        list.add(Row.header("minegasm.customization.section.events"));
        for (GameEventKind kind : CustomizationModel.EVENTS) {
            CustomizationModel.EventRow eventRow = model.events.get(kind);
            list.add(new Row(eventLabel(kind), (x, y, w, h) -> {
                int toggleWidth = 50;
                addRenderableWidget(toggle(x, y, toggleWidth, h, () -> eventRow.enabled,
                        v -> eventRow.enabled = v));
                addRenderableWidget(new BoundedSlider(x + toggleWidth + 4, y,
                        w - toggleWidth - 4, h, "minegasm.customization.multiplier", 0.0, 4.0, 2,
                        eventRow.multiplier, v -> eventRow.multiplier = v));
            }));
        }

        list.add(Row.header("minegasm.customization.section.output"));
        for (OutputKind kind : CustomizationModel.OUTPUT_KINDS) {
            list.add(new Row(outputLabel(kind), (x, y, w, h) ->
                    addRenderableWidget(toggle(x, y, w, h, () -> model.outputPolicy.get(kind),
                            v -> model.outputPolicy.put(kind, v)))));
        }

        list.add(Row.header("minegasm.customization.section.reconnect"));
        list.add(new Row(Component.translatable("minegasm.customization.reconnect.enabled"),
                (x, y, w, h) -> addRenderableWidget(toggle(x, y, w, h, () -> model.reconnectEnabled,
                        v -> model.reconnectEnabled = v))));
        list.add(new Row(Component.translatable("minegasm.customization.reconnect.max_delay"),
                (x, y, w, h) -> addRenderableWidget(cycleInt(x, y, w, h,
                        "minegasm.customization.reconnect.max_delay_value",
                        new int[] {10, 30, 60, 120, 300, 600},
                        model.reconnectMaxDelaySeconds,
                        v -> model.reconnectMaxDelaySeconds = v))));

        if (mode == MinegasmMode.MOMENTUM) {
            list.add(Row.header("minegasm.customization.section.accumulation"));
            list.add(new Row(Component.translatable("minegasm.customization.accumulation.capacity"),
                    (x, y, w, h) -> addRenderableWidget(new BoundedSlider(x, y, w, h,
                            "minegasm.customization.value", 10.0, 500.0, 0,
                            model.accumulationCapacity, v -> model.accumulationCapacity = v))));
            list.add(new Row(Component.translatable("minegasm.customization.accumulation.decay"),
                    (x, y, w, h) -> addRenderableWidget(new BoundedSlider(x, y, w, h,
                            "minegasm.customization.value", 0.0, 10.0, 2,
                            model.accumulationDecayPerSecond,
                            v -> model.accumulationDecayPerSecond = v))));
            list.add(new Row(Component.translatable("minegasm.customization.accumulation.curve"),
                    (x, y, w, h) -> addRenderableWidget(button(curveLabel(), b -> {
                        model.cycleAccumulationCurve();
                        b.setMessage(curveLabel());
                    }, x, y, w, h))));
            for (String key : new ArrayList<>(model.accumulationContributions.keySet())) {
                list.add(new Row(Component.translatable(
                        "minegasm.customization.accumulation.contribution", key),
                        (x, y, w, h) -> addRenderableWidget(new BoundedSlider(x, y, w, h,
                                "minegasm.customization.value", 0.0, 20.0, 2,
                                model.accumulationContributions.get(key),
                                v -> model.accumulationContributions.put(key, v)))));
            }
        } else if (mode == MinegasmMode.CUSTOM) {
            list.add(Row.header("minegasm.customization.section.custom_intensity"));
            addCustomIntensityRow(list, "attack", () -> model.customAttack, v -> model.customAttack = v);
            addCustomIntensityRow(list, "hurt", () -> model.customHurt, v -> model.customHurt = v);
            addCustomIntensityRow(list, "mine", () -> model.customMine, v -> model.customMine = v);
            addCustomIntensityRow(list, "place", () -> model.customPlace, v -> model.customPlace = v);
            addCustomIntensityRow(list, "xp_change", () -> model.customXpChange,
                    v -> model.customXpChange = v);
            addCustomIntensityRow(list, "fishing", () -> model.customFishing,
                    v -> model.customFishing = v);
            addCustomIntensityRow(list, "harvest", () -> model.customHarvest,
                    v -> model.customHarvest = v);
            addCustomIntensityRow(list, "vitality", () -> model.customVitality,
                    v -> model.customVitality = v);
            addCustomIntensityRow(list, "advancement", () -> model.customAdvancement,
                    v -> model.customAdvancement = v);
        }

        return list;
    }

    private void addCustomIntensityRow(List<Row> list, String key, DoubleSupplier getter,
                                       DoubleConsumer setter) {
        list.add(new Row(Component.translatable("minegasm.customization.custom_intensity." + key),
                (x, y, w, h) -> addRenderableWidget(new BoundedSlider(x, y, w, h,
                        "minegasm.customization.value", 0.0, 1.0, 2, getter.getAsDouble(), setter))));
    }

    private Component eventLabel(GameEventKind kind) {
        return Component.translatable("minegasm.customization.events." + kind.configKey());
    }

    private Component outputLabel(OutputKind kind) {
        return Component.translatable("minegasm.customization.output." + kind.wireName());
    }

    private Component curveLabel() {
        return Component.translatable("minegasm.customization.accumulation.curve_value",
                model.accumulationCurve);
    }

    private Button toggle(int x, int y, int width, int height, BooleanSupplier getter,
                          Consumer<Boolean> setter) {
        return button(toggleLabel(getter.getAsBoolean()), b -> {
            boolean value = !getter.getAsBoolean();
            setter.accept(value);
            b.setMessage(toggleLabel(value));
        }, x, y, width, height);
    }

    private Component toggleLabel(boolean value) {
        return Component.translatable(value ? "options.on" : "options.off");
    }

    private Button cycleInt(int x, int y, int width, int height, String key, int[] values,
                            int current, Consumer<Integer> setter) {
        return button(Component.translatable(key, current), b -> {
            int next = values[0];
            for (int i = 0; i < values.length; i++) {
                if (values[i] == current) {
                    next = values[(i + 1) % values.length];
                    break;
                }
            }
            setter.accept(next);
            b.setMessage(Component.translatable(key, next));
        }, x, y, width, height);
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

    private void save() {
        model.apply(client);
        onClose();
    }

    // Wheel-up steps toward the top of the list, matching how every other scrollable list in this mod
    // behaves. The 4-arg overload with a horizontal scrollX component was added in 1.21.1; 1.19.2 and
    // 1.20.1 both still take a single delta (this boundary does not line up with the render-era guards
    // used elsewhere in this file, so it needs its own).
    //? if >=1.21.1 {
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY > 0 && scroller.canScrollUp()) {
            scroller.up();
            rebuildWidgets();
            return true;
        }
        if (scrollY < 0 && scroller.canScrollDown()) {
            scroller.down();
            rebuildWidgets();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
    //?} else {
    /*@Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (delta > 0 && scroller.canScrollUp()) {
            scroller.up();
            rebuildWidgets();
            return true;
        }
        if (delta < 0 && scroller.canScrollDown()) {
            scroller.down();
            rebuildWidgets();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }
    *///?}

    //? if >=26.1.2 {
    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                   float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(font, title, width / 2, 16, 0xFFFFFFFF);
        for (VisibleLabel label : visibleLabels) {
            graphics.text(font, label.text, label.x, label.y, 0xFFFFFFFF);
        }
    }
    //?} elif >=1.20.1 {
    /*@Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        //? if <1.21.1 {
        /^this.renderBackground(graphics);
        ^///?}
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 16, 0xFFFFFFFF);
        for (VisibleLabel label : visibleLabels) {
            graphics.drawString(font, label.text, label.x, label.y, 0xFFFFFFFF);
        }
    }
    *///?} else {
    /*@Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(poseStack);
        super.render(poseStack, mouseX, mouseY, partialTick);
        GuiComponent.drawCenteredString(poseStack, font, title, width / 2, 16, 0xFFFFFFFF);
        for (VisibleLabel label : visibleLabels) {
            GuiComponent.drawString(poseStack, font, label.text, label.x, label.y, 0xFFFFFFFF);
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

    /** One row's build function: adds whatever widgets it needs at the given content-area bounds. */
    private interface RowBuilder {
        void build(int x, int y, int width, int height);
    }

    private static final class Row {
        final Component label;
        final RowBuilder build;

        Row(Component label, RowBuilder build) {
            this.label = label;
            this.build = build;
        }

        static Row header(String key) {
            return new Row(Component.translatable(key), null);
        }

        Component label() {
            return label;
        }
    }

    private static final class VisibleLabel {
        final Component text;
        final int x;
        final int y;

        VisibleLabel(Component text, int x, int y) {
            this.text = text;
            this.x = x;
            this.y = y;
        }
    }
}
