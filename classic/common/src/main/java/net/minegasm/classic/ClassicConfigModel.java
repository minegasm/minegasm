package net.minegasm.classic;

import net.minegasm.client.MinegasmClient;
import net.minegasm.config.HapticConfig;
import net.minegasm.config.MinegasmMode;
import net.minegasm.config.PauseBehavior;
import net.minegasm.config.TestOutputLimits;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

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
    /** Raw recipe-pack selector: a built-in name ("classic"/"balanced") or a loaded file pack id (ADR-017). */
    public String recipePack;
    public MinegasmMode mode;
    public boolean fatigueProtection;
    public PauseBehavior pauseBehavior;
    public boolean stopOnWorldUnload;
    public String serverUrl;
    public boolean autoConnect;
    public boolean autoScan;
    public boolean allowRemote;
    public int testMaxPercent;
    public int testMaxDurationMs;
    public int unsafeTestMaxPercent;
    public int unsafeTestMaxDurationMs;

    public ClassicConfigModel(HapticConfig original) {
        this.original = original;
        HapticConfig.Global g = original.global();
        this.enabled = g.enabled();
        this.intensity = g.intensity();
        this.variation = g.variation();
        this.fatigueProtection = g.fatigueProtection();
        this.pauseBehavior = g.pauseBehaviorMode();
        this.stopOnWorldUnload = g.stopOnWorldUnload();
        HapticConfig.Profile id = original.profile();
        this.recipePack = id.recipePack();
        this.mode = id.mode();
        HapticConfig.Buttplug b = original.buttplug();
        this.serverUrl = b.serverUrl();
        this.autoConnect = b.autoConnect();
        this.autoScan = b.autoScan();
        this.allowRemote = b.allowRemoteServer();
        this.testMaxPercent = g.testMaxPercent();
        this.testMaxDurationMs = g.testMaxDurationMs();
        this.unsafeTestMaxPercent = g.unsafeTestMaxPercent();
        this.unsafeTestMaxDurationMs = g.unsafeTestMaxDurationMs();
    }

    /** Step the pause behavior through Stop -> Pause -> Continue -> Stop. */
    public void cyclePauseBehavior() {
        PauseBehavior[] all = PauseBehavior.values();
        pauseBehavior = all[(pauseBehavior.ordinal() + 1) % all.length];
    }

    /**
     * Step the recipe pack through the two built-ins and then every loaded file pack, in order
     * (ADR-017). An unrecognized current selection resets to Classic. {@code fileIds} are the loaded
     * scene pack ids (from {@code client.scenePacks()}); pass empty to cycle the built-ins only.
     */
    public void cycleRecipePack(List<String> fileIds) {
        List<String> ids = new ArrayList<>();
        ids.add("classic");
        ids.add("balanced");
        if (fileIds != null) {
            for (String id : fileIds) {
                if (id != null && !ids.contains(id)) {
                    ids.add(id);
                }
            }
        }
        int idx = ids.indexOf(recipePack);
        recipePack = ids.get((idx + 1 + ids.size()) % ids.size());
    }

    /** Step the compatibility mode through its presets. */
    public void cycleMode() {
        MinegasmMode[] all = MinegasmMode.values();
        mode = all[(mode.ordinal() + 1) % all.length];
    }

    /** Step the everyday test-output cap through preset profiles (mirrors the modern settings screen). */
    public void cycleNormalTestLimit() {
        int[][] profiles = {{25, 400}, {50, 2_000}, {75, 5_000}, {100, 10_000}};
        int next = nextProfile(profiles, testMaxPercent, testMaxDurationMs);
        testMaxPercent = profiles[next][0];
        testMaxDurationMs = profiles[next][1];
        unsafeTestMaxPercent = Math.max(unsafeTestMaxPercent, testMaxPercent);
        unsafeTestMaxDurationMs = Math.max(unsafeTestMaxDurationMs, testMaxDurationMs);
    }

    /** Step the hard (unsafe-confirmed) test cap, never below the everyday cap. */
    public void cycleUnsafeTestLimit() {
        int[][] profiles = {{50, 2_000}, {75, 5_000}, {100, 10_000},
                {100, 30_000}, {100, 60_000}, {100, 300_000},
                {TestOutputLimits.MAX_PERCENT, TestOutputLimits.MAX_DURATION_MS}};
        int next = nextProfile(profiles, unsafeTestMaxPercent, unsafeTestMaxDurationMs);
        for (int i = 0; i < profiles.length; i++) {
            int candidate = (next + i) % profiles.length;
            if (profiles[candidate][0] >= testMaxPercent && profiles[candidate][1] >= testMaxDurationMs) {
                unsafeTestMaxPercent = profiles[candidate][0];
                unsafeTestMaxDurationMs = profiles[candidate][1];
                return;
            }
        }
    }

    /**
     * Whether a server URL is a well-formed Buttplug WebSocket endpoint (matches the modern settings
     * screen's check). The settings screens use this to reject a bad URL before saving and flag the field,
     * rather than persisting something the provider can never connect to.
     */
    public static boolean isValidServerUrl(String url) {
        try {
            URI parsed = URI.create(url);
            String scheme = parsed.getScheme();
            return ("ws".equalsIgnoreCase(scheme) || "wss".equalsIgnoreCase(scheme))
                    && parsed.getHost() != null;
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    private static int nextProfile(int[][] profiles, int percent, int durationMs) {
        for (int i = 0; i < profiles.length; i++) {
            if (profiles[i][0] == percent && profiles[i][1] == durationMs) {
                return (i + 1) % profiles.length;
            }
        }
        return 0;
    }

    /** Rebuild the persisted config: the original with only the edited fields replaced. */
    public HapticConfig toConfig() {
        HapticConfig.Global g = original.global();
        HapticConfig.Global newGlobal = new HapticConfig.Global(
                enabled, intensity, variation, fatigueProtection,
                pauseBehavior.name(), stopOnWorldUnload, g.panicKey(),
                testMaxPercent, testMaxDurationMs,
                unsafeTestMaxPercent, unsafeTestMaxDurationMs);

        HapticConfig.Profile newIdentity = new HapticConfig.Profile(recipePack, mode.name());

        HapticConfig.Buttplug b = original.buttplug();
        HapticConfig.Buttplug newButtplug = new HapticConfig.Buttplug(
                serverUrl, autoConnect, autoScan, allowRemote, b.reconnect(), b.client());

        return new HapticConfig(original.schemaVersion(), newIdentity, newGlobal, newButtplug,
                original.events(), original.outputPolicy(), original.devices(),
                original.positionCalibrations(), original.accumulation(), original.customIntensity(),
                original.bridge());
    }

    /** Persist the edits through the shared client (which stops output on an enable->disable change). */
    public void apply(MinegasmClient client) {
        client.updateConfig(toConfig());
    }
}
