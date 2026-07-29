package net.minegasm.classic;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.util.FormattedCharSequence;

import com.mojang.blaze3d.vertex.PoseStack;

import java.util.List;

/**
 * Bounded, scrollable provider-error history for the 1.16.5 dashboard, mirroring the modern
 * {@code ErrorListWidget}. Rows are fixed height (one wrapped line rendered), and the list scrolls to the
 * newest entry.
 */
final class ErrorList16 extends ObjectSelectionList<ErrorList16.Entry> {

    ErrorList16(Minecraft minecraft, int x, int y, int width, int height, List<String> errors) {
        super(minecraft, width, height, y, y + height, 20);
        setLeftPos(x);
        setRenderBackground(false);
        setRenderTopAndBottom(false);
        centerListVertically = false;
        for (String error : errors) {
            addEntry(new Entry(minecraft, error, width - 24));
        }
        setScrollAmount(Double.MAX_VALUE);
    }

    @Override
    public int getRowWidth() {
        return this.width - 14;
    }

    static final class Entry extends ObjectSelectionList.Entry<Entry> {
        private final Minecraft minecraft;
        private final List<FormattedCharSequence> lines;

        Entry(Minecraft minecraft, String error, int wrapWidth) {
            this.minecraft = minecraft;
            this.lines = minecraft.font.split(new TextComponent(error), Math.max(40, wrapWidth));
        }

        @Override
        public void render(PoseStack pose, int index, int top, int left, int rowWidth, int rowHeight,
                           int mouseX, int mouseY, boolean hovered, float partialTick) {
            if (!lines.isEmpty()) {
                minecraft.font.draw(pose, lines.get(0), left + 4, top + 6, 0xFF7777);
            }
        }
    }
}
