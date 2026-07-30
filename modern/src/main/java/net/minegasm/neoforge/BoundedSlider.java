package net.minegasm.neoforge;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

import java.util.function.DoubleConsumer;

/**
 * A slider over an arbitrary {@code [min, max]} range, unlike {@code MinegasmSettingsScreen}'s private
 * {@code PercentSlider} which is hardwired to {@code [0, 1]}. Used by the Customization and Device
 * Editor screens, whose fields (event multiplier {@code [0,4]}, feature multiplier {@code [0,2]},
 * calibration travel fraction {@code [0,0.20]}, etc.) don't fit a plain percentage.
 */
final class BoundedSlider extends AbstractSliderButton {
    private final String key;
    private final double min;
    private final double max;
    private final int decimals;
    private final DoubleConsumer consumer;

    BoundedSlider(int x, int y, int width, int height, String key, double min, double max,
                  int decimals, double value, DoubleConsumer consumer) {
        super(x, y, width, height, Component.empty(), toSlider(value, min, max));
        this.key = key;
        this.min = min;
        this.max = max;
        this.decimals = decimals;
        this.consumer = consumer;
        updateMessage();
    }

    private static double toSlider(double value, double min, double max) {
        if (max <= min) {
            return 0;
        }
        double v = (value - min) / (max - min);
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }

    private double fromSlider() {
        return min + value * (max - min);
    }

    @Override
    protected void updateMessage() {
        if (key != null) {
            setMessage(Component.translatable(key, format(fromSlider())));
        }
    }

    private String format(double v) {
        return String.format(java.util.Locale.ROOT, "%." + decimals + "f", v);
    }

    @Override
    protected void applyValue() {
        if (consumer != null) {
            consumer.accept(fromSlider());
        }
    }
}
