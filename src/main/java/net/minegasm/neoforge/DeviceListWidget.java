package net.minegasm.neoforge;

import net.minegasm.device.HapticDevice;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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
        super(minecraft, width, height, y, 28);
        setX(x);
        centerListVertically = false;
        devices.forEach(device -> addEntry(new Entry(minecraft, device)));
    }

    @Override
    public int getRowWidth() {
        return getWidth() - 14;
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

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                   boolean hovered, float partialTick) {
            int x = getContentX() + 4;
            graphics.text(minecraft.font, name, x, getContentY() + 3, 0xFFFFFFFF);
            graphics.text(minecraft.font, capabilities, x, getContentY() + 14, 0xFFA0A0A0);
        }
    }
}
