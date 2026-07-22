package net.minegasm.neoforge;

import net.minecraft.client.Minecraft;
//? if >=26.1.2 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;
*///?}
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/** Bounded, scrollable provider error history with wrapped original messages. */
final class ErrorListWidget extends ObjectSelectionList<ErrorListWidget.Entry> {
    ErrorListWidget(Minecraft minecraft, int x, int y, int width, int height,
                    List<String> errors) {
        super(minecraft, width, height, y, 20);
        setX(x);
        centerListVertically = false;
        for (String error : errors) {
            Entry entry = new Entry(minecraft, error, width - 24);
            //? if >=26.1.2 {
            addEntry(entry, entry.contentHeight());
            //?} else {
            /*addEntry(entry); // pre-26.1.2 AbstractSelectionList has no per-entry height override
            *///?}
        }
        setScrollAmount(Double.MAX_VALUE);
    }

    @Override
    public int getRowWidth() {
        return getWidth() - 14;
    }

    static final class Entry extends ObjectSelectionList.Entry<Entry> {
        private final Minecraft minecraft;
        private final Component original;
        private final List<FormattedCharSequence> lines;

        Entry(Minecraft minecraft, String error, int wrapWidth) {
            this.minecraft = minecraft;
            this.original = Component.literal(error);
            this.lines = minecraft.font.split(original, Math.max(40, wrapWidth));
        }

        int contentHeight() {
            return Math.max(20, lines.size() * 10 + 6);
        }

        @Override
        public Component getNarration() {
            return original;
        }

        //? if >=26.1.2 {
        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                   boolean hovered, float partialTick) {
            int x = getContentX() + 4;
            int y = getContentY() + 3;
            for (int i = 0; i < lines.size(); i++) {
                graphics.text(minecraft.font, lines.get(i), x, y + i * 10, 0xFFFF7777);
            }
        }
        //?} else {
        /*@Override
        public void render(GuiGraphics graphics, int index, int top, int left, int rowWidth,
                           int rowHeight, int mouseX, int mouseY, boolean hovered, float partialTick) {
            int x = left + 4;
            int y = top + 3;
            for (int i = 0; i < lines.size(); i++) {
                graphics.drawString(minecraft.font, lines.get(i), x, y + i * 10, 0xFFFF7777);
            }
        }
        *///?}
    }
}
