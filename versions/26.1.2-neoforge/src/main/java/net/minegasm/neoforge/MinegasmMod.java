package net.minegasm.neoforge;

import net.minegasm.client.MinegasmClient;
import net.minegasm.core.GameEventKind;
import net.minegasm.core.RawGameEvent;
import net.minegasm.config.TestOutputLimits;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.resources.Identifier;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.lifecycle.ClientStoppingEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Client-only mod entrypoint (brief §5.1, ADR-002/003). Owns the loader-independent
 * {@link MinegasmClient} and drives it from the client tick: each tick it samples state and forwards
 * discrete events, then hands the snapshot to the engine. Registers key bindings (panic stop, quick
 * connect, config screen) and the in-game config screen. No device I/O happens on the client thread.
 *
 * <p>Lives under {@code versions/26.1.2-neoforge/src}, not the shared root {@code src}: NeoForge is now
 * one of several loaders sharing {@code net.minegasm.neoforge}'s loader-agnostic classes (sampler,
 * screens, widgets, {@code ProviderFactory}), and this is the one class in the package that actually
 * touches NeoForge's own API — keeping it out of the shared tree means Forge/Fabric compiles never see
 * these imports (ADR-011, ADR-012). The 26.2 variant has its own copy
 * ({@code versions/26.2-neoforge/src/.../MinegasmMod.java}) using that line's newer toast API
 * directly, since a per-version file no longer needs a Stonecutter guard for that difference.
 */
@Mod(value = "minegasm", dist = Dist.CLIENT)
public final class MinegasmMod {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<String, GameEventKind> TRIGGER_EVENTS = triggerEvents();

    private final MinegasmClient client;
    private final MinecraftSampler sampler = new MinecraftSampler();

    private static KeyMapping panicKey;
    private static KeyMapping connectKey;
    private static final KeyMapping.Category KEY_CATEGORY = new KeyMapping.Category(
            Identifier.fromNamespaceAndPath("minegasm", "controls"));
    private static final SystemToast.SystemToastId PANIC_TOAST = new SystemToast.SystemToastId();
    private long gameTick;
    private boolean shortAliasAvailable;
    private boolean showFirstRunNotice;

    public MinegasmMod(IEventBus modBus, ModContainer container) {
        // Select the Buttplug backend from config (default buttplug4j; "native" for the JDK provider).
        java.nio.file.Path configFile = FMLPaths.CONFIGDIR.get().resolve("minegasm.json");
        this.client = new MinegasmClient(configFile, ProviderFactory.create(configFile),
                net.minegasm.time.SystemClock.INSTANCE);
        this.showFirstRunNotice = client.isFirstRun();

        modBus.addListener(this::onClientSetup);
        modBus.addListener(MinegasmMod::onRegisterKeyMappings);
        NeoForge.EVENT_BUS.addListener(this::onClientTick);
        NeoForge.EVENT_BUS.addListener(this::onClientStopping);
        NeoForge.EVENT_BUS.addListener(this::onRegisterClientCommands);

        // In-game config screen from the mods list (brief §11.2).
        container.registerExtensionPoint(IConfigScreenFactory.class,
                (mc, parent) -> new MinegasmConfigScreen(parent, client));
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(client::start);
    }

    private static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.registerCategory(KEY_CATEGORY);
        panicKey = new KeyMapping("key.minegasm.panic", InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN, KEY_CATEGORY);
        connectKey = new KeyMapping("key.minegasm.connect", InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN, KEY_CATEGORY);
        event.register(panicKey);
        event.register(connectKey);
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        gameTick++;

        if (showFirstRunNotice && mc.gui != null) {
            showFirstRunNotice = false;
            showToast(mc, "minegasm.toast.first_run", "minegasm.toast.first_run_detail");
        }

        // Panic is the highest-priority action and must work in-world or in menus (brief §12.1).
        if (panicKey != null) {
            while (panicKey.consumeClick()) {
                client.panic();
                showToast(mc, "minegasm.toast.panic");
            }
        }
        if (connectKey != null) {
            while (connectKey.consumeClick()) {
                if (!client.isConnected()) {
                    client.connect();
                }
            }
        }

        long nowNs = System.nanoTime();
        var snapshot = sampler.sample(mc, gameTick, nowNs, client::recordEvent);
        client.onClientTickEnd(snapshot);
    }

    private void onClientStopping(ClientStoppingEvent event) {
        client.shutdown();
    }

    private void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        var dispatcher = event.getDispatcher();
        var canonical = dispatcher.register(Commands.literal("minegasm")
                .then(Commands.literal("stop").executes(context -> {
                    client.panic();
                    showToast(Minecraft.getInstance(), "minegasm.toast.panic");
                    context.getSource().sendSuccess(
                            () -> Component.translatable("minegasm.command.stopped"), false);
                    return 1;
                }))
                .then(Commands.literal("resume").executes(context -> {
                    client.clearPanic();
                    context.getSource().sendSuccess(
                            () -> Component.translatable("minegasm.command.resumed"), false);
                    return 1;
                }))
                .then(Commands.literal("status").executes(context -> {
                    sendStatus(context.getSource());
                    return 1;
                }))
                .then(Commands.literal("connect").executes(context -> {
                    connectFromCommand(context.getSource(), false);
                    return 1;
                }))
                .then(Commands.literal("disconnect").executes(context -> {
                    client.disconnect();
                    context.getSource().sendSuccess(
                            () -> Component.translatable("minegasm.command.disconnected"), false);
                    return 1;
                }))
                .then(Commands.literal("reconnect").executes(context -> {
                    connectFromCommand(context.getSource(), true);
                    return 1;
                }))
                .then(Commands.literal("test")
                        .executes(context -> testFromCommand(context.getSource(), 25, 400, false))
                        .then(Commands.argument("strength-percent", IntegerArgumentType.integer(
                                TestOutputLimits.MIN_PERCENT, TestOutputLimits.MAX_PERCENT))
                                .executes(context -> testFromCommand(context.getSource(),
                                        IntegerArgumentType.getInteger(context, "strength-percent"), 400, false))
                                .then(Commands.argument("duration-ms", IntegerArgumentType.integer(
                                        TestOutputLimits.MIN_DURATION_MS,
                                        TestOutputLimits.MAX_DURATION_MS))
                                        .executes(context -> testFromCommand(context.getSource(),
                                                IntegerArgumentType.getInteger(context, "strength-percent"),
                                                IntegerArgumentType.getInteger(context, "duration-ms"), false))
                                        .then(Commands.literal("unsafe").executes(context -> testFromCommand(
                                                context.getSource(),
                                                IntegerArgumentType.getInteger(context, "strength-percent"),
                                                IntegerArgumentType.getInteger(context, "duration-ms"), true))))))
                .then(Commands.literal("trigger")
                        .then(Commands.argument("event", StringArgumentType.word())
                                .suggests((context, builder) ->
                                        SharedSuggestionProvider.suggest(TRIGGER_EVENTS.keySet(), builder))
                                .executes(context -> triggerFromCommand(context.getSource(),
                                        StringArgumentType.getString(context, "event"))))));
        if (dispatcher.getRoot().getChild("mg") == null) {
            dispatcher.register(Commands.literal("mg").redirect(canonical));
            shortAliasAvailable = true;
            LOGGER.info("Registered Minegasm client commands at /minegasm with short alias /mg");
        } else {
            shortAliasAvailable = false;
            LOGGER.warn("Minegasm did not register the /mg alias because that command root is already in use; /minegasm remains available");
        }
    }

    private int testFromCommand(CommandSourceStack source, int strengthPercent, int durationMs,
                                boolean unsafeConfirmed) {
        var global = client.config().raw().global();
        if ((strengthPercent > global.testMaxPercent()
                || durationMs > global.testMaxDurationMs()) && !unsafeConfirmed) {
            source.sendFailure(Component.translatable("minegasm.command.test_unsafe_confirmation",
                    strengthPercent, durationMs, strengthPercent, durationMs));
            return 0;
        }
        if (strengthPercent > global.unsafeTestMaxPercent()
                || durationMs > global.unsafeTestMaxDurationMs()) {
            source.sendFailure(Component.translatable("minegasm.command.test_configured_cap",
                    global.unsafeTestMaxPercent(), global.unsafeTestMaxDurationMs()));
            return 0;
        }
        if (!client.isConnected()) {
            source.sendFailure(Component.translatable("minegasm.command.test_disconnected"));
            return 0;
        }
        if (!client.config().enabled() || !client.runtime().worker().isOutputEnabled()) {
            source.sendFailure(Component.translatable("minegasm.command.test_disabled"));
            return 0;
        }
        int targeted = client.testPulse(strengthPercent / 100f, durationMs);
        if (targeted == 0) {
            source.sendFailure(Component.translatable("minegasm.command.test_no_features"));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("minegasm.command.test_sent",
                targeted, strengthPercent, durationMs), false);
        return targeted;
    }

    private int triggerFromCommand(CommandSourceStack source, String name) {
        GameEventKind kind = TRIGGER_EVENTS.get(name.toLowerCase(Locale.ROOT));
        if (kind == null) {
            source.sendFailure(Component.translatable("minegasm.command.trigger_unknown", name));
            return 0;
        }
        if (!client.isConnected() || !client.config().enabled()
                || !client.runtime().worker().isOutputEnabled()) {
            source.sendFailure(Component.translatable("minegasm.command.trigger_unavailable"));
            return 0;
        }
        client.recordEvent(RawGameEvent.of(kind, gameTick, System.nanoTime()));
        source.sendSuccess(() -> Component.translatable("minegasm.command.triggered", kind.key()), false);
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

    private void sendStatus(CommandSourceStack source) {
        var status = client.status();
        source.sendSuccess(() -> Component.translatable("minegasm.command.status",
                Component.translatable("minegasm.connection.state."
                        + status.state().name().toLowerCase(Locale.ROOT)),
                status.deviceCount(),
                Component.translatable("minegasm.adapter."
                        + client.config().raw().buttplug().client().toLowerCase(Locale.ROOT))), false);
        if (!shortAliasAvailable) {
            source.sendSuccess(() -> Component.translatable("minegasm.command.alias_unavailable"), false);
        }
    }

    private void connectFromCommand(CommandSourceStack source, boolean reconnect) {
        var state = client.status().state();
        if (state == net.minegasm.buttplug.ConnectionState.CONNECTING
                || state == net.minegasm.buttplug.ConnectionState.NEGOTIATING
                || state == net.minegasm.buttplug.ConnectionState.STOPPING) {
            source.sendSuccess(() -> Component.translatable("minegasm.command.connection_busy"), false);
            return;
        }
        if (!reconnect && client.isConnected()) {
            sendStatus(source);
            return;
        }
        if (reconnect && client.isConnected()) {
            client.disconnect();
        }
        source.sendSuccess(() -> Component.translatable(
                reconnect ? "minegasm.command.reconnecting" : "minegasm.command.connecting"), false);
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
                source.sendFailure(Component.translatable("minegasm.command.connect_failed", message));
            }
        }));
    }

    private void showToast(Minecraft mc, String key) {
        showToast(mc, key, null);
    }

    private void showToast(Minecraft mc, String key, String detailKey) {
        // A concrete toast is added by the UI layer; kept minimal here to limit speculative API use.
        if (mc != null && mc.gui != null) {
            SystemToast.addOrUpdate(mc.getToastManager(), PANIC_TOAST,
                    Component.translatable(key),
                    detailKey == null ? Component.empty() : Component.translatable(detailKey));
        }
    }

    /** Convenience for a config screen or other UI to obtain the client. */
    public MinegasmClient client() {
        return client;
    }
}
