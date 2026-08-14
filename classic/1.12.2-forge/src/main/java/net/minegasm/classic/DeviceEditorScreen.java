package net.minegasm.classic;

import net.minegasm.client.MinegasmClient;
import net.minegasm.config.editor.DeviceEditorModel;

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

/**
 * 1.12.2 mirror of the modern {@code MinegasmDeviceEditorScreen}: per-device enable/intensity cap/
 * per-feature settings, and stroker calibration, both keyed by
 * {@link net.minegasm.device.HapticDevice#identityKey()}. Backed by {@link DeviceEditorModel}. See
 * {@link CustomizationScreen}'s class doc for why every button is a {@link CallbackButton} and why
 * sliders are flushed rather than callback-driven.
 */
public final class DeviceEditorScreen extends GuiScreen {

    private static final int ROW_HEIGHT = 22;

    private final GuiScreen parent;
    private final MinegasmClient client;
    private final DeviceEditorModel model;

    private List<Row> rows = new ArrayList<>();
    private List<VisibleLabel> visibleLabels = new ArrayList<>();
    private List<SliderBinding> sliderBindings = new ArrayList<>();
    private RowScroller scroller;

    public DeviceEditorScreen(GuiScreen parent) {
        this.parent = parent;
        this.client = ClassicClientHolder.get();
        this.model = new DeviceEditorModel(client.config().raw(), client.provider().devices().all());
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
                row.build.build(left + 176, y, contentWidth - 176, ROW_HEIGHT - 4);
            }
        }

        int scrollX = left + totalWidth - scrollColumnWidth;
        CallbackButton up = new CallbackButton(scrollX, viewportTop, scrollColumnWidth, 20, "^", () -> {
            scroller.up();
            initGui();
        });
        up.enabled = scroller.canScrollUp();
        addButton(up);

        CallbackButton down = new CallbackButton(scrollX, viewportBottom - 20, scrollColumnWidth, 20,
                "v", () -> {
            scroller.down();
            initGui();
        });
        down.enabled = scroller.canScrollDown();
        addButton(down);

        int half = (totalWidth - 4) / 2;
        addButton(new CallbackButton(left, height - 24, half, 20, "Save", this::save));
        addButton(new CallbackButton(left + half + 4, height - 24, half, 20, "Cancel",
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
        List<DeviceEditorModel.DeviceRow> deviceRows = model.rows();

        if (deviceRows.isEmpty()) {
            list.add(new Row("No devices. Start Intiface and scan.", null));
            return list;
        }

        for (DeviceEditorModel.DeviceRow device : deviceRows) {
            list.add(new Row(device.label, null));

            list.add(new Row("Enable", (x, y, w, h) -> {
                int toggleWidth = 50;
                addButton(toggle(x, y, toggleWidth, h, () -> device.enabled, v -> {
                    device.enabled = v;
                    device.deviceTouched = true;
                }));
                addSlider(x + toggleWidth + 4, y, w - toggleWidth - 4, h, "Cap: ", "", 0.0, 1.0,
                        device.maxLevel, v -> {
                            device.maxLevel = v;
                            device.deviceTouched = true;
                        });
            }));

            list.add(new Row("", (x, y, w, h) ->
                    addSlider(x, y, w, h, "Start threshold: ", "", 0.0, 1.0, device.minLevel, v -> {
                        device.minLevel = v;
                        device.deviceTouched = true;
                    })));

            boolean firstRegion = true;
            for (net.minegasm.core.BodyRegion choice : DeviceEditorModel.regionChoices()) {
                String label = (device.region == choice ? "[x] " : "[ ] ")
                        + DeviceEditorModel.regionLabel(choice);
                list.add(new Row(firstRegion ? "Body region" : "", (x, y, w, h) ->
                        addButton(new CallbackButton(x, y, w, h, label, () -> {
                            device.region = choice;
                            device.deviceTouched = true;
                            initGui();
                        }))));
                firstRegion = false;
            }

            for (DeviceEditorModel.FeatureRow feature : device.features) {
                String featureLabel = feature.description + " (" + feature.kind.wireName() + ")";
                list.add(new Row(featureLabel, (x, y, w, h) -> {
                    int toggleWidth = 50;
                    addButton(toggle(x, y, toggleWidth, h, () -> feature.enabled, v -> {
                        feature.enabled = v;
                        device.deviceTouched = true;
                    }));
                    addSlider(x + toggleWidth + 4, y, w - toggleWidth - 4, h, "", "x", 0.0, 2.0,
                            feature.multiplier, v -> {
                                feature.multiplier = v;
                                device.deviceTouched = true;
                            });
                }));
            }

            if (device.calibrationApplies) {
                DeviceEditorModel.PositionCalibrationRow calibration = device.calibration;
                list.add(Row.header("Position Calibration (experimental)"));
                list.add(new Row("Calibration enabled", (x, y, w, h) -> addButton(toggle(x, y, w, h,
                        () -> calibration.enabled, v -> {
                            calibration.enabled = v;
                            device.calibrationTouched = true;
                        }))));
                list.add(calibrationSlider(device, calibration, "Minimum", 0.0, 1.0,
                        () -> calibration.minimum, v -> calibration.minimum = v));
                list.add(calibrationSlider(device, calibration, "Maximum", 0.0, 1.0,
                        () -> calibration.maximum, v -> calibration.maximum = v));
                list.add(calibrationSlider(device, calibration, "Neutral", 0.0, 1.0,
                        () -> calibration.neutral, v -> calibration.neutral = v));
                list.add(new Row("Invert", (x, y, w, h) -> addButton(toggle(x, y, w, h,
                        () -> calibration.invert, v -> {
                            calibration.invert = v;
                            device.calibrationTouched = true;
                        }))));
                list.add(calibrationSlider(device, calibration, "Gameplay travel", 0.0, 0.20,
                        () -> calibration.gameplayTravelFraction,
                        v -> calibration.gameplayTravelFraction = v));
                list.add(new Row("Require return to neutral", (x, y, w, h) -> addButton(toggle(
                        x, y, w, h, () -> calibration.requireReturnToNeutral, v -> {
                            calibration.requireReturnToNeutral = v;
                            device.calibrationTouched = true;
                        }))));
            }
        }

        return list;
    }

    private interface DoubleGetter {
        double get();
    }

    private interface DoubleSetter {
        void set(double value);
    }

    private Row calibrationSlider(DeviceEditorModel.DeviceRow device,
                                  DeviceEditorModel.PositionCalibrationRow calibration, String label,
                                  double min, double max, DoubleGetter getter, DoubleSetter setter) {
        return new Row(label, (x, y, w, h) -> addSlider(x, y, w, h, "", "", min, max, getter.get(), v -> {
            setter.set(v);
            device.calibrationTouched = true;
        }));
    }

    private void addSlider(int x, int y, int w, int h, String prefix, String suffix, double min,
                           double max, double current, DoubleConsumer consumer) {
        GuiSlider slider = new GuiSlider(0, x, y, w, h, prefix, suffix, min, max, current, true, true);
        addButton(slider);
        sliderBindings.add(new SliderBinding(slider, consumer));
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

    private void save() {
        flushSliders();
        model.apply(client);
        mc.displayGuiScreen(parent);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);
        drawCenteredString(fontRenderer, "Minegasm Device Editor", width / 2, 16, 0xFFFFFF);
        for (VisibleLabel label : visibleLabels) {
            drawString(fontRenderer, label.text, label.x, label.y, 0xFFFFFF);
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
