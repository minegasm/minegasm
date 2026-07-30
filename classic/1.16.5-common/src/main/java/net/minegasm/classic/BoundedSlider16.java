package net.minegasm.classic;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.TextComponent;

import java.util.Locale;
import java.util.function.DoubleConsumer;

/**
 * A slider over an arbitrary {@code [min, max]} range, unlike {@code SettingsScreen16}'s private
 * {@code PctSlider} which is hardwired to {@code [0, 1]} shown as a percentage. Used by the
 * Customization and Device Editor screens, whose fields (event multiplier {@code [0,4]}, feature
 * multiplier {@code [0,2]}, calibration travel fraction {@code [0,0.20]}, etc.) don't fit a percentage.
 */
final class BoundedSlider16 extends AbstractSliderButton {
    private final String prefix;
    private final double min;
    private final double max;
    private final int decimals;
    private final DoubleConsumer consumer;

    BoundedSlider16(int x, int y, int width, int height, String prefix, double min, double max,
                    int decimals, double value, DoubleConsumer consumer) {
        super(x, y, width, height, new TextComponent(""), toSlider(value, min, max));
        this.prefix = prefix;
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
        setMessage(new TextComponent(prefix + format(fromSlider())));
    }

    private String format(double v) {
        return String.format(Locale.ROOT, "%." + decimals + "f", v);
    }

    @Override
    protected void applyValue() {
        if (consumer != null) {
            consumer.accept(fromSlider());
        }
    }
}
