package net.minegasm.neoforge;

import net.minegasm.client.MinegasmClient;
import net.minegasm.config.HapticConfig;
import net.minegasm.pack.ScenePackInfo;

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

/**
 * Pack-manager screen (brief 0003 §2.7): pick the recipe pack that turns game events into haptics.
 * Lists the two built-ins plus every loaded user scene pack, and writes the choice into the config's
 * {@code recipePack} selector (the raw id string, per ADR-017). Selection never mutates engine state
 * directly, only through {@link MinegasmClient#updateConfig}.
 *
 * <p>UNVERIFIED: drafted against the pinned 26.x API and the sibling screens' Stonecutter guards, but
 * not yet compiled or run in a Minecraft build. Validate the Screen/Button API and the version guards
 * against each target before shipping.
 */
public final class MinegasmScenePackScreen extends Screen {

    private final Screen parent;
    private final MinegasmClient client;
    private RowScroller scroller;

    public MinegasmScenePackScreen(Screen parent, MinegasmClient client) {
        super(Component.translatable("minegasm.packs.title"));
        this.parent = parent;
        this.client = client;
    }

    @Override
    protected void init() {
        int w = Math.min(this.width - 16, 300);
        int x = (this.width - w) / 2;
        int h = 20;
        int gap = 24;

        String selected = client.config().recipePackName();

        // The two built-ins always appear first, then the loaded file packs in load order. The whole list
        // scrolls, since the number of installed packs is unbounded; Done stays pinned at the bottom.
        List<String> ids = new ArrayList<>();
        List<Component> labels = new ArrayList<>();
        ids.add("classic");
        labels.add(Component.translatable("minegasm.packs.builtin.classic"));
        ids.add("balanced");
        labels.add(Component.translatable("minegasm.packs.builtin.balanced"));
        for (ScenePackInfo pack : client.scenePacks()) {
            ids.add(pack.id());
            labels.add(Component.literal(pack.displayName()));
        }

        int doneY = this.height - 24;
        int viewportTop = 44;
        int viewportBottom = doneY - 6;
        int rowCount = ids.size();
        int visibleRows = Math.max(1, (viewportBottom - viewportTop) / gap);
        if (scroller == null) {
            scroller = new RowScroller(visibleRows, rowCount);
        } else {
            scroller.resize(visibleRows, rowCount); // resize (not recreate) so a live rebuild keeps position
        }
        boolean needsScroll = rowCount > visibleRows;
        int rowW = needsScroll ? w - 20 : w;

        for (int i = 0; i < rowCount; i++) {
            if (!scroller.isVisible(i)) {
                continue;
            }
            int ry = viewportTop + (i - scroller.first()) * gap;
            addSelector(ids.get(i), labels.get(i), selected, x, ry, rowW, h);
        }
        if (needsScroll) {
            int scrollX = x + w - 20;
            Button up = addRenderableWidget(button(Component.literal("^"), b -> {
                scroller.up();
                rebuildWidgets();
            }, scrollX, viewportTop, 20, h));
            up.active = scroller.canScrollUp();
            Button down = addRenderableWidget(button(Component.literal("v"), b -> {
                scroller.down();
                rebuildWidgets();
            }, scrollX, viewportBottom - 20, 20, h));
            down.active = scroller.canScrollDown();
        }

        addRenderableWidget(button(Component.translatable("gui.done"), b -> onClose(), x, doneY, w, h));
    }

    private void addSelector(String id, Component label, String selected, int x, int y, int width,
                             int height) {
        boolean current = id.equals(selected);
        Button b = addRenderableWidget(button(
                current ? Component.translatable("minegasm.packs.selected", label) : label,
                click -> select(id), x, y, width, height));
        b.active = !current; // the current pack is shown but not re-selectable
    }

    // Wheel scrolls the pack list, page-at-a-time via the scroller. The 4-arg overload with a horizontal
    // scrollX component was added in 1.21.1; 1.19.2 and 1.20.1 take a single delta. Mirrors the hub screen.
    //? if >=1.21.1 {
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scroller != null && scrollY > 0 && scroller.canScrollUp()) {
            scroller.up();
            rebuildWidgets();
            return true;
        }
        if (scroller != null && scrollY < 0 && scroller.canScrollDown()) {
            scroller.down();
            rebuildWidgets();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
    //?} else {
    /*@Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (scroller != null && delta > 0 && scroller.canScrollUp()) {
            scroller.up();
            rebuildWidgets();
            return true;
        }
        if (scroller != null && delta < 0 && scroller.canScrollDown()) {
            scroller.down();
            rebuildWidgets();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }
    *///?}

    private void select(String packId) {
        HapticConfig cfg = client.config().raw();
        HapticConfig.Profile p = cfg.profile();
        HapticConfig updated = new HapticConfig(cfg.schemaVersion(),
                new HapticConfig.Profile(packId, p.hapticMode()),
                cfg.global(), cfg.buttplug(), cfg.events(), cfg.outputPolicy(), cfg.devices(),
                cfg.positionCalibrations(), cfg.accumulation(), cfg.customIntensity(), cfg.bridges());
        client.updateConfig(updated);
        rebuildWidgets();
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

    //? if >=26.1.2 {
    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                   float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(this.font, this.title, this.width / 2, 20, 0xFFFFFFFF);
        graphics.centeredText(this.font,
                Component.translatable("minegasm.packs.subtitle", client.scenePackCount()),
                this.width / 2, 31, 0xFFA0A0A0);
    }
    //?} elif >=1.20.1 {
    /*@Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        //? if <1.21.1 {
        /^this.renderBackground(graphics);
        ^///?}
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFFFF);
        graphics.drawCenteredString(this.font,
                Component.translatable("minegasm.packs.subtitle", client.scenePackCount()),
                this.width / 2, 31, 0xFFA0A0A0);
    }
    *///?} else {
    /*@Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(poseStack);
        super.render(poseStack, mouseX, mouseY, partialTick);
        GuiComponent.drawCenteredString(poseStack, this.font, this.title, this.width / 2, 20, 0xFFFFFFFF);
        GuiComponent.drawCenteredString(poseStack, this.font,
                Component.translatable("minegasm.packs.subtitle", client.scenePackCount()),
                this.width / 2, 31, 0xFFA0A0A0);
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
}
