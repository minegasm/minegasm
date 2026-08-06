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

    public MinegasmScenePackScreen(Screen parent, MinegasmClient client) {
        super(Component.translatable("minegasm.packs.title"));
        this.parent = parent;
        this.client = client;
    }

    @Override
    protected void init() {
        int width = Math.min(this.width - 16, 300);
        int x = (this.width - width) / 2;
        int y = 44;
        int h = 20;
        int gap = 24;

        String selected = client.config().recipePackName();

        // The two built-ins always appear first, then the loaded file packs in load order.
        y = addSelector("classic", Component.translatable("minegasm.packs.builtin.classic"),
                selected, x, y, width, h, gap);
        y = addSelector("balanced", Component.translatable("minegasm.packs.builtin.balanced"),
                selected, x, y, width, h, gap);

        for (ScenePackInfo pack : client.scenePacks()) {
            y = addSelector(pack.id(), Component.literal(pack.displayName()),
                    selected, x, y, width, h, gap);
        }

        addRenderableWidget(button(Component.translatable("gui.done"),
                b -> onClose(), x, this.height - 24, width, h));
    }

    private int addSelector(String id, Component label, String selected, int x, int y, int width,
                            int height, int gap) {
        boolean current = id.equals(selected);
        Button b = addRenderableWidget(button(
                current ? Component.translatable("minegasm.packs.selected", label) : label,
                click -> select(id), x, y, width, height));
        b.active = !current; // the current pack is shown but not re-selectable
        return y + gap;
    }

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
