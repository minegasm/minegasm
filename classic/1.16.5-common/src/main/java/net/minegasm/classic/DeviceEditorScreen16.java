package net.minegasm.classic;

import net.minegasm.client.MinegasmClient;
import net.minegasm.config.editor.DeviceEditorModel;

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

/**
 * 1.16.5 mirror of the modern {@code MinegasmDeviceEditorScreen}: per-device enable/intensity cap/
 * per-feature settings, and stroker calibration, both keyed by
 * {@link net.minegasm.device.HapticDevice#identityKey()}. Backed by {@link DeviceEditorModel}, built
 * once from the currently-known device list when the screen opens.
 */
public final class DeviceEditorScreen16 extends Screen {

    private static final int ROW_HEIGHT = 22;

    private final Screen parent;
    private final MinegasmClient client;
    private final DeviceEditorModel model;

    private List<Row> rows = new ArrayList<>();
    private List<VisibleLabel> visibleLabels = new ArrayList<>();
    private RowScroller scroller;

    public DeviceEditorScreen16(Screen parent, MinegasmClient client) {
        super(new TextComponent("Minegasm Device Editor"));
        this.parent = parent;
        this.client = client;
        this.model = new DeviceEditorModel(client.config().raw(), client.provider().devices().all());
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
                row.build.build(left + 176, y, contentWidth - 176, ROW_HEIGHT - 4);
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
                addButton(new BoundedSlider16(x + toggleWidth + 4, y, w - toggleWidth - 4, h,
                        "Cap: ", 0.0, 1.0, 2, device.maxLevel, v -> {
                            device.maxLevel = v;
                            device.deviceTouched = true;
                        }));
            }));

            for (DeviceEditorModel.FeatureRow feature : device.features) {
                String featureLabel = feature.description + " (" + feature.kind.wireName() + ")";
                list.add(new Row(featureLabel, (x, y, w, h) -> {
                    int toggleWidth = 50;
                    addButton(toggle(x, y, toggleWidth, h, () -> feature.enabled, v -> {
                        feature.enabled = v;
                        device.deviceTouched = true;
                    }));
                    addButton(new BoundedSlider16(x + toggleWidth + 4, y, w - toggleWidth - 4, h,
                            "x", 0.0, 2.0, 2, feature.multiplier, v -> {
                                feature.multiplier = v;
                                device.deviceTouched = true;
                            }));
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
        return new Row(label, (x, y, w, h) -> addButton(new BoundedSlider16(x, y, w, h, "", min, max, 2,
                getter.get(), v -> {
                    setter.set(v);
                    device.calibrationTouched = true;
                })));
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
