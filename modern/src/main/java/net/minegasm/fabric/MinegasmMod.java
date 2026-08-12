//? if fabric {
/*package net.minegasm.fabric;

import net.minegasm.client.MinegasmClient;
import net.minegasm.core.GameEventKind;
import net.minegasm.core.RawGameEvent;
import net.minegasm.config.HapticConfig;
import net.minegasm.config.MinegasmMode;
import net.minegasm.config.TestOutputLimits;
import net.minegasm.neoforge.McCompat;
import net.minegasm.neoforge.MinecraftSampler;
import net.minegasm.neoforge.MinegasmHubScreen;
import net.minegasm.neoforge.ProviderFactory;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
//? if >=26.1.2 {
import net.minecraft.resources.Identifier;
//?}

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
//? if >=26.1.2 {
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
//?}
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
//? if >=26.1.2 {
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
//?}
import net.fabricmc.loader.api.FabricLoader;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;

/^*
 * Client-only mod entrypoint for the Fabric loader (brief §5.1, ADR-003, ADR-012). Mirrors
 * {@link net.minegasm.neoforge.MinegasmMod} one-for-one: owns the loader-independent
 * {@link MinegasmClient}, drives it from the client tick, and registers key bindings. No device I/O
 * happens on the client thread.
 *
 * <p>Lives in the shared root {@code src} behind a whole-file {@code //? if fabric} Stonecutter
 * loader guard: for any non-Fabric variant the guard comments the entire file out, so its
 * {@code net.fabricmc.*} imports never reach a NeoForge or Forge compile (docs/adr/ADR-013). The two
 * vanilla APIs that differ between the 26.1.2 and 26.2 lines (the screen setter and the toast-manager
 * accessor) are handled by {@link McCompat}, so this single file serves every Minecraft line rather
 * than one copy per line.
 *
 * <p>Wired to Fabric API's event-object model ({@code Event<T>.register(...)}, not an annotated
 * event bus); see {@code package-info} for provenance. Fabric client commands run against
 * {@link FabricClientCommandSource}, not vanilla {@code CommandSourceStack}. Its feedback methods
 * take a plain {@code Component} rather than vanilla's {@code Supplier<Component>} + broadcast flag.
 * Core Fabric has no mods-list config-screen extension point; {@code key.minegasm.config} opens the
 * screen directly. The optional {@link ModMenuIntegration} adds a mods-list entry too, but only when
 * the third-party ModMenu is installed (compile-only dependency).
 ^/
public final class MinegasmMod implements ClientModInitializer {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<String, GameEventKind> TRIGGER_EVENTS = triggerEvents();
    private static final java.util.List<String> MODE_NAMES = lowerNames(MinegasmMode.values());

    // The live client, published for the optional ModMenu integration (a separate `modmenu`
    // entrypoint ModMenu instantiates on its own, so it cannot reach the instance field). Written
    // once in the constructor before any screen can be opened; volatile for safe cross-thread reads.
    private static volatile MinegasmClient activeClient;

    private final MinegasmClient client;
    private final MinecraftSampler sampler = new MinecraftSampler();

    private KeyMapping panicKey;
    private KeyMapping connectKey;
    private KeyMapping configKey;
    //? if >=26.1.2 {
    private static final KeyMapping.Category KEY_CATEGORY = new KeyMapping.Category(
            Identifier.fromNamespaceAndPath("minegasm", "controls"));
    //?} else {
    /^private static final String KEY_CATEGORY = "key.categories.minegasm";
    ^///?}
    //? if >=1.21.1 {
    private static final SystemToast.SystemToastId PANIC_TOAST = new SystemToast.SystemToastId();
    //?} else {
    /^private static final SystemToast.SystemToastIds PANIC_TOAST =
            SystemToast.SystemToastIds.PERIODIC_NOTIFICATION;
    ^///?}
    private long gameTick;
    private boolean shortAliasAvailable;
    private boolean showFirstRunNotice;

    public MinegasmMod() {
        // Select the Buttplug backend from config (default native; "buttplug4j" for the library client).
        java.nio.file.Path configFile = FabricLoader.getInstance().getConfigDir().resolve("minegasm.json");
        this.client = new MinegasmClient(configFile, backend -> ProviderFactory.create(backend),
                net.minegasm.time.SystemClock.INSTANCE);
        activeClient = this.client;
        this.showFirstRunNotice = client.isFirstRun();
    }

    @Override
    public void onInitializeClient() {
        panicKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.minegasm.panic",
                InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, KEY_CATEGORY));
        connectKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.minegasm.connect",
                InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, KEY_CATEGORY));
        configKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.minegasm.config",
                InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, KEY_CATEGORY));

        ClientLifecycleEvents.CLIENT_STARTED.register(mc -> client.start());
        ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> client.shutdown());
        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
        ClientCommandRegistrationCallback.EVENT.register(this::onRegisterClientCommands);
    }

    private void onClientTick(Minecraft mc) {
        gameTick++;

        if (showFirstRunNotice && mc.gui != null) {
            showFirstRunNotice = false;
            showToast(mc, "minegasm.toast.first_run", "minegasm.toast.first_run_detail");
        }

        // Panic is the highest-priority action and must work in-world or in menus (brief §12.1).
        while (panicKey.consumeClick()) {
            client.panic();
            showToast(mc, "minegasm.toast.panic");
        }
        while (connectKey.consumeClick()) {
            if (!client.isConnected()) {
                client.connect();
            }
        }
        while (configKey.consumeClick()) {
            McCompat.setScreen(mc, new MinegasmHubScreen(null, client));
        }

        long nowNs = System.nanoTime();
        var snapshot = sampler.sample(mc, gameTick, nowNs, client::recordEvent);
        client.onClientTickEnd(snapshot);
    }

    private void onRegisterClientCommands(com.mojang.brigadier.CommandDispatcher<FabricClientCommandSource> dispatcher,
                                          net.minecraft.commands.CommandBuildContext buildContext) {
        var canonical = dispatcher.register(ClientCommands.literal("minegasm")
                .then(ClientCommands.literal("stop").executes(context -> {
                    client.panic();
                    showToast(Minecraft.getInstance(), "minegasm.toast.panic");
                    context.getSource().sendFeedback(Component.translatable("minegasm.command.stopped"));
                    return 1;
                }))
                .then(ClientCommands.literal("resume").executes(context -> {
                    client.clearPanic();
                    context.getSource().sendFeedback(Component.translatable("minegasm.command.resumed"));
                    return 1;
                }))
                .then(ClientCommands.literal("enable").executes(context ->
                        hapticsFromCommand(context.getSource(), true)))
                .then(ClientCommands.literal("disable").executes(context ->
                        hapticsFromCommand(context.getSource(), false)))
                .then(ClientCommands.literal("status").executes(context -> {
                    sendStatus(context.getSource());
                    return 1;
                }))
                .then(ClientCommands.literal("connect").executes(context -> {
                    connectFromCommand(context.getSource(), false);
                    return 1;
                }))
                .then(ClientCommands.literal("disconnect").executes(context -> {
                    client.disconnect();
                    context.getSource().sendFeedback(Component.translatable("minegasm.command.disconnected"));
                    return 1;
                }))
                .then(ClientCommands.literal("reconnect").executes(context -> {
                    connectFromCommand(context.getSource(), true);
                    return 1;
                }))
                .then(ClientCommands.literal("mode")
                        .executes(context -> {
                            sendMode(context.getSource());
                            return 1;
                        })
                        .then(ClientCommands.argument("mode", StringArgumentType.word())
                                .suggests((context, builder) ->
                                        SharedSuggestionProvider.suggest(MODE_NAMES, builder))
                                .executes(context -> modeFromCommand(context.getSource(),
                                        StringArgumentType.getString(context, "mode")))))
                .then(ClientCommands.literal("recipe")
                        .executes(context -> {
                            sendRecipe(context.getSource());
                            return 1;
                        })
                        .then(ClientCommands.argument("recipe", StringArgumentType.word())
                                .suggests((context, builder) ->
                                        SharedSuggestionProvider.suggest(recipeOptions(), builder))
                                .executes(context -> recipeFromCommand(context.getSource(),
                                        StringArgumentType.getString(context, "recipe")))))
                .then(ClientCommands.literal("bridge")
                        .executes(context -> {
                            sendBridge(context.getSource());
                            return 1;
                        })
                        .then(ClientCommands.literal("on")
                                .executes(context -> bridgeFromCommand(context.getSource(), true)))
                        .then(ClientCommands.literal("off")
                                .executes(context -> bridgeFromCommand(context.getSource(), false))))
                .then(ClientCommands.literal("adapter")
                        .executes(context -> {
                            sendAdapter(context.getSource());
                            return 1;
                        })
                        .then(ClientCommands.literal("native")
                                .executes(context -> adapterFromCommand(context.getSource(), "native")))
                        .then(ClientCommands.literal("buttplug4j")
                                .executes(context -> adapterFromCommand(context.getSource(), "buttplug4j"))))
                .then(ClientCommands.literal("test")
                        .executes(context -> testFromCommand(context.getSource(), 25, 400, false))
                        .then(ClientCommands.literal("buttplug")
                                .executes(context -> testButtplugFromCommand(context.getSource())))
                        .then(ClientCommands.literal("bridge")
                                .then(ClientCommands.argument("name", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                                enabledBridgeNames(), b))
                                        .executes(context -> testBridgeFromCommand(context.getSource(),
                                                StringArgumentType.getString(context, "name")))))
                        .then(ClientCommands.argument("strength-percent", IntegerArgumentType.integer(
                                TestOutputLimits.MIN_PERCENT, TestOutputLimits.MAX_PERCENT))
                                .executes(context -> testFromCommand(context.getSource(),
                                        IntegerArgumentType.getInteger(context, "strength-percent"), 400, false))
                                .then(ClientCommands.argument("duration-ms", IntegerArgumentType.integer(
                                        TestOutputLimits.MIN_DURATION_MS,
                                        TestOutputLimits.MAX_DURATION_MS))
                                        .executes(context -> testFromCommand(context.getSource(),
                                                IntegerArgumentType.getInteger(context, "strength-percent"),
                                                IntegerArgumentType.getInteger(context, "duration-ms"), false))
                                        .then(ClientCommands.literal("unsafe").executes(context -> testFromCommand(
                                                context.getSource(),
                                                IntegerArgumentType.getInteger(context, "strength-percent"),
                                                IntegerArgumentType.getInteger(context, "duration-ms"), true))))))
                .then(ClientCommands.literal("trigger")
                        .then(ClientCommands.argument("event", StringArgumentType.word())
                                .suggests((context, builder) ->
                                        SharedSuggestionProvider.suggest(TRIGGER_EVENTS.keySet(), builder))
                                .executes(context -> triggerFromCommand(context.getSource(),
                                        StringArgumentType.getString(context, "event"))))));
        if (dispatcher.getRoot().getChild("mg") == null) {
            dispatcher.register(ClientCommands.literal("mg").redirect(canonical));
            shortAliasAvailable = true;
            LOGGER.info("Registered Minegasm client commands at /minegasm with short alias /mg");
        } else {
            shortAliasAvailable = false;
            LOGGER.warn("Minegasm did not register the /mg alias because that command root is already in use; /minegasm remains available");
        }
    }

    private int hapticsFromCommand(FabricClientCommandSource source, boolean enable) {
        boolean changed = client.setHapticsEnabled(enable);
        String key = enable
                ? (changed ? "minegasm.command.haptics_enabled" : "minegasm.command.haptics_already_enabled")
                : (changed ? "minegasm.command.haptics_disabled" : "minegasm.command.haptics_already_disabled");
        source.sendFeedback(Component.translatable(key));
        return 1;
    }

    private int testButtplugFromCommand(FabricClientCommandSource source) {
        if (!client.isConnected()) {
            source.sendError(Component.translatable("minegasm.command.test_disconnected"));
            return 0;
        }
        if (!client.config().enabled() || !client.runtime().worker().isOutputEnabled()) {
            source.sendError(Component.translatable("minegasm.command.test_disabled"));
            return 0;
        }
        int targeted = client.testButtplugOutput(0.25f, 400);
        if (targeted == 0) {
            source.sendError(Component.translatable("minegasm.command.test_no_features"));
            return 0;
        }
        source.sendFeedback(Component.translatable("minegasm.command.test_sent", targeted, 25, 400));
        return targeted;
    }

    private int testBridgeFromCommand(FabricClientCommandSource source, String name) {
        if (!client.config().enabled() || !client.runtime().worker().isOutputEnabled()) {
            source.sendError(Component.translatable("minegasm.command.test_disabled"));
            return 0;
        }
        if (!bridgeEnabled(name)) {
            source.sendError(Component.translatable("minegasm.command.test_bridge_unknown", name));
            return 0;
        }
        client.testBridgeOutput(name, 0.25f, 400);
        source.sendFeedback(Component.translatable("minegasm.command.test_sent_bridge", 25, 400));
        return 1;
    }

    private java.util.List<String> enabledBridgeNames() {
        java.util.List<String> names = new java.util.ArrayList<>();
        for (net.minegasm.config.HapticConfig.Bridge bridge : client.config().raw().bridges()) {
            if (bridge.enabled()) {
                names.add(bridge.name());
            }
        }
        return names;
    }

    private boolean bridgeEnabled(String name) {
        return client.config().raw().bridges().stream()
                .anyMatch(bridge -> bridge.enabled() && bridge.name().equals(name));
    }

    private int testFromCommand(FabricClientCommandSource source, int strengthPercent, int durationMs,
                                boolean unsafeConfirmed) {
        var global = client.config().raw().global();
        if ((strengthPercent > global.testMaxPercent()
                || durationMs > global.testMaxDurationMs()) && !unsafeConfirmed) {
            source.sendError(Component.translatable("minegasm.command.test_unsafe_confirmation",
                    strengthPercent, durationMs, strengthPercent, durationMs));
            return 0;
        }
        if (strengthPercent > global.unsafeTestMaxPercent()
                || durationMs > global.unsafeTestMaxDurationMs()) {
            source.sendError(Component.translatable("minegasm.command.test_configured_cap",
                    global.unsafeTestMaxPercent(), global.unsafeTestMaxDurationMs()));
            return 0;
        }
        boolean anyBridge = client.config().raw().bridges().stream()
                .anyMatch(net.minegasm.config.HapticConfig.Bridge::enabled);
        if (!client.isConnected() && !anyBridge) {
            source.sendError(Component.translatable("minegasm.command.test_disconnected"));
            return 0;
        }
        if (!client.config().enabled() || !client.runtime().worker().isOutputEnabled()) {
            source.sendError(Component.translatable("minegasm.command.test_disabled"));
            return 0;
        }
        int targeted = client.testPulse(strengthPercent / 100f, durationMs);
        if (targeted == 0 && !anyBridge) {
            source.sendError(Component.translatable("minegasm.command.test_no_features"));
            return 0;
        }
        if (targeted > 0) {
            source.sendFeedback(Component.translatable("minegasm.command.test_sent",
                    targeted, strengthPercent, durationMs));
        } else {
            source.sendFeedback(Component.translatable("minegasm.command.test_sent_bridge",
                    strengthPercent, durationMs));
        }
        return targeted;
    }

    private void sendMode(FabricClientCommandSource source) {
        source.sendFeedback(Component.translatable("minegasm.command.mode_current",
                client.config().raw().profile().mode().name().toLowerCase(Locale.ROOT)));
    }

    private int modeFromCommand(FabricClientCommandSource source, String name) {
        MinegasmMode mode = MinegasmMode.fromString(name, null);
        if (mode == null) {
            source.sendError(Component.translatable("minegasm.command.mode_unknown", name));
            return 0;
        }
        HapticConfig cfg = client.config().raw();
        applyProfile(new HapticConfig.Profile(cfg.profile().recipePack(), mode.name()));
        source.sendFeedback(Component.translatable("minegasm.command.mode_set",
                mode.name().toLowerCase(Locale.ROOT)));
        return 1;
    }

    private void sendRecipe(FabricClientCommandSource source) {
        source.sendFeedback(Component.translatable("minegasm.command.recipe_current",
                client.config().raw().profile().recipePack()));
    }

    private int recipeFromCommand(FabricClientCommandSource source, String name) {
        String selected = matchRecipe(name);
        if (selected == null) {
            source.sendError(Component.translatable("minegasm.command.recipe_unknown", name));
            return 0;
        }
        HapticConfig cfg = client.config().raw();
        applyProfile(new HapticConfig.Profile(selected, cfg.profile().hapticMode()));
        source.sendFeedback(Component.translatable("minegasm.command.recipe_set", selected));
        return 1;
    }

    // The selectable recipe pack ids: the two built-ins plus every loaded file pack (ADR-017).
    private java.util.List<String> recipeOptions() {
        java.util.List<String> options = new java.util.ArrayList<>();
        options.add("classic");
        options.add("balanced");
        for (net.minegasm.pack.ScenePackInfo info : client.scenePacks()) {
            if (!options.contains(info.id())) {
                options.add(info.id());
            }
        }
        return options;
    }

    // Resolve a requested pack id against the selectable options, case-insensitively.
    private String matchRecipe(String name) {
        for (String option : recipeOptions()) {
            if (option.equalsIgnoreCase(name)) {
                return option;
            }
        }
        return null;
    }

    private void sendBridge(FabricClientCommandSource source) {
        source.sendFeedback(Component.translatable("minegasm.command.bridge_current",
                client.config().raw().bridges().get(0).enabled() ? "on" : "off"));
    }

    private int bridgeFromCommand(FabricClientCommandSource source, boolean enable) {
        HapticConfig cfg = client.config().raw();
        java.util.List<HapticConfig.Bridge> bridges = new java.util.ArrayList<>(cfg.bridges());
        HapticConfig.Bridge b = bridges.get(0);
        bridges.set(0, new HapticConfig.Bridge(b.name(), enable, b.url(), b.transport(), b.allowRemote(),
                b.id())); // preserve the id so a toggle doesn't reconnect the endpoint
        client.updateConfig(new HapticConfig(cfg.schemaVersion(), cfg.profile(), cfg.global(),
                cfg.buttplug(), cfg.events(), cfg.outputPolicy(), cfg.devices(),
                cfg.positionCalibrations(), cfg.accumulation(), cfg.customIntensity(), bridges));
        source.sendFeedback(Component.translatable("minegasm.command.bridge_set",
                enable ? "on" : "off"));
        return 1;
    }

    private void sendAdapter(FabricClientCommandSource source) {
        source.sendFeedback(Component.translatable("minegasm.command.adapter_current",
                client.backend()));
    }

    private int adapterFromCommand(FabricClientCommandSource source, String backend) {
        boolean changed = client.setBackend(backend);
        source.sendFeedback(Component.translatable(
                changed ? "minegasm.command.adapter_set" : "minegasm.command.adapter_unchanged",
                backend));
        return 1;
    }

    // Persist a new profile (recipe pack + mode), preserving everything else in the config.
    private void applyProfile(HapticConfig.Profile profile) {
        HapticConfig cfg = client.config().raw();
        client.updateConfig(new HapticConfig(cfg.schemaVersion(), profile, cfg.global(),
                cfg.buttplug(), cfg.events(), cfg.outputPolicy(), cfg.devices(),
                cfg.positionCalibrations(), cfg.accumulation(), cfg.customIntensity(), cfg.bridges()));
    }

    private static java.util.List<String> lowerNames(Enum<?>[] values) {
        java.util.List<String> names = new java.util.ArrayList<>(values.length);
        for (Enum<?> value : values) {
            names.add(value.name().toLowerCase(Locale.ROOT));
        }
        return names;
    }

    private int triggerFromCommand(FabricClientCommandSource source, String name) {
        GameEventKind kind = TRIGGER_EVENTS.get(name.toLowerCase(Locale.ROOT));
        if (kind == null) {
            source.sendError(Component.translatable("minegasm.command.trigger_unknown", name));
            return 0;
        }
        if (!client.isConnected() || !client.config().enabled()
                || !client.runtime().worker().isOutputEnabled()) {
            source.sendError(Component.translatable("minegasm.command.trigger_unavailable"));
            return 0;
        }
        client.recordEvent(RawGameEvent.of(kind, gameTick, System.nanoTime()));
        source.sendFeedback(Component.translatable("minegasm.command.triggered", kind.key()));
        return 1;
    }

    private static Map<String, GameEventKind> triggerEvents() {
        Map<String, GameEventKind> events = new LinkedHashMap<>();
        for (GameEventKind kind : new GameEventKind[] {
                GameEventKind.ATTACK, GameEventKind.BLOCK_BROKEN, GameEventKind.PLACE,
                GameEventKind.HARVEST, GameEventKind.FISHING_BITE,
                GameEventKind.ADVANCEMENT, GameEventKind.EXPLOSION}) {
            events.put(kind.key(), kind);
        }
        return Map.copyOf(events);
    }

    private void sendStatus(FabricClientCommandSource source) {
        var status = client.status();
        source.sendFeedback(Component.translatable("minegasm.command.status",
                Component.translatable("minegasm.connection.state."
                        + status.state().name().toLowerCase(Locale.ROOT)),
                status.deviceCount(),
                Component.translatable("minegasm.adapter."
                        + client.config().raw().buttplug().client().toLowerCase(Locale.ROOT))));
        for (String line : client.bridgeStatusLines()) {
            source.sendFeedback(Component.literal(line));
        }
        if (!shortAliasAvailable) {
            source.sendFeedback(Component.translatable("minegasm.command.alias_unavailable"));
        }
    }

    private void connectFromCommand(FabricClientCommandSource source, boolean reconnect) {
        var state = client.status().state();
        if (state == net.minegasm.buttplug.ConnectionState.CONNECTING
                || state == net.minegasm.buttplug.ConnectionState.NEGOTIATING
                || state == net.minegasm.buttplug.ConnectionState.STOPPING) {
            source.sendFeedback(Component.translatable("minegasm.command.connection_busy"));
            return;
        }
        if (!reconnect && client.isConnected()) {
            sendStatus(source);
            return;
        }
        if (reconnect && client.isConnected()) {
            client.disconnect();
        }
        source.sendFeedback(Component.translatable(
                reconnect ? "minegasm.command.reconnecting" : "minegasm.command.connecting"));
        client.connect().whenComplete((status, failure) -> Minecraft.getInstance().execute(() -> {
            if (failure == null) {
                sendStatus(source);
            } else {
                Throwable cause = failure;
                while (cause.getCause() != null) {
                    cause = cause.getCause();
                }
                String message = cause.getMessage() == null
                        ? cause.getClass().getSimpleName() : cause.getMessage();
                source.sendError(Component.translatable("minegasm.command.connect_failed", message));
            }
        }));
    }

    private void showToast(Minecraft mc, String key) {
        showToast(mc, key, null);
    }

    private void showToast(Minecraft mc, String key, String detailKey) {
        // A concrete toast is added by the UI layer; kept minimal here to limit speculative API use.
        if (mc != null && mc.gui != null) {
            McCompat.showToast(mc, PANIC_TOAST, Component.translatable(key),
                    detailKey == null ? Component.empty() : Component.translatable(detailKey));
        }
    }

    /^* Convenience for a config screen or other UI to obtain the client. ^/
    public MinegasmClient client() {
        return client;
    }

    /^* The live client for the optional ModMenu integration; null only before init (never in practice
     * by the time the mods list can open a config screen). ^/
    public static MinegasmClient activeClient() {
        return activeClient;
    }
}

//? if <26.1.2 {
/^// Pre-26.1.2 Fabric API compat shims: 1.21.1's fabric-api ships the pre-rename
// fabric-command-api-v2 (ClientCommandManager, not ClientCommands) and fabric-key-binding-api-v1
// (KeyBindingHelper.registerKeyBinding, not fabric-key-mapping-api-v1's KeyMappingHelper.
// registerKeyMapping). These package-private shims keep every call site above unchanged.
final class ClientCommands {
    private ClientCommands() {
    }

    static com.mojang.brigadier.builder.LiteralArgumentBuilder<FabricClientCommandSource> literal(
            String name) {
        return net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal(name);
    }

    static <T> com.mojang.brigadier.builder.RequiredArgumentBuilder<FabricClientCommandSource, T> argument(
            String name, com.mojang.brigadier.arguments.ArgumentType<T> type) {
        return net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument(name, type);
    }
}

final class KeyMappingHelper {
    private KeyMappingHelper() {
    }

    static KeyMapping registerKeyMapping(KeyMapping mapping) {
        return net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper.registerKeyBinding(mapping);
    }
}
^///?}
*///?}
