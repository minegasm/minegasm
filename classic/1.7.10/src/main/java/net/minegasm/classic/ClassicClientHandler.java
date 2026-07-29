package net.minegasm.classic;

import net.minegasm.client.MinegasmClient;
import net.minegasm.core.GameEventKind;
import net.minegasm.core.MaterialFeel;
import net.minegasm.core.RawGameEvent;
import net.minegasm.observe.ClientStateSnapshot;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.projectile.EntityFishHook;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.MovingObjectPosition;

import net.minecraftforge.client.ClientCommandHandler;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The Minecraft 1.7.10 observation and UI layer. This line predates the 1.8 API changes, so it stands
 * apart from the shared 1.8.9/1.12.2 handler: it uses {@code cpw.mods.fml}, has no block state or
 * {@code BlockPos} (blocks are a {@link Block} plus integer coordinates), and its client player is
 * {@link EntityClientPlayerMP}. It drives the same shared {@link MinegasmClient} once per client tick,
 * emitting attack/block-break/placement/fishing-bite events and a {@link ClientStateSnapshot}, and owns
 * the panic/connect key bindings and the hand-parsed {@code /minegasm} (and {@code /mg}) command whose
 * parsing lives in {@link ClassicCommands}.
 *
 * <p>1.7.10 has no client-side destroy-progress getter, so mining is inferred from the attack key held
 * on a block target rather than the game mode's block-hit state.
 */
public final class ClassicClientHandler {

    private static final String CATEGORY = "key.categories.minegasm";
    private static final String CHAT_PREFIX = "§d[Minegasm]§r ";

    private final MinegasmClient client;
    private final KeyBinding panicKey = new KeyBinding("key.minegasm.panic", Keyboard.KEY_NONE, CATEGORY);
    private final KeyBinding connectKey = new KeyBinding("key.minegasm.connect", Keyboard.KEY_NONE, CATEGORY);

    private long gameTick;

    private boolean prevAttackDown;
    private boolean prevMining;
    private String prevMiningTargetKey;
    private Block prevLookedBlock;
    private float prevLookedHardnessRaw;
    private String prevLookedPos;
    private boolean prevBobberInWater;

    public ClassicClientHandler(MinegasmClient client) {
        this.client = client;
    }

    public void register() {
        ClientRegistry.registerKeyBinding(panicKey);
        ClientRegistry.registerKeyBinding(connectKey);
        FMLCommonHandler.instance().bus().register(this);
        ClientCommandHandler.instance.registerCommand(new MinegasmCommand());
    }

    // --- tick loop -------------------------------------------------------------------------

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        gameTick++;

        while (panicKey.isPressed()) {
            client.panic();
            sendChat("Haptic output stopped (panic).");
        }
        while (connectKey.isPressed()) {
            if (!client.isConnected()) {
                sendChat("Connecting...");
                client.connect();
            }
        }

        long nowNs = System.nanoTime();
        client.onClientTickEnd(sample(mc, nowNs));
    }

    // --- sampling --------------------------------------------------------------------------

    private ClientStateSnapshot sample(Minecraft mc, long nowNs) {
        EntityClientPlayerMP player = mc.thePlayer;
        WorldClient world = mc.theWorld;
        boolean worldReady = player != null && world != null;
        boolean paused = mc.isGamePaused();
        if (!worldReady) {
            resetPerWorld();
            return ClientStateSnapshot.empty(gameTick);
        }

        boolean miningNow = mining(mc, player);
        String miningKey = miningNow ? blockPosKey(mc) : null;
        Block looked = lookedBlock(mc);
        MaterialFeel material = miningNow ? MaterialClassifier.classify(blockId(looked)) : MaterialFeel.UNKNOWN;
        float hardness = miningNow ? hardness(mc, looked) : 0f;

        detectAttack(mc, player, nowNs);
        detectBlockBreak(mc, nowNs);
        detectPlacement(mc, player, nowNs);
        detectFishingBite(player, nowNs);

        prevMining = miningNow;
        prevMiningTargetKey = miningKey;

        return new ClientStateSnapshot(
                player.getHealth(),
                player.getAbsorptionAmount(),
                player.getFoodStats().getFoodLevel(),
                player.experienceLevel,
                player.experience,
                player.experienceTotal,
                miningNow,
                optional(miningKey),
                0f, // 1.7.10 exposes no client-side destroy-progress accessor
                optional(miningNow ? blockId(looked) : null),
                material,
                hardness,
                player.isBurning(),
                player.isInsideOfMaterial(Material.water),
                player.fishEntity != null,
                bobberBite(player),
                paused,
                true,
                gameTick);
    }

    private void resetPerWorld() {
        prevAttackDown = false;
        prevMining = false;
        prevMiningTargetKey = null;
        prevLookedBlock = null;
        prevLookedPos = null;
        prevBobberInWater = false;
    }

    // --- discrete detectors ----------------------------------------------------------------

    private void detectAttack(Minecraft mc, EntityClientPlayerMP player, long nowNs) {
        boolean down = mc.gameSettings.keyBindAttack.getIsKeyPressed();
        boolean edge = down && !prevAttackDown;
        prevAttackDown = down;
        MovingObjectPosition hit = mc.objectMouseOver;
        if (!edge || hit == null
                || hit.typeOfHit != MovingObjectPosition.MovingObjectType.ENTITY
                || hit.entityHit == null) {
            return;
        }
        boolean critical = player.fallDistance > 0 && !player.onGround && !player.isInWater();
        Map<String, Object> payload = new HashMap<String, Object>();
        payload.put("cooldown", 1.0f);
        payload.put("critical", critical);
        payload.put("dedupe", hit.entityHit.getEntityId());
        client.recordEvent(new RawGameEvent(GameEventKind.ATTACK, gameTick, nowNs, payload));
    }

    private void detectBlockBreak(Minecraft mc, long nowNs) {
        Block wasLooking = prevLookedBlock;
        float wasHardness = prevLookedHardnessRaw;
        Block nowLooking = lookedBlock(mc);
        prevLookedBlock = nowLooking;
        prevLookedHardnessRaw = rawHardness(mc, nowLooking);
        boolean wasAir = wasLooking == null || isAir(wasLooking);
        boolean nowAir = nowLooking == null || isAir(nowLooking);
        if (prevMining && wasLooking != null && nowAir && !wasAir) {
            String id = blockId(wasLooking);
            Map<String, Object> payload = new HashMap<String, Object>();
            payload.put("ore", MaterialClassifier.isOre(id));
            payload.put("hardness", MaterialClassifier.normalizedHardness(wasHardness));
            payload.put("material", MaterialClassifier.classify(id).name());
            payload.put("dedupe", (prevMiningTargetKey == null ? "" : prevMiningTargetKey).hashCode());
            client.recordEvent(new RawGameEvent(GameEventKind.BLOCK_BROKEN, gameTick, nowNs, payload));
        }
    }

    private void detectPlacement(Minecraft mc, EntityClientPlayerMP player, long nowNs) {
        String pos = blockPosKey(mc);
        Block block = lookedBlock(mc);
        ItemStack held = player.getHeldItem();
        boolean placed = pos != null && !pos.equals(prevLookedPos)
                && block != null && !isAir(block)
                && held != null && held.getItem() instanceof ItemBlock;
        prevLookedPos = pos;
        if (placed) {
            Map<String, Object> payload = new HashMap<String, Object>();
            payload.put("material", MaterialClassifier.classify(blockId(block)).name());
            payload.put("dedupe", pos.hashCode());
            client.recordEvent(new RawGameEvent(GameEventKind.PLACE, gameTick, nowNs, payload));
        }
    }

    private void detectFishingBite(EntityClientPlayerMP player, long nowNs) {
        boolean bite = bobberBite(player);
        EntityFishHook hook = player.fishEntity;
        boolean inWater = hook != null && hook.isInWater();
        boolean edge = bite && !prevBobberInWater;
        prevBobberInWater = inWater && bite;
        if (edge) {
            client.recordEvent(RawGameEvent.of(GameEventKind.FISHING_BITE, gameTick, nowNs));
        }
    }

    // --- client helpers --------------------------------------------------------------------

    private boolean mining(Minecraft mc, EntityClientPlayerMP player) {
        // 1.7.10 has no client destroy-progress getter, so infer mining from the attack key held on a
        // block target. Creative instant-breaks are the only notable false positive, and block-break
        // events still fire independently.
        return mc.gameSettings.keyBindAttack.getIsKeyPressed()
                && mc.objectMouseOver != null
                && mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK;
    }

    private Block lookedBlock(Minecraft mc) {
        MovingObjectPosition hit = mc.objectMouseOver;
        if (hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                && mc.theWorld != null) {
            return mc.theWorld.getBlock(hit.blockX, hit.blockY, hit.blockZ);
        }
        return null;
    }

    private String blockPosKey(Minecraft mc) {
        MovingObjectPosition hit = mc.objectMouseOver;
        if (hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
            return hit.blockX + "," + hit.blockY + "," + hit.blockZ;
        }
        return null;
    }

    private boolean isAir(Block block) {
        return block == Blocks.air;
    }

    private String blockId(Block block) {
        if (block == null) {
            return null;
        }
        String registry = Block.blockRegistry.getNameForObject(block);
        return registry != null ? registry : block.getUnlocalizedName();
    }

    private float hardness(Minecraft mc, Block block) {
        return MaterialClassifier.normalizedHardness(rawHardness(mc, block));
    }

    /** Raw hardness of the block at the current look target, or -1 when unavailable. */
    private float rawHardness(Minecraft mc, Block block) {
        MovingObjectPosition hit = mc.objectMouseOver;
        if (block == null || isAir(block) || hit == null || mc.theWorld == null
                || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) {
            return -1f;
        }
        try {
            return block.getBlockHardness(mc.theWorld, hit.blockX, hit.blockY, hit.blockZ);
        } catch (RuntimeException unavailable) {
            return -1f;
        }
    }

    private boolean bobberBite(EntityClientPlayerMP player) {
        EntityFishHook hook = player.fishEntity;
        if (hook == null) {
            return false;
        }
        return hook.motionY < -0.075 && hook.isInWater()
                && Math.abs(hook.motionX) < 0.01 && Math.abs(hook.motionZ) < 0.01;
    }

    private static java.util.Optional<String> optional(String value) {
        return value == null ? java.util.Optional.<String>empty() : java.util.Optional.of(value);
    }

    // --- chat feedback ---------------------------------------------------------------------

    private void sendChat(final String message) {
        final Minecraft mc = Minecraft.getMinecraft();
        mc.addScheduledTask(new Runnable() {
            @Override
            public void run() {
                EntityClientPlayerMP player = mc.thePlayer;
                if (player != null) {
                    IChatComponent text = new ChatComponentText(CHAT_PREFIX + message);
                    player.addChatMessage(text);
                }
            }
        });
    }

    private final ClassicCommands.Feedback feedback = new ClassicCommands.Feedback() {
        @Override
        public void info(String message) {
            sendChat(message);
        }

        @Override
        public void error(String message) {
            sendChat("§c" + message);
        }
    };

    // --- command ---------------------------------------------------------------------------

    private final class MinegasmCommand implements ICommand {
        @Override
        public String getCommandName() {
            return "minegasm";
        }

        @Override
        public String getCommandUsage(ICommandSender sender) {
            return "/minegasm <" + String.join("|", ClassicCommands.SUBCOMMANDS) + ">";
        }

        @Override
        public List getCommandAliases() {
            return Collections.singletonList("mg");
        }

        @Override
        public void processCommand(ICommandSender sender, String[] args) {
            ClassicCommands.dispatch(client, gameTick, args, feedback);
        }

        @Override
        public boolean canCommandSenderUseCommand(ICommandSender sender) {
            return true;
        }

        @Override
        public List addTabCompletionOptions(ICommandSender sender, String[] args) {
            if (args.length == 1) {
                List<String> out = new ArrayList<String>();
                String prefix = args[0].toLowerCase(Locale.ROOT);
                for (String name : ClassicCommands.SUBCOMMANDS) {
                    if (name.startsWith(prefix)) {
                        out.add(name);
                    }
                }
                return out;
            }
            return Collections.emptyList();
        }

        @Override
        public boolean isUsernameIndex(String[] args, int index) {
            return false;
        }

        @Override
        public int compareTo(Object other) {
            return getCommandName().compareTo(((ICommand) other).getCommandName());
        }
    }
}
