package net.minegasm.neoforge;

import net.minecraft.client.Minecraft;
//? if >=26.1.2 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} elif >=1.20.1 {
/*import net.minecraft.client.gui.GuiGraphics;
*///?} else {
/*import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiComponent;
*///?}
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/** Bounded, scrollable provider error history with wrapped original messages. */
final class ErrorListWidget extends ObjectSelectionList<ErrorListWidget.Entry> {
    ErrorListWidget(Minecraft minecraft, int x, int y, int width, int height,
                    List<String> errors) {
        //? if >=1.21.1 {
        super(minecraft, width, height, y, 20);
        setX(x);
        //?} else {
        /*super(minecraft, width, height, y, y + height, 20); // 1.20.1 ctor takes explicit y0/y1
        setLeftPos(x);
        // See DeviceListWidget: disable the pre-1.20.2 dirt masks (their bottom mask, sized from the
        // list-height ctor arg, paints over the entries) and the dirt fill; the screen draws the backdrop.
        setRenderBackground(false);
        setRenderTopAndBottom(false);
        *///?}
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
        //? if >=1.21.1 {
        return getWidth() - 14;
        //?} else {
        /*return this.width - 14; // 1.20.1 AbstractSelectionList exposes the width field, not getWidth()
        *///?}
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
        //?} elif >=1.20.1 {
        /*@Override
        public void render(GuiGraphics graphics, int index, int top, int left, int rowWidth,
                           int rowHeight, int mouseX, int mouseY, boolean hovered, float partialTick) {
            // Fixed-height rows (pre-1.20.2 lists take no per-entry height): render one line so a wrapped
            // multi-line error can't overlap the next row. The 26.x path shows the full wrapped text.
            if (!lines.isEmpty()) {
                graphics.drawString(minecraft.font, lines.get(0), left + 4, top + 6, 0xFFFF7777);
            }
        }
        *///?} else {
        /*@Override
        public void render(PoseStack poseStack, int index, int top, int left, int rowWidth,
                           int rowHeight, int mouseX, int mouseY, boolean hovered, float partialTick) {
            // Single line only — see the >=1.20.1 branch. Pre-1.20 draw helpers are static on GuiComponent.
            if (!lines.isEmpty()) {
                GuiComponent.drawString(poseStack, minecraft.font, lines.get(0), left + 4, top + 6, 0xFFFF7777);
            }
        }
        *///?}
    }
}
