package net.minegasm.classic;

import net.minegasm.client.MinegasmClient;
import net.minegasm.core.GameEventKind;
import net.minegasm.core.MaterialFeel;
import net.minegasm.core.RawGameEvent;
import net.minegasm.observe.ClientStateSnapshot;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.projectile.EntityFishHook;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.MovingObjectPosition;

import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import org.lwjgl.input.Keyboard;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Minecraft 1.8.9 observation and UI layer, the sibling of the 1.12.2 handler under
 * {@code classic/1.12.2}. Both drive the shared {@link MinegasmClient} once per client tick (sampling
 * continuous player state and emitting attack/block-break/placement/fishing-bite events) and own the
 * panic/connect key bindings plus the hand-parsed {@code /minegasm} (and {@code /mg}) command. They are
 * kept separate because the 1.8.9 client API differs from 1.12.2: {@code mc.thePlayer}/{@code theWorld},
 * {@code MovingObjectPosition} instead of {@code RayTraceResult}, the {@code net.minecraft.util}
 * {@code BlockPos} package, block name/hardness accessors, the {@code ICommand} method names, and the
 * {@code ChatComponentText}/{@code IChatComponent} text types.
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
    private IBlockState prevLookedBlock;
    private String prevLookedPos;
    private boolean prevBobberInWater;

    public ClassicClientHandler(MinegasmClient client) {
        this.client = client;
    }

    public void register() {
        ClientRegistry.registerKeyBinding(panicKey);
        ClientRegistry.registerKeyBinding(connectKey);
        MinecraftForge.EVENT_BUS.register(this);
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
        EntityPlayerSP player = mc.thePlayer;
        WorldClient world = mc.theWorld;
        boolean worldReady = player != null && world != null;
        boolean paused = mc.isGamePaused();
        if (!worldReady) {
            resetPerWorld();
            return ClientStateSnapshot.empty(gameTick);
        }

        boolean miningNow = mining(mc);
        String miningKey = miningNow ? blockPosKey(mc) : null;
        IBlockState looked = lookedBlock(mc);
        MaterialFeel material = miningNow ? MaterialClassifier.classify(blockId(looked)) : MaterialFeel.UNKNOWN;
        float hardness = miningNow ? hardness(mc, looked) : 0f;

        detectAttack(mc, player, nowNs);
        detectBlockBreak(mc, world, nowNs);
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
                0f, // no public destroy-progress accessor on 1.8.9 PlayerControllerMP
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

    private void detectAttack(Minecraft mc, EntityPlayerSP player, long nowNs) {
        boolean down = mc.gameSettings.keyBindAttack.isKeyDown();
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

    private void detectBlockBreak(Minecraft mc, WorldClient world, long nowNs) {
        IBlockState wasLooking = prevLookedBlock;
        IBlockState nowLooking = lookedBlock(mc);
        prevLookedBlock = nowLooking;
        boolean wasAir = wasLooking == null || isAir(wasLooking);
        boolean nowAir = nowLooking == null || isAir(nowLooking);
        if (prevMining && wasLooking != null && nowAir && !wasAir) {
            String id = blockId(wasLooking);
            Map<String, Object> payload = new HashMap<String, Object>();
            payload.put("ore", MaterialClassifier.isOre(id));
            payload.put("hardness", MaterialClassifier.normalizedHardness(rawHardness(mc, wasLooking)));
            payload.put("material", MaterialClassifier.classify(id).name());
            payload.put("dedupe", (prevMiningTargetKey == null ? "" : prevMiningTargetKey).hashCode());
            client.recordEvent(new RawGameEvent(GameEventKind.BLOCK_BROKEN, gameTick, nowNs, payload));
        }
    }

    private void detectPlacement(Minecraft mc, EntityPlayerSP player, long nowNs) {
        String pos = blockPosKey(mc);
        IBlockState state = lookedBlock(mc);
        ItemStack held = player.getHeldItem();
        boolean placed = pos != null && !pos.equals(prevLookedPos)
                && state != null && !isAir(state)
                && held != null && held.getItem() instanceof ItemBlock;
        prevLookedPos = pos;
        if (placed) {
            Map<String, Object> payload = new HashMap<String, Object>();
            payload.put("material", MaterialClassifier.classify(blockId(state)).name());
            payload.put("dedupe", pos.hashCode());
            client.recordEvent(new RawGameEvent(GameEventKind.PLACE, gameTick, nowNs, payload));
        }
    }

    private void detectFishingBite(EntityPlayerSP player, long nowNs) {
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

    private boolean mining(Minecraft mc) {
        return mc.playerController != null && mc.playerController.getIsHittingBlock()
                && mc.objectMouseOver != null
                && mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK;
    }

    private IBlockState lookedBlock(Minecraft mc) {
        MovingObjectPosition hit = mc.objectMouseOver;
        if (hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                && mc.theWorld != null) {
            return mc.theWorld.getBlockState(hit.getBlockPos());
        }
        return null;
    }

    private String blockPosKey(Minecraft mc) {
        MovingObjectPosition hit = mc.objectMouseOver;
        if (hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
            BlockPos p = hit.getBlockPos();
            return p.getX() + "," + p.getY() + "," + p.getZ();
        }
        return null;
    }

    private boolean isAir(IBlockState state) {
        return state.getBlock() == Blocks.air;
    }

    private String blockId(IBlockState state) {
        if (state == null) {
            return null;
        }
        Block block = state.getBlock();
        String registry = block.getRegistryName();
        return registry != null ? registry : block.getUnlocalizedName();
    }

    private float rawHardness(Minecraft mc, IBlockState state) {
        if (state == null || mc.theWorld == null || mc.objectMouseOver == null) {
            return -1f;
        }
        try {
            return state.getBlock().getBlockHardness(mc.theWorld, mc.objectMouseOver.getBlockPos());
        } catch (RuntimeException unavailable) {
            return -1f;
        }
    }

    private float hardness(Minecraft mc, IBlockState state) {
        return MaterialClassifier.normalizedHardness(rawHardness(mc, state));
    }

    private boolean bobberBite(EntityPlayerSP player) {
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
                EntityPlayerSP player = mc.thePlayer;
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
        public List<String> getCommandAliases() {
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
        public List<String> addTabCompletionOptions(ICommandSender sender, String[] args,
                                                    BlockPos targetPos) {
            if (args.length == 1) {
                List<String> out = new java.util.ArrayList<String>();
                String prefix = args[0].toLowerCase(java.util.Locale.ROOT);
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
        public int compareTo(ICommand other) {
            return getCommandName().compareTo(other.getCommandName());
        }
    }
}
