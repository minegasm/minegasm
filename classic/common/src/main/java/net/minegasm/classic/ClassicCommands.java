package net.minegasm.classic;

import net.minegasm.buttplug.ConnectionState;
import net.minegasm.buttplug.ProviderStatus;
import net.minegasm.client.MinegasmClient;
import net.minegasm.config.HapticConfig;
import net.minegasm.config.MinegasmMode;
import net.minegasm.config.TestOutputLimits;
import net.minegasm.core.GameEventKind;
import net.minegasm.core.RawGameEvent;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Version-agnostic parser and executor for the {@code /minegasm} (and {@code /mg}) client command tree.
 * Classic Minecraft has no brigadier, so each loader hands the raw argument tokens here and this class
 * drives the shared {@link MinegasmClient}, reporting back through a {@link Feedback} seam. It holds no
 * Minecraft types, so 1.7.10, 1.8.9, and 1.12.2 all reuse it; only the thin registration and chat glue
 * differs per version. Messages are plain English strings (Classic has no toast or lang pipeline).
 */
public final class ClassicCommands {

    /**
     * Where command output goes. Implementations wrap the version-specific chat sink. Because the
     * connect flow reports asynchronously from a provider thread, implementations must be safe to call
     * from any thread (marshalling onto the client thread where the loader requires it).
     */
    public interface Feedback {
        void info(String message);
        void error(String message);
    }

    /** Sub-command names, in help order, for tab completion. */
    public static final List<String> SUBCOMMANDS = Arrays.asList(
            "status", "connect", "disconnect", "reconnect", "enable", "disable",
            "mode", "recipe", "bridge", "adapter", "stop", "resume", "test", "trigger");

    private static final Map<String, GameEventKind> TRIGGER_EVENTS = triggerEvents();

    private ClassicCommands() {
    }

    /**
     * Execute one command invocation. {@code args} are the tokens after {@code /minegasm} (or
     * {@code /mg}); an empty array prints status. {@code gameTick} is the loader's own tick counter,
     * used to stamp manually triggered events.
     */
    public static void dispatch(MinegasmClient client, long gameTick, String[] args, Feedback out) {
        String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "status":
                sendStatus(client, out);
                break;
            case "stop":
                client.panic();
                out.info("Haptic output stopped.");
                break;
            case "resume":
                client.clearPanic();
                out.info("Haptic output resumed.");
                break;
            case "enable":
                haptics(client, out, true);
                break;
            case "disable":
                haptics(client, out, false);
                break;
            case "connect":
                connect(client, out, false);
                break;
            case "reconnect":
                connect(client, out, true);
                break;
            case "disconnect":
                client.disconnect();
                out.info("Disconnected.");
                break;
            case "mode":
                mode(client, out, args);
                break;
            case "recipe":
                recipe(client, out, args);
                break;
            case "bridge":
                bridge(client, out, args);
                break;
            case "adapter":
                adapter(client, out, args);
                break;
            case "test":
                test(client, out, args);
                break;
            case "trigger":
                trigger(client, gameTick, out, args);
                break;
            default:
                out.error("Unknown command '" + sub + "'. Try: " + String.join(", ", SUBCOMMANDS));
        }
    }

    private static void haptics(MinegasmClient client, Feedback out, boolean enable) {
        boolean changed = client.setHapticsEnabled(enable);
        if (enable) {
            out.info(changed ? "Haptics enabled." : "Haptics were already enabled.");
        } else {
            out.info(changed ? "Haptics disabled." : "Haptics were already disabled.");
        }
    }

    private static void mode(MinegasmClient client, Feedback out, String[] args) {
        HapticConfig cfg = client.config().raw();
        if (args.length < 2) {
            out.info("Mode: " + cfg.profile().mode().name().toLowerCase(Locale.ROOT)
                    + ". Options: " + names(MinegasmMode.values()));
            return;
        }
        MinegasmMode mode = MinegasmMode.fromString(args[1], null);
        if (mode == null) {
            out.error("Unknown mode '" + args[1] + "'. Options: " + names(MinegasmMode.values()));
            return;
        }
        applyProfile(client, cfg, new HapticConfig.Profile(cfg.profile().recipePack(), mode.name()));
        out.info("Mode set to " + mode.name().toLowerCase(Locale.ROOT) + ".");
    }

    private static void recipe(MinegasmClient client, Feedback out, String[] args) {
        HapticConfig cfg = client.config().raw();
        java.util.List<String> options = recipeOptions(client);
        if (args.length < 2) {
            out.info("Recipe: " + cfg.profile().recipePack()
                    + ". Options: " + String.join(", ", options));
            return;
        }
        String selected = null;
        for (String option : options) {
            if (option.equalsIgnoreCase(args[1])) {
                selected = option;
                break;
            }
        }
        if (selected == null) {
            out.error("Unknown recipe '" + args[1] + "'. Options: " + String.join(", ", options));
            return;
        }
        applyProfile(client, cfg, new HapticConfig.Profile(selected, cfg.profile().hapticMode()));
        out.info("Recipe set to " + selected + ".");
    }

    private static void adapter(MinegasmClient client, Feedback out, String[] args) {
        if (args.length < 2) {
            out.info("Adapter: " + client.backend() + ". Use: /minegasm adapter native|buttplug4j");
            return;
        }
        String requested = args[1].toLowerCase(Locale.ROOT);
        if (!requested.equals("native") && !requested.equals("buttplug4j")) {
            out.error("Unknown adapter '" + args[1] + "'. Options: native, buttplug4j");
            return;
        }
        if (client.setBackend(requested)) {
            out.info("Adapter switched to " + requested + ".");
        } else {
            out.info("Adapter already " + requested + ".");
        }
    }

    private static void bridge(MinegasmClient client, Feedback out, String[] args) {
        HapticConfig cfg = client.config().raw();
        HapticConfig.Bridge b = cfg.bridges().get(0);
        if (args.length < 2) {
            out.info("Bridge: " + (b.enabled() ? "on" : "off")
                    + " (" + b.transport() + " " + b.url() + "). Use: /minegasm bridge on|off");
            return;
        }
        String arg = args[1].toLowerCase(Locale.ROOT);
        boolean enable;
        if (arg.equals("on") || arg.equals("enable") || arg.equals("true")) {
            enable = true;
        } else if (arg.equals("off") || arg.equals("disable") || arg.equals("false")) {
            enable = false;
        } else {
            out.error("Use: /minegasm bridge on|off");
            return;
        }
        java.util.List<HapticConfig.Bridge> bridges = new java.util.ArrayList<>(cfg.bridges());
        bridges.set(0, new HapticConfig.Bridge(b.name(), enable, b.url(), b.transport(), b.allowRemote(),
                b.id())); // preserve the id so a toggle doesn't reconnect the endpoint
        client.updateConfig(new HapticConfig(cfg.schemaVersion(), cfg.profile(), cfg.global(),
                cfg.buttplug(), cfg.events(), cfg.outputPolicy(), cfg.devices(),
                cfg.positionCalibrations(), cfg.accumulation(), cfg.customIntensity(), bridges));
        out.info("Bridge " + (enable ? "enabled" : "disabled")
                + ". Restart Minecraft for it to take effect.");
    }

    /** The selectable recipe pack ids: the two built-ins plus every loaded file pack (ADR-017). */
    private static java.util.List<String> recipeOptions(MinegasmClient client) {
        java.util.List<String> options = new java.util.ArrayList<String>();
        options.add("classic");
        options.add("balanced");
        for (net.minegasm.pack.ScenePackInfo info : client.scenePacks()) {
            if (!options.contains(info.id())) {
                options.add(info.id());
            }
        }
        return options;
    }

    /** Persist a new profile (recipe pack + mode), preserving everything else in the config. */
    private static void applyProfile(MinegasmClient client, HapticConfig cfg,
                                      HapticConfig.Profile profile) {
        client.updateConfig(new HapticConfig(cfg.schemaVersion(), profile, cfg.global(),
                cfg.buttplug(), cfg.events(), cfg.outputPolicy(), cfg.devices(),
                cfg.positionCalibrations(), cfg.accumulation(), cfg.customIntensity(), cfg.bridges()));
    }

    private static String names(Enum<?>[] values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(values[i].name().toLowerCase(Locale.ROOT));
        }
        return sb.toString();
    }

    private static void testButtplug(MinegasmClient client, Feedback out) {
        if (!client.isConnected()) {
            out.error("Not connected. Run '/minegasm connect' first.");
            return;
        }
        if (!client.config().enabled() || !client.runtime().worker().isOutputEnabled()) {
            out.error("Haptics are disabled or stopped. Enable and resume first.");
            return;
        }
        int targeted = client.testButtplugOutput(0.25f, 400);
        if (targeted == 0) {
            out.error("No connected device has a feature to test.");
            return;
        }
        out.info("Sent a 25% / 400 ms test to Buttplug (" + targeted
                + (targeted == 1 ? " feature)." : " features)."));
    }

    private static void testBridge(MinegasmClient client, Feedback out, String name) {
        if (!client.config().enabled() || !client.runtime().worker().isOutputEnabled()) {
            out.error("Haptics are disabled or stopped. Enable and resume first.");
            return;
        }
        boolean known = false;
        for (HapticConfig.Bridge bridge : client.config().raw().bridges()) {
            if (bridge.enabled() && bridge.name().equals(name)) {
                known = true;
                break;
            }
        }
        if (!known) {
            out.error("No enabled bridge named '" + name + "'.");
            return;
        }
        client.testBridgeOutput(name, 0.25f, 400);
        out.info("Sent a 25% / 400 ms test to bridge " + name + ".");
    }

    private static void test(MinegasmClient client, Feedback out, String[] args) {
        // Per-backend targets; a bare "/mg test" (or one that starts with a number) is the global test.
        if (args.length >= 2 && args[1].equalsIgnoreCase("buttplug")) {
            testButtplug(client, out);
            return;
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("bridge")) {
            if (args.length < 3) {
                out.error("Usage: /minegasm test bridge <name>");
                return;
            }
            testBridge(client, out, args[2]);
            return;
        }
        int percent = TestOutputLimits.DEFAULT_NORMAL_PERCENT;
        int durationMs = 400;
        boolean unsafeConfirmed = false;
        if (args.length >= 2) {
            Integer p = parseInt(args[1]);
            if (p == null || p < TestOutputLimits.MIN_PERCENT || p > TestOutputLimits.MAX_PERCENT) {
                out.error("Strength must be " + TestOutputLimits.MIN_PERCENT + "-"
                        + TestOutputLimits.MAX_PERCENT + " percent.");
                return;
            }
            percent = p;
        }
        if (args.length >= 3) {
            Integer d = parseInt(args[2]);
            if (d == null || d < TestOutputLimits.MIN_DURATION_MS || d > TestOutputLimits.MAX_DURATION_MS) {
                out.error("Duration must be " + TestOutputLimits.MIN_DURATION_MS + "-"
                        + TestOutputLimits.MAX_DURATION_MS + " ms.");
                return;
            }
            durationMs = d;
        }
        if (args.length >= 4 && args[3].equalsIgnoreCase("unsafe")) {
            unsafeConfirmed = true;
        }

        HapticConfig.Global global = client.config().raw().global();
        if ((percent > global.testMaxPercent() || durationMs > global.testMaxDurationMs())
                && !unsafeConfirmed) {
            out.error("That test exceeds the safe cap (" + global.testMaxPercent() + "%, "
                    + global.testMaxDurationMs() + " ms). Repeat with 'unsafe' to confirm.");
            return;
        }
        if (percent > global.unsafeTestMaxPercent() || durationMs > global.unsafeTestMaxDurationMs()) {
            out.error("That test exceeds the configured hard cap (" + global.unsafeTestMaxPercent()
                    + "%, " + global.unsafeTestMaxDurationMs() + " ms).");
            return;
        }
        boolean anyBridge = false;
        for (HapticConfig.Bridge b : client.config().raw().bridges()) {
            if (b.enabled()) {
                anyBridge = true;
                break;
            }
        }
        if (!client.isConnected() && !anyBridge) {
            out.error("Not connected. Run '/minegasm connect' first.");
            return;
        }
        if (!client.config().enabled() || !client.runtime().worker().isOutputEnabled()) {
            out.error("Haptics are disabled or stopped. Enable and resume first.");
            return;
        }
        int targeted = client.testPulse(percent / 100f, durationMs);
        if (targeted == 0 && !anyBridge) {
            out.error("No connected device has a feature to test.");
            return;
        }
        if (targeted > 0) {
            out.info("Sent a " + percent + "% / " + durationMs + " ms test to " + targeted
                    + (targeted == 1 ? " feature." : " features."));
        } else {
            out.info("Sent a " + percent + "% / " + durationMs + " ms test to your bridge(s).");
        }
    }

    private static void trigger(MinegasmClient client, long gameTick, Feedback out, String[] args) {
        if (args.length < 2) {
            out.error("Usage: /minegasm trigger <" + String.join("|", TRIGGER_EVENTS.keySet()) + ">");
            return;
        }
        GameEventKind kind = TRIGGER_EVENTS.get(args[1].toLowerCase(Locale.ROOT));
        if (kind == null) {
            out.error("Unknown event '" + args[1] + "'. Options: "
                    + String.join(", ", TRIGGER_EVENTS.keySet()));
            return;
        }
        if (!client.isConnected() || !client.config().enabled()
                || !client.runtime().worker().isOutputEnabled()) {
            out.error("Haptics are unavailable (disconnected, disabled, or stopped).");
            return;
        }
        client.recordEvent(RawGameEvent.of(kind, gameTick, System.nanoTime()));
        out.info("Triggered " + kind.key() + ".");
    }

    private static void connect(MinegasmClient client, Feedback out, boolean reconnect) {
        ConnectionState state = client.status().state();
        if (state == ConnectionState.CONNECTING || state == ConnectionState.NEGOTIATING
                || state == ConnectionState.STOPPING) {
            out.info("A connection attempt is already in progress.");
            return;
        }
        if (client.isConnected()) {
            if (!reconnect) {
                sendStatus(client, out);
                return;
            }
            client.disconnect();
        }
        out.info(reconnect ? "Reconnecting..." : "Connecting...");
        client.connect().whenComplete((status, failure) -> {
            if (failure == null) {
                sendStatus(client, out);
            } else {
                Throwable cause = failure;
                while (cause.getCause() != null) {
                    cause = cause.getCause();
                }
                String message = cause.getMessage() == null
                        ? cause.getClass().getSimpleName() : cause.getMessage();
                out.error("Connect failed: " + message);
            }
        });
    }

    private static void sendStatus(MinegasmClient client, Feedback out) {
        ProviderStatus status = client.status();
        String adapter = client.config().raw().buttplug().client();
        out.info("Minegasm: " + status.state().name().toLowerCase(Locale.ROOT)
                + ", " + status.deviceCount()
                + (status.deviceCount() == 1 ? " device" : " devices")
                + ", adapter " + adapter + ".");
        for (String line : client.bridgeStatusLines()) {
            out.info(line);
        }
    }

    private static Integer parseInt(String token) {
        try {
            return Integer.valueOf(token);
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }

    private static Map<String, GameEventKind> triggerEvents() {
        Map<String, GameEventKind> events = new LinkedHashMap<>();
        GameEventKind[] exposed = {
                GameEventKind.ATTACK, GameEventKind.BLOCK_BROKEN, GameEventKind.PLACE,
                GameEventKind.HARVEST, GameEventKind.FISHING_BITE,
                GameEventKind.ADVANCEMENT, GameEventKind.EXPLOSION};
        for (GameEventKind kind : exposed) {
            events.put(kind.key(), kind);
        }
        return events;
    }
}
