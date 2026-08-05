package net.minegasm.neoforge;

import net.minegasm.client.MinegasmClient;
import net.minegasm.config.editor.DeviceEditorModel;

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

/**
 * Editor for the per-device settings no other screen exposes: {@code devices} (enable, intensity cap,
 * per-feature enable/multiplier) and {@code positionCalibrations} (stroker calibration), both keyed by
 * {@link net.minegasm.device.HapticDevice#identityKey()}. Backed by {@link DeviceEditorModel}, built
 * once from the currently-known device list ({@code client.provider().devices().all()}) when the screen
 * opens; a device not currently connected simply has no rows here and its saved settings are left alone
 * by the model on save (see {@link DeviceEditorModel}'s class doc).
 */
public final class MinegasmDeviceEditorScreen extends Screen {

    private static final int ROW_HEIGHT = 22;

    private final Screen parent;
    private final MinegasmClient client;
    private final DeviceEditorModel model;

    private List<Row> rows = new ArrayList<>();
    private List<VisibleLabel> visibleLabels = new ArrayList<>();
    private RowScroller scroller;

    public MinegasmDeviceEditorScreen(Screen parent, MinegasmClient client) {
        super(Component.translatable("minegasm.devices.editor.title"));
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
        List<DeviceEditorModel.DeviceRow> deviceRows = model.rows();

        if (deviceRows.isEmpty()) {
            list.add(new Row(Component.translatable("minegasm.devices.none"), null));
            return list;
        }

        for (DeviceEditorModel.DeviceRow device : deviceRows) {
            list.add(new Row(Component.literal(device.label), null));

            list.add(new Row(Component.translatable("minegasm.devices.enable"), (x, y, w, h) -> {
                int toggleWidth = 50;
                addRenderableWidget(toggle(x, y, toggleWidth, h, () -> device.enabled, v -> {
                    device.enabled = v;
                    device.deviceTouched = true;
                }));
                addRenderableWidget(new BoundedSlider(x + toggleWidth + 4, y, w - toggleWidth - 4, h,
                        "minegasm.devices.editor.cap_value", 0.0, 1.0, 2, device.maxLevel, v -> {
                            device.maxLevel = v;
                            device.deviceTouched = true;
                        }));
            }));

            list.add(new Row(Component.literal(""), (x, y, w, h) ->
                    addRenderableWidget(new BoundedSlider(x, y, w, h,
                            "minegasm.devices.editor.min_value", 0.0, 1.0, 2, device.minLevel, v -> {
                                device.minLevel = v;
                                device.deviceTouched = true;
                            }))));

            for (DeviceEditorModel.FeatureRow feature : device.features) {
                Component featureLabel = Component.literal(
                        feature.description + " (" + feature.kind.wireName() + ")");
                list.add(new Row(featureLabel, (x, y, w, h) -> {
                    int toggleWidth = 50;
                    addRenderableWidget(toggle(x, y, toggleWidth, h, () -> feature.enabled, v -> {
                        feature.enabled = v;
                        device.deviceTouched = true;
                    }));
                    addRenderableWidget(new BoundedSlider(x + toggleWidth + 4, y,
                            w - toggleWidth - 4, h, "minegasm.devices.editor.feature_multiplier",
                            0.0, 2.0, 2, feature.multiplier, v -> {
                                feature.multiplier = v;
                                device.deviceTouched = true;
                            }));
                }));
            }

            if (device.calibrationApplies) {
                DeviceEditorModel.PositionCalibrationRow calibration = device.calibration;
                list.add(Row.header("minegasm.screen.calibration"));
                list.add(new Row(Component.translatable("minegasm.devices.editor.calibration.enabled"),
                        (x, y, w, h) -> addRenderableWidget(toggle(x, y, w, h,
                                () -> calibration.enabled, v -> {
                                    calibration.enabled = v;
                                    device.calibrationTouched = true;
                                }))));
                list.add(calibrationSlider(device, calibration,
                        "minegasm.devices.editor.calibration.minimum", 0.0, 1.0,
                        () -> calibration.minimum, v -> calibration.minimum = v));
                list.add(calibrationSlider(device, calibration,
                        "minegasm.devices.editor.calibration.maximum", 0.0, 1.0,
                        () -> calibration.maximum, v -> calibration.maximum = v));
                list.add(calibrationSlider(device, calibration,
                        "minegasm.devices.editor.calibration.neutral", 0.0, 1.0,
                        () -> calibration.neutral, v -> calibration.neutral = v));
                list.add(new Row(Component.translatable("minegasm.devices.editor.calibration.invert"),
                        (x, y, w, h) -> addRenderableWidget(toggle(x, y, w, h,
                                () -> calibration.invert, v -> {
                                    calibration.invert = v;
                                    device.calibrationTouched = true;
                                }))));
                list.add(calibrationSlider(device, calibration,
                        "minegasm.devices.editor.calibration.travel_fraction", 0.0, 0.20,
                        () -> calibration.gameplayTravelFraction,
                        v -> calibration.gameplayTravelFraction = v));
                list.add(new Row(
                        Component.translatable("minegasm.devices.editor.calibration.require_neutral"),
                        (x, y, w, h) -> addRenderableWidget(toggle(x, y, w, h,
                                () -> calibration.requireReturnToNeutral, v -> {
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
                                  DeviceEditorModel.PositionCalibrationRow calibration, String key,
                                  double min, double max, DoubleGetter getter, DoubleSetter setter) {
        return new Row(Component.translatable(key), (x, y, w, h) ->
                addRenderableWidget(new BoundedSlider(x, y, w, h, "minegasm.customization.value",
                        min, max, 2, getter.get(), v -> {
                            setter.set(v);
                            device.calibrationTouched = true;
                        })));
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

    // Button.builder(...) was added in 1.19.4; 1.19.2 constructs Button directly. One guarded factory
    // keeps every call site version-agnostic (message, action, then bounds as x/y/width/height).
    private Button button(Component message, Button.OnPress onPress, int x, int y, int width, int height) {
        //? if >=1.20.1 {
        return Button.builder(message, onPress).bounds(x, y, width, height).build();
        //?} else {
        /*return new Button(x, y, width, height, message, onPress);
        *///?}
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

    private void save() {
        model.apply(client);
        onClose();
    }

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
