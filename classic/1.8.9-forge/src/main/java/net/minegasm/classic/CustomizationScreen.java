package net.minegasm.classic;

import net.minegasm.client.MinegasmClient;
import net.minegasm.config.MinegasmMode;
import net.minegasm.config.editor.CustomizationModel;
import net.minegasm.core.GameEventKind;
import net.minegasm.core.OutputKind;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fml.client.config.GuiSlider;

import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

/**
 * 1.8.9 mirror of the modern {@code MinegasmCustomizationScreen}: per-event enable/multiplier,
 * per-output-kind enable, the Buttplug reconnect policy, and (mode-gated) accumulation / custom-intensity
 * parameters. Backed by {@link CustomizationModel}. Rows page a screenful at a time via
 * {@link RowScroller}. Every button here is a {@link CallbackButton} rather than an id-switched
 * {@link GuiButton}, since rows are built dynamically from a scrolled list rather than a fixed set.
 *
 * <p>Sibling of the 1.12.2 and 1.7.10 customization screens. On 1.8.9 the font accessor is
 * {@code fontRendererObj} and widgets are added straight to {@code buttonList} (no {@code addButton}).
 *
 * <p>Sliders ({@link GuiSlider}) are not read via a click/drag callback: whether Forge's config
 * {@code GuiSlider} calls {@code actionPerformed} on every drag tick or only at release isn't something
 * this codebase already relies on anywhere, so rather than assume, every visible slider's current value
 * is pulled directly via {@link GuiSlider#getValue()} into the model right before the widget list is
 * rebuilt (a scroll) or the config is saved — both of which are certain to happen after any edit.
 */
public final class CustomizationScreen extends GuiScreen {

    private static final int ROW_HEIGHT = 22;

    private final GuiScreen parent;
    private final MinegasmClient client;
    private final CustomizationModel model;
    private final MinegasmMode mode;

    private List<Row> rows = new ArrayList<>();
    private List<VisibleLabel> visibleLabels = new ArrayList<>();
    private List<SliderBinding> sliderBindings = new ArrayList<>();
    private RowScroller scroller;

    public CustomizationScreen(GuiScreen parent) {
        this.parent = parent;
        this.client = ClassicClientHolder.get();
        this.model = new CustomizationModel(client.config().raw());
        this.mode = client.config().raw().profile().mode();
    }

    @Override
    public void initGui() {
        flushSliders();
        buttonList.clear();
        rows = buildRows();
        visibleLabels = new ArrayList<>();
        sliderBindings = new ArrayList<>();

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
        CallbackButton up = new CallbackButton(scrollX, viewportTop, scrollColumnWidth, 20, "^", () -> {
            scroller.up();
            initGui();
        });
        up.enabled = scroller.canScrollUp();
        buttonList.add(up);

        CallbackButton down = new CallbackButton(scrollX, viewportBottom - 20, scrollColumnWidth, 20,
                "v", () -> {
            scroller.down();
            initGui();
        });
        down.enabled = scroller.canScrollDown();
        buttonList.add(down);

        int half = (totalWidth - 4) / 2;
        buttonList.add(new CallbackButton(left, height - 24, half, 20, "Save", this::save));
        buttonList.add(new CallbackButton(left + half + 4, height - 24, half, 20, "Cancel",
                () -> mc.displayGuiScreen(parent)));
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button instanceof CallbackButton) {
            ((CallbackButton) button).press();
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel > 0 && scroller.canScrollUp()) {
            scroller.up();
            initGui();
        } else if (wheel < 0 && scroller.canScrollDown()) {
            scroller.down();
            initGui();
        }
    }

    private void flushSliders() {
        for (SliderBinding binding : sliderBindings) {
            binding.consumer.accept(binding.slider.getValue());
        }
    }

    private List<Row> buildRows() {
        List<Row> list = new ArrayList<>();

        list.add(Row.header("Events"));
        for (GameEventKind kind : CustomizationModel.EVENTS) {
            CustomizationModel.EventRow eventRow = model.events.get(kind);
            list.add(new Row(eventLabel(kind), (x, y, w, h) -> {
                int toggleWidth = 50;
                buttonList.add(toggle(x, y, toggleWidth, h, () -> eventRow.enabled,
                        v -> eventRow.enabled = v));
                addSlider(x + toggleWidth + 4, y, w - toggleWidth - 4, h, "x", 0.0, 4.0,
                        eventRow.multiplier, v -> eventRow.multiplier = v);
            }));
        }

        list.add(Row.header("Output kinds"));
        for (OutputKind kind : CustomizationModel.OUTPUT_KINDS) {
            list.add(new Row(outputLabel(kind), (x, y, w, h) ->
                    buttonList.add(toggle(x, y, w, h, () -> model.outputPolicy.get(kind),
                            v -> model.outputPolicy.put(kind, v)))));
        }

        list.add(Row.header("Reconnect"));
        list.add(new Row("Auto-reconnect", (x, y, w, h) -> buttonList.add(toggle(x, y, w, h,
                () -> model.reconnectEnabled, v -> model.reconnectEnabled = v))));
        list.add(new Row("Max reconnect delay", (x, y, w, h) -> buttonList.add(cycleInt(x, y, w, h,
                new int[] {10, 30, 60, 120, 300, 600}, model.reconnectMaxDelaySeconds,
                v -> model.reconnectMaxDelaySeconds = v))));

        if (mode == MinegasmMode.MOMENTUM) {
            list.add(Row.header("Accumulation (Momentum mode)"));
            list.add(new Row("Capacity", (x, y, w, h) -> addSlider(x, y, w, h, "", 10.0, 500.0,
                    model.accumulationCapacity, v -> model.accumulationCapacity = v)));
            list.add(new Row("Decay per second", (x, y, w, h) -> addSlider(x, y, w, h, "", 0.0, 10.0,
                    model.accumulationDecayPerSecond, v -> model.accumulationDecayPerSecond = v)));
            list.add(new Row("Output curve", (x, y, w, h) -> {
                CallbackButton[] holder = new CallbackButton[1];
                holder[0] = new CallbackButton(x, y, w, h, curveLabel(), () -> {
                    model.cycleAccumulationCurve();
                    holder[0].displayString = curveLabel();
                });
                buttonList.add(holder[0]);
            }));
            for (String key : new ArrayList<>(model.accumulationContributions.keySet())) {
                list.add(new Row("Contribution: " + key, (x, y, w, h) -> addSlider(x, y, w, h, "",
                        0.0, 20.0, model.accumulationContributions.get(key),
                        v -> model.accumulationContributions.put(key, v))));
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
        list.add(new Row(label, (x, y, w, h) -> addSlider(x, y, w, h, "", 0.0, 1.0,
                getter.getAsDouble(), setter)));
    }

    private void addSlider(int x, int y, int w, int h, String suffix, double min, double max,
                           double current, DoubleConsumer consumer) {
        GuiSlider slider = new GuiSlider(0, x, y, w, h, "", suffix, min, max, current, true, true);
        buttonList.add(slider);
        sliderBindings.add(new SliderBinding(slider, consumer));
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

    private String curveLabel() {
        return "Curve: " + model.accumulationCurve;
    }

    private CallbackButton toggle(int x, int y, int width, int height, BooleanSupplier getter,
                                  Consumer<Boolean> setter) {
        CallbackButton[] holder = new CallbackButton[1];
        holder[0] = new CallbackButton(x, y, width, height, toggleLabel(getter.getAsBoolean()), () -> {
            boolean value = !getter.getAsBoolean();
            setter.accept(value);
            holder[0].displayString = toggleLabel(value);
        });
        return holder[0];
    }

    private String toggleLabel(boolean value) {
        return value ? "ON" : "OFF";
    }

    private CallbackButton cycleInt(int x, int y, int width, int height, int[] values, int current,
                                    Consumer<Integer> setter) {
        CallbackButton[] holder = new CallbackButton[1];
        holder[0] = new CallbackButton(x, y, width, height, current + "s", () -> {
            int next = values[0];
            for (int i = 0; i < values.length; i++) {
                if (values[i] == current) {
                    next = values[(i + 1) % values.length];
                    break;
                }
            }
            setter.accept(next);
            holder[0].displayString = next + "s";
        });
        return holder[0];
    }

    private void save() {
        flushSliders();
        model.apply(client);
        mc.displayGuiScreen(parent);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);
        drawCenteredString(fontRendererObj, "Minegasm Customization", width / 2, 16, 0xFFFFFF);
        for (VisibleLabel label : visibleLabels) {
            drawString(fontRendererObj, label.text, label.x, label.y, 0xFFFFFF);
        }
    }

    @Override
    public void onGuiClosed() {
    }

    /** One row's build function: adds whatever widgets it needs at the given content-area bounds. */
    private interface RowBuilder {
        void build(int x, int y, int width, int height);
    }

    private static final class Row {
        final String label;
        final RowBuilder build;

        Row(String label, RowBuilder build) {
            this.label = label;
            this.build = build;
        }

        static Row header(String label) {
            return new Row(label, null);
        }
    }

    private static final class VisibleLabel {
        final String text;
        final int x;
        final int y;

        VisibleLabel(String text, int x, int y) {
            this.text = text;
            this.x = x;
            this.y = y;
        }
    }

    private static final class SliderBinding {
        final GuiSlider slider;
        final DoubleConsumer consumer;

        SliderBinding(GuiSlider slider, DoubleConsumer consumer) {
            this.slider = slider;
            this.consumer = consumer;
        }
    }
}
