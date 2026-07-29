package net.minegasm.classic;

import net.minegasm.device.HapticDevice;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;

import com.mojang.blaze3d.vertex.PoseStack;

import java.util.List;

/**
 * Compact, scrollable list of the devices Intiface currently reports, for the 1.16.5 dashboard. Mirrors
 * the modern {@code DeviceListWidget}; the device data comes from the shared engine types, so this reads
 * the same on both loaders.
 */
final class DeviceList16 extends ObjectSelectionList<DeviceList16.Entry> {

    DeviceList16(Minecraft minecraft, int x, int y, int width, int height, List<HapticDevice> devices) {
        super(minecraft, width, height, y, y + height, 28);
        setLeftPos(x);
        setRenderBackground(false);
        setRenderTopAndBottom(false);
        centerListVertically = false;
        for (HapticDevice device : devices) {
            addEntry(new Entry(minecraft, device));
        }
    }

    @Override
    public int getRowWidth() {
        return this.width - 14;
    }

    static final class Entry extends ObjectSelectionList.Entry<Entry> {
        private final Minecraft minecraft;
        private final Component name;
        private final Component capabilities;

        Entry(Minecraft minecraft, HapticDevice device) {
            this.minecraft = minecraft;
            this.name = new TextComponent(ClassicDeviceFormat.label(device));
            this.capabilities = new TextComponent(ClassicDeviceFormat.capabilities(device));
        }

        @Override
        public void render(PoseStack pose, int index, int top, int left, int rowWidth, int rowHeight,
                           int mouseX, int mouseY, boolean hovered, float partialTick) {
            int x = left + 4;
            GuiComponent.drawString(pose, minecraft.font, name, x, top + 3, 0xFFFFFF);
            GuiComponent.drawString(pose, minecraft.font, capabilities, x, top + 14, 0xA0A0A0);
        }
    }
}
