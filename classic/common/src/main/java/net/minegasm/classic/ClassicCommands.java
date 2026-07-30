package net.minegasm.classic;

import net.minegasm.buttplug.ConnectionState;
import net.minegasm.buttplug.ProviderStatus;
import net.minegasm.client.MinegasmClient;
import net.minegasm.config.HapticConfig;
import net.minegasm.config.MinegasmMode;
import net.minegasm.config.RecipePackId;
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
            "mode", "recipe", "stop", "resume", "test", "trigger");

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
            out.info("Mode: " + cfg.identity().mode().name().toLowerCase(Locale.ROOT)
                    + ". Options: " + names(MinegasmMode.values()));
            return;
        }
        MinegasmMode mode = MinegasmMode.fromString(args[1], null);
        if (mode == null) {
            out.error("Unknown mode '" + args[1] + "'. Options: " + names(MinegasmMode.values()));
            return;
        }
        applyIdentity(client, cfg, new HapticConfig.Identity(cfg.identity().recipePack(), mode.name()));
        out.info("Mode set to " + mode.name().toLowerCase(Locale.ROOT) + ".");
    }

    private static void recipe(MinegasmClient client, Feedback out, String[] args) {
        HapticConfig cfg = client.config().raw();
        if (args.length < 2) {
            out.info("Recipe: " + cfg.identity().recipePackId().name().toLowerCase(Locale.ROOT)
                    + ". Options: " + names(RecipePackId.values()));
            return;
        }
        RecipePackId pack = RecipePackId.fromString(args[1], null);
        if (pack == null) {
            out.error("Unknown recipe '" + args[1] + "'. Options: " + names(RecipePackId.values()));
            return;
        }
        applyIdentity(client, cfg, new HapticConfig.Identity(
                pack.name().toLowerCase(Locale.ROOT), cfg.identity().compatibilityMode()));
        out.info("Recipe set to " + pack.name().toLowerCase(Locale.ROOT) + ".");
    }

    /** Persist a new identity (recipe pack + mode), preserving everything else in the config. */
    private static void applyIdentity(MinegasmClient client, HapticConfig cfg,
                                      HapticConfig.Identity identity) {
        client.updateConfig(new HapticConfig(cfg.schemaVersion(), identity, cfg.global(),
                cfg.buttplug(), cfg.events(), cfg.outputPolicy(), cfg.devices(),
                cfg.positionCalibrations(), cfg.accumulation(), cfg.customIntensity()));
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

    private static void test(MinegasmClient client, Feedback out, String[] args) {
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
        if (!client.isConnected()) {
            out.error("Not connected. Run '/minegasm connect' first.");
            return;
        }
        if (!client.config().enabled() || !client.runtime().worker().isOutputEnabled()) {
            out.error("Haptics are disabled or stopped. Enable and resume first.");
            return;
        }
        int targeted = client.testPulse(percent / 100f, durationMs);
        if (targeted == 0) {
            out.error("No connected device has a feature to test.");
            return;
        }
        out.info("Sent a " + percent + "% / " + durationMs + " ms test to " + targeted
                + (targeted == 1 ? " feature." : " features."));
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
