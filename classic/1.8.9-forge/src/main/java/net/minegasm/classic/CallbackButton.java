package net.minegasm.classic;

import net.minecraft.client.gui.GuiButton;

/**
 * A {@link GuiButton} whose click runs an arbitrary callback. Vanilla's {@code GuiScreen} dispatches
 * clicks through {@code actionPerformed(GuiButton)} with a {@code switch} on a fixed integer id, which
 * doesn't fit a screen whose rows (and their toggle buttons) are built dynamically from a scrolled,
 * variable-length list. Button clicks are dispatched synchronously by {@code GuiScreen} regardless, so
 * this needs no special handling beyond running the callback when the screen's
 * {@code actionPerformed} sees an instance of this class.
 */
final class CallbackButton extends GuiButton {
    private final Runnable onPress;

    CallbackButton(int x, int y, int width, int height, String label, Runnable onPress) {
        super(0, x, y, width, height, label);
        this.onPress = onPress;
    }

    void press() {
        onPress.run();
    }
}
