package net.minegasm.neoforge;

import net.minegasm.device.HapticDevice;

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

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** Compact, scrollable view of the devices currently reported by Intiface. */
final class DeviceListWidget extends ObjectSelectionList<DeviceListWidget.Entry> {
    DeviceListWidget(Minecraft minecraft, int x, int y, int width, int height,
                     List<HapticDevice> devices) {
        //? if >=1.21.1 {
        super(minecraft, width, height, y, 28);
        setX(x);
        //?} else {
        /*super(minecraft, width, height, y, y + height, 28); // 1.20.1 ctor takes explicit y0/y1
        setLeftPos(x);
        // Pre-1.20.2 AbstractSelectionList sizes its top/bottom dirt masks from the ctor's height arg,
        // assuming it is the screen height with y0/y1 the visible band. Embedded here that arg is the
        // list's own height (< y1), so the "bottom" mask paints upward over the entries and hides them.
        // Disable the masks and the dirt fill — the config screen already draws the backdrop.
        setRenderBackground(false);
        setRenderTopAndBottom(false);
        *///?}
        centerListVertically = false;
        devices.forEach(device -> addEntry(new Entry(minecraft, device)));
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
        private final Component name;
        private final Component capabilities;

        Entry(Minecraft minecraft, HapticDevice device) {
            this.minecraft = minecraft;
            this.name = Component.literal(device.label());
            Map<String, Long> counts = device.features().values().stream()
                    .flatMap(feature -> feature.outputs().keySet().stream())
                    .filter(kind -> kind.renderableWireName().isPresent())
                    .collect(Collectors.groupingBy(kind -> kind.wireName(), TreeMap::new,
                            Collectors.counting()));
            String detail = counts.entrySet().stream()
                    .map(entry -> entry.getValue() > 1
                            ? entry.getKey() + " ×" + entry.getValue() : entry.getKey())
                    .collect(Collectors.joining(", "));
            this.capabilities = Component.literal(detail.isBlank() ? "No supported output" : detail);
        }

        @Override
        public Component getNarration() {
            return name.copy().append(". ").append(capabilities);
        }

        //? if >=26.1.2 {
        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                   boolean hovered, float partialTick) {
            int x = getContentX() + 4;
            graphics.text(minecraft.font, name, x, getContentY() + 3, 0xFFFFFFFF);
            graphics.text(minecraft.font, capabilities, x, getContentY() + 14, 0xFFA0A0A0);
        }
        //?} elif >=1.20.1 {
        /*@Override
        public void render(GuiGraphics graphics, int index, int top, int left, int rowWidth,
                           int rowHeight, int mouseX, int mouseY, boolean hovered, float partialTick) {
            int x = left + 4;
            graphics.drawString(minecraft.font, name, x, top + 3, 0xFFFFFFFF);
            graphics.drawString(minecraft.font, capabilities, x, top + 14, 0xFFA0A0A0);
        }
        *///?} else {
        /*@Override
        public void render(PoseStack poseStack, int index, int top, int left, int rowWidth,
                           int rowHeight, int mouseX, int mouseY, boolean hovered, float partialTick) {
            int x = left + 4; // pre-1.20 draw helpers are static on GuiComponent and take a PoseStack
            GuiComponent.drawString(poseStack, minecraft.font, name, x, top + 3, 0xFFFFFFFF);
            GuiComponent.drawString(poseStack, minecraft.font, capabilities, x, top + 14, 0xFFA0A0A0);
        }
        *///?}
    }
}
