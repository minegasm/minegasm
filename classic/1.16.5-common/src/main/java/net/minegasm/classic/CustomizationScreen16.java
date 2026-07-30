package net.minegasm.classic;

import net.minegasm.client.MinegasmClient;
import net.minegasm.config.MinegasmMode;
import net.minegasm.config.editor.CustomizationModel;
import net.minegasm.core.GameEventKind;
import net.minegasm.core.OutputKind;

import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;

import com.mojang.blaze3d.vertex.PoseStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

/**
 * 1.16.5 mirror of the modern {@code MinegasmCustomizationScreen}: per-event enable/multiplier,
 * per-output-kind enable, the Buttplug reconnect policy, and (mode-gated) accumulation / custom-intensity
 * parameters. Backed by {@link CustomizationModel}. Rows page a screenful at a time via
 * {@link RowScroller} rather than a fixed layout, matching the modern screen's approach (see its class
 * doc for why: content routinely exceeds one screenful).
 */
public final class CustomizationScreen16 extends Screen {

    private static final int ROW_HEIGHT = 22;

    private final Screen parent;
    private final MinegasmClient client;
    private final CustomizationModel model;
    private final MinegasmMode mode;

    private List<Row> rows = new ArrayList<>();
    private List<VisibleLabel> visibleLabels = new ArrayList<>();
    private RowScroller scroller;

    public CustomizationScreen16(Screen parent, MinegasmClient client) {
        super(new TextComponent("Minegasm Customization"));
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
            visibleLabels.add(new VisibleLabel(row.label, left, y + 6));
            if (row.build != null) {
                row.build.build(left + 152, y, contentWidth - 152, ROW_HEIGHT - 4);
            }
        }

        int scrollX = left + totalWidth - scrollColumnWidth;
        Button up = addButton(new Button(scrollX, viewportTop, scrollColumnWidth, 20,
                new TextComponent("^"), b -> {
            scroller.up();
            rebuild();
        }));
        up.active = scroller.canScrollUp();

        Button down = addButton(new Button(scrollX, viewportBottom - 20, scrollColumnWidth, 20,
                new TextComponent("v"), b -> {
            scroller.down();
            rebuild();
        }));
        down.active = scroller.canScrollDown();

        int half = (totalWidth - 4) / 2;
        addButton(new Button(left, height - 24, half, 20, new TextComponent("Save"), b -> save()));
        addButton(new Button(left + half + 4, height - 24, half, 20, new TextComponent("Cancel"),
                b -> onClose()));
    }

    private void rebuild() {
        buttons.clear();
        children.clear();
        init();
    }

    private List<Row> buildRows() {
        List<Row> list = new ArrayList<>();

        list.add(Row.header("Events"));
        for (GameEventKind kind : CustomizationModel.EVENTS) {
            CustomizationModel.EventRow eventRow = model.events.get(kind);
            list.add(new Row(eventLabel(kind), (x, y, w, h) -> {
                int toggleWidth = 50;
                addButton(toggle(x, y, toggleWidth, h, () -> eventRow.enabled,
                        v -> eventRow.enabled = v));
                addButton(new BoundedSlider16(x + toggleWidth + 4, y, w - toggleWidth - 4, h,
                        "x", 0.0, 4.0, 2, eventRow.multiplier, v -> eventRow.multiplier = v));
            }));
        }

        list.add(Row.header("Output kinds"));
        for (OutputKind kind : CustomizationModel.OUTPUT_KINDS) {
            list.add(new Row(outputLabel(kind), (x, y, w, h) ->
                    addButton(toggle(x, y, w, h, () -> model.outputPolicy.get(kind),
                            v -> model.outputPolicy.put(kind, v)))));
        }

        list.add(Row.header("Reconnect"));
        list.add(new Row("Auto-reconnect", (x, y, w, h) -> addButton(toggle(x, y, w, h,
                () -> model.reconnectEnabled, v -> model.reconnectEnabled = v))));
        list.add(new Row("Max reconnect delay", (x, y, w, h) -> addButton(cycleInt(x, y, w, h,
                new int[] {10, 30, 60, 120, 300, 600}, model.reconnectMaxDelaySeconds,
                v -> model.reconnectMaxDelaySeconds = v))));

        if (mode == MinegasmMode.MOMENTUM) {
            list.add(Row.header("Accumulation (Momentum mode)"));
            list.add(new Row("Capacity", (x, y, w, h) -> addButton(new BoundedSlider16(x, y, w, h,
                    "", 10.0, 500.0, 0, model.accumulationCapacity,
                    v -> model.accumulationCapacity = v))));
            list.add(new Row("Decay per second", (x, y, w, h) -> addButton(new BoundedSlider16(
                    x, y, w, h, "", 0.0, 10.0, 2, model.accumulationDecayPerSecond,
                    v -> model.accumulationDecayPerSecond = v))));
            list.add(new Row("Output curve", (x, y, w, h) -> addButton(new Button(x, y, w, h,
                    curveLabel(), b -> {
                        model.cycleAccumulationCurve();
                        b.setMessage(curveLabel());
                    }))));
            for (String key : new ArrayList<>(model.accumulationContributions.keySet())) {
                list.add(new Row("Contribution: " + key, (x, y, w, h) -> addButton(new BoundedSlider16(
                        x, y, w, h, "", 0.0, 20.0, 2, model.accumulationContributions.get(key),
                        v -> model.accumulationContributions.put(key, v)))));
            }
        } else if (mode == MinegasmMode.CUSTOM) {
            list.add(Row.header("Custom intensities (Custom mode)"));
            addCustomIntensityRow(list, "Attack", () -> model.customAttack, v -> model.customAttack = v);
            addCustomIntensityRow(list, "Hurt", () -> model.customHurt, v -> model.customHurt = v);
            addCustomIntensityRow(list, "Mine", () -> model.customMine, v -> model.customMine = v);
            addCustomIntensityRow(list, "Place", () -> model.customPlace, v -> model.customPlace = v);
            addCustomIntensityRow(list, "XP gain", () -> model.customXpChange,
                    v -> model.customXpChange = v);
            addCustomIntensityRow(list, "Fishing", () -> model.customFishing,
                    v -> model.customFishing = v);
            addCustomIntensityRow(list, "Harvest", () -> model.customHarvest,
                    v -> model.customHarvest = v);
            addCustomIntensityRow(list, "Vitality", () -> model.customVitality,
                    v -> model.customVitality = v);
            addCustomIntensityRow(list, "Advancement", () -> model.customAdvancement,
                    v -> model.customAdvancement = v);
        }

        return list;
    }

    private void addCustomIntensityRow(List<Row> list, String label, DoubleSupplier getter,
                                       DoubleConsumer setter) {
        list.add(new Row(label, (x, y, w, h) -> addButton(new BoundedSlider16(x, y, w, h, "",
                0.0, 1.0, 2, getter.getAsDouble(), setter))));
    }

    private static String eventLabel(GameEventKind kind) {
        switch (kind) {
            case ATTACK: return "Attack";
            case HURT: return "Hurt";
            case MINING_ACTIVE: return "Mining";
            case BLOCK_BROKEN: return "Block break";
            case PLACE: return "Place";
            case HARVEST: return "Harvest";
            case FISHING_BITE: return "Fishing bite";
            case XP_GAIN: return "XP gain";
            case ADVANCEMENT: return "Advancement";
            case VITALITY: return "Vitality";
            case EXPLOSION: return "Explosion";
            default: return kind.key();
        }
    }

    private static String outputLabel(OutputKind kind) {
        switch (kind) {
            case VIBRATE: return "Vibrate";
            case OSCILLATE: return "Oscillate";
            case ROTATE: return "Rotate";
            case POSITION: return "Position (stroker)";
            case HW_POSITION_WITH_DURATION: return "Timed move (stroker)";
            default: return kind.wireName();
        }
    }

    private Component curveLabel() {
        return new TextComponent("Curve: " + model.accumulationCurve);
    }

    private Button toggle(int x, int y, int width, int height, BooleanSupplier getter,
                          Consumer<Boolean> setter) {
        return new Button(x, y, width, height, toggleLabel(getter.getAsBoolean()), b -> {
            boolean value = !getter.getAsBoolean();
            setter.accept(value);
            b.setMessage(toggleLabel(value));
        });
    }

    private Component toggleLabel(boolean value) {
        return new TextComponent(value ? "ON" : "OFF");
    }

    private Button cycleInt(int x, int y, int width, int height, int[] values, int current,
                            Consumer<Integer> setter) {
        return new Button(x, y, width, height, new TextComponent(current + "s"), b -> {
            int next = values[0];
            for (int i = 0; i < values.length; i++) {
                if (values[i] == current) {
                    next = values[(i + 1) % values.length];
                    break;
                }
            }
            setter.accept(next);
            b.setMessage(new TextComponent(next + "s"));
        });
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (delta > 0 && scroller.canScrollUp()) {
            scroller.up();
            rebuild();
            return true;
        }
        if (delta < 0 && scroller.canScrollDown()) {
            scroller.down();
            rebuild();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private void save() {
        model.apply(client);
        onClose();
    }

    @Override
    public void render(PoseStack pose, int mouseX, int mouseY, float partialTick) {
        renderBackground(pose);
        super.render(pose, mouseX, mouseY, partialTick);
        GuiComponent.drawCenteredString(pose, font, title, width / 2, 16, 0xFFFFFF);
        for (VisibleLabel label : visibleLabels) {
            GuiComponent.drawString(pose, font, label.text, label.x, label.y, 0xFFFFFF);
        }
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    /** One row's build function: adds whatever widgets it needs at the given content-area bounds. */
    private interface RowBuilder {
        void build(int x, int y, int width, int height);
    }

    private static final class Row {
        final Component label;
        final RowBuilder build;

        Row(String label, RowBuilder build) {
            this(new TextComponent(label), build);
        }

        Row(Component label, RowBuilder build) {
            this.label = label;
            this.build = build;
        }

        static Row header(String label) {
            return new Row(label, null);
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
