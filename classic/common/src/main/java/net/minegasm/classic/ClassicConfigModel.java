package net.minegasm.classic;

import net.minegasm.client.MinegasmClient;
import net.minegasm.config.HapticConfig;
import net.minegasm.config.MinegasmMode;
import net.minegasm.config.PauseBehavior;
import net.minegasm.config.RecipePackId;

import java.util.Locale;

/**
 * A small editable view of the settings the Classic config screen exposes, sitting between the immutable
 * {@link HapticConfig} and the per-version {@code GuiScreen}. Minecraft-free, so all three versions share
 * it; the screens only render widgets bound to these fields and call {@link #apply(MinegasmClient)}.
 *
 * <p>The set of settings mirrors the modern config screen: master enable, intensity, variation, recipe
 * pack, compatibility mode, fatigue protection, pause behavior, stop-on-world-unload, the Buttplug server
 * URL, auto-connect, auto-scan, and allow-remote. Like modern, it does not toggle individual gameplay
 * events; the recipe pack and mode select those. Per-event settings, and everything else the screen does
 * not show, are preserved from the original config in {@link #toConfig()}: it keeps the whole original and
 * swaps in only these fields, so a save can never drop config the screen does not model (checked by an
 * MC-free round-trip test).
 */
public final class ClassicConfigModel {

    private final HapticConfig original;

    public boolean enabled;
    public double intensity;
    public double variation;
    public RecipePackId recipePack;
    public MinegasmMode mode;
    public boolean fatigueProtection;
    public PauseBehavior pauseBehavior;
    public boolean stopOnWorldUnload;
    public String serverUrl;
    public boolean autoConnect;
    public boolean autoScan;
    public boolean allowRemote;

    public ClassicConfigModel(HapticConfig original) {
        this.original = original;
        HapticConfig.Global g = original.global();
        this.enabled = g.enabled();
        this.intensity = g.intensity();
        this.variation = g.variation();
        this.fatigueProtection = g.fatigueProtection();
        this.pauseBehavior = g.pauseBehaviorMode();
        this.stopOnWorldUnload = g.stopOnWorldUnload();
        HapticConfig.Identity id = original.identity();
        this.recipePack = id.recipePackId();
        this.mode = id.mode();
        HapticConfig.Buttplug b = original.buttplug();
        this.serverUrl = b.serverUrl();
        this.autoConnect = b.autoConnect();
        this.autoScan = b.autoScan();
        this.allowRemote = b.allowRemoteServer();
    }

    /** Step the pause behavior through Stop -> Pause -> Continue -> Stop. */
    public void cyclePauseBehavior() {
        PauseBehavior[] all = PauseBehavior.values();
        pauseBehavior = all[(pauseBehavior.ordinal() + 1) % all.length];
    }

    /** Toggle the recipe pack between Balanced and Classic (the two modern exposes). */
    public void toggleRecipePack() {
        recipePack = recipePack == RecipePackId.BALANCED ? RecipePackId.CLASSIC : RecipePackId.BALANCED;
    }

    /** Step the compatibility mode through its presets. */
    public void cycleMode() {
        MinegasmMode[] all = MinegasmMode.values();
        mode = all[(mode.ordinal() + 1) % all.length];
    }

    /** Rebuild the persisted config: the original with only the edited fields replaced. */
    public HapticConfig toConfig() {
        HapticConfig.Global g = original.global();
        HapticConfig.Global newGlobal = new HapticConfig.Global(
                enabled, intensity, variation, fatigueProtection,
                pauseBehavior.name(), stopOnWorldUnload, g.panicKey(),
                g.testMaxPercent(), g.testMaxDurationMs(),
                g.unsafeTestMaxPercent(), g.unsafeTestMaxDurationMs());

        HapticConfig.Identity newIdentity = new HapticConfig.Identity(
                recipePack.name().toLowerCase(Locale.ROOT), mode.name());

        HapticConfig.Buttplug b = original.buttplug();
        HapticConfig.Buttplug newButtplug = new HapticConfig.Buttplug(
                serverUrl, autoConnect, autoScan, allowRemote, b.reconnect(), b.client());

        return new HapticConfig(original.schemaVersion(), newIdentity, newGlobal, newButtplug,
                original.events(), original.outputPolicy(), original.devices(),
                original.positionCalibrations(), original.accumulation(), original.customIntensity());
    }

    /** Persist the edits through the shared client (which stops output on an enable->disable change). */
    public void apply(MinegasmClient client) {
        client.updateConfig(toConfig());
    }
}
