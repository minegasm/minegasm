package net.minegasm.classic;

import net.minegasm.client.MinegasmClient;
import net.minegasm.core.GameEventKind;
import net.minegasm.core.MaterialFeel;
import net.minegasm.core.RawGameEvent;
import net.minegasm.observe.ClientStateSnapshot;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Builds a {@link ClientStateSnapshot} from the Minecraft 1.16.5 client and derives the discrete events
 * that read reliably on an unmodified multiplayer server (attack, block break, placement, fishing bite),
 * the same shape as the modern NeoForge sampler. It touches only vanilla {@code net.minecraft.*} types
 * (mapped to Mojang official names), so the Forge and Fabric subprojects share it unchanged; only the
 * loader hooks that drive it differ.
 */
public final class Sampler16 {

    private final MinegasmClient client;

    private boolean prevAttackDown;
    private boolean prevMining;
    private String prevMiningTargetKey;
    private BlockState prevLookedBlock;
    private float prevLookedHardnessRaw;
    private String prevLookedPos;
    private boolean prevBobberInWater;

    public Sampler16(MinegasmClient client) {
        this.client = client;
    }

    /** Sample the current client state and emit any discrete events observed since the last sample. */
    public ClientStateSnapshot sample(Minecraft mc, long gameTick, long nowNs) {
        LocalPlayer player = mc.player;
        boolean worldReady = player != null && mc.level != null;
        boolean paused = mc.isPaused();
        if (!worldReady) {
            resetPerWorld();
            return ClientStateSnapshot.empty(gameTick);
        }

        boolean miningNow = mining(mc);
        String miningKey = miningNow ? blockPosKey(mc) : null;
        BlockState looked = lookedBlock(mc);
        MaterialFeel material = miningNow ? MaterialClassifier.classify(blockId(looked)) : MaterialFeel.UNKNOWN;
        float hardness = miningNow ? MaterialClassifier.normalizedHardness(rawHardness(mc, looked)) : 0f;

        detectAttack(mc, player, gameTick, nowNs);
        detectBlockBreak(mc, gameTick, nowNs);
        detectPlacement(mc, player, gameTick, nowNs);
        detectFishingBite(player, gameTick, nowNs);

        prevMining = miningNow;
        prevMiningTargetKey = miningKey;

        return new ClientStateSnapshot(
                player.getHealth(),
                player.getAbsorptionAmount(),
                player.getFoodData().getFoodLevel(),
                player.experienceLevel,
                player.experienceProgress,
                player.totalExperience,
                miningNow,
                optional(miningKey),
                0f, // 1.16.5 MultiPlayerGameMode exposes no destroy-stage accessor
                optional(miningNow ? blockId(looked) : null),
                material,
                hardness,
                player.isOnFire(),
                player.isUnderWater(),
                player.fishing != null,
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

    private void detectAttack(Minecraft mc, LocalPlayer player, long tick, long nowNs) {
        boolean down = mc.options.keyAttack.isDown();
        boolean edge = down && !prevAttackDown;
        prevAttackDown = down;
        if (!edge || !(mc.hitResult instanceof EntityHitResult)) {
            return;
        }
        Entity target = ((EntityHitResult) mc.hitResult).getEntity();
        float cooldown = player.getAttackStrengthScale(0f);
        boolean critical = cooldown > 0.9f && player.fallDistance > 0
                && !player.isOnGround() && !player.isInWater();
        Map<String, Object> payload = new HashMap<String, Object>();
        payload.put("cooldown", cooldown);
        payload.put("critical", critical);
        payload.put("dedupe", target.getId());
        client.recordEvent(new RawGameEvent(GameEventKind.ATTACK, tick, nowNs, payload));
    }

    private void detectBlockBreak(Minecraft mc, long tick, long nowNs) {
        BlockState wasLooking = prevLookedBlock;
        float wasHardness = prevLookedHardnessRaw;
        BlockState nowLooking = lookedBlock(mc);
        prevLookedBlock = nowLooking;
        prevLookedHardnessRaw = rawHardness(mc, nowLooking);
        if (prevMining && wasLooking != null && (nowLooking == null || nowLooking.isAir())
                && !wasLooking.isAir()) {
            String id = blockId(wasLooking);
            Map<String, Object> payload = new HashMap<String, Object>();
            payload.put("ore", MaterialClassifier.isOre(id));
            payload.put("hardness", MaterialClassifier.normalizedHardness(wasHardness));
            payload.put("material", MaterialClassifier.classify(id).name());
            payload.put("dedupe", (prevMiningTargetKey == null ? "" : prevMiningTargetKey).hashCode());
            client.recordEvent(new RawGameEvent(GameEventKind.BLOCK_BROKEN, tick, nowNs, payload));
        }
    }

    private void detectPlacement(Minecraft mc, LocalPlayer player, long tick, long nowNs) {
        String pos = blockPosKey(mc);
        BlockState state = lookedBlock(mc);
        ItemStack held = player.getMainHandItem();
        boolean placed = pos != null && !pos.equals(prevLookedPos)
                && state != null && !state.isAir()
                && held != null && !held.isEmpty() && held.getItem() instanceof BlockItem;
        prevLookedPos = pos;
        if (placed) {
            Map<String, Object> payload = new HashMap<String, Object>();
            payload.put("material", MaterialClassifier.classify(blockId(state)).name());
            payload.put("dedupe", pos.hashCode());
            client.recordEvent(new RawGameEvent(GameEventKind.PLACE, tick, nowNs, payload));
        }
    }

    private void detectFishingBite(LocalPlayer player, long tick, long nowNs) {
        boolean bite = bobberBite(player);
        FishingHook hook = player.fishing;
        boolean inWater = hook != null && hook.isInWater();
        boolean edge = bite && !prevBobberInWater;
        prevBobberInWater = inWater && bite;
        if (edge) {
            client.recordEvent(RawGameEvent.of(GameEventKind.FISHING_BITE, tick, nowNs));
        }
    }

    // --- client helpers --------------------------------------------------------------------

    private boolean mining(Minecraft mc) {
        return mc.gameMode != null && mc.gameMode.isDestroying()
                && mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK;
    }

    private BlockState lookedBlock(Minecraft mc) {
        if (mc.hitResult instanceof BlockHitResult && mc.level != null) {
            return mc.level.getBlockState(((BlockHitResult) mc.hitResult).getBlockPos());
        }
        return null;
    }

    private String blockPosKey(Minecraft mc) {
        if (mc.hitResult instanceof BlockHitResult) {
            net.minecraft.core.BlockPos p = ((BlockHitResult) mc.hitResult).getBlockPos();
            return p.getX() + "," + p.getY() + "," + p.getZ();
        }
        return null;
    }

    private String blockId(BlockState state) {
        // Vanilla translation key (e.g. "block.minecraft.stone"); works on both loaders, unlike the
        // Forge-only getRegistryName(). Good enough for the name-based material classifier.
        return state == null ? null : state.getBlock().getDescriptionId();
    }

    /** Raw hardness (destroy speed) of the block at the current look target, or -1 when unavailable. */
    private float rawHardness(Minecraft mc, BlockState state) {
        if (state == null || state.isAir() || mc.level == null
                || !(mc.hitResult instanceof BlockHitResult)) {
            return -1f;
        }
        try {
            return state.getDestroySpeed(mc.level, ((BlockHitResult) mc.hitResult).getBlockPos());
        } catch (RuntimeException unavailable) {
            return -1f;
        }
    }

    private boolean bobberBite(LocalPlayer player) {
        FishingHook hook = player.fishing;
        if (hook == null) {
            return false;
        }
        Vec3 v = hook.getDeltaMovement();
        return v.y < -0.075 && hook.isInWater() && Math.abs(v.x) < 0.01 && Math.abs(v.z) < 0.01;
    }

    private static Optional<String> optional(String value) {
        return value == null ? Optional.<String>empty() : Optional.of(value);
    }
}
