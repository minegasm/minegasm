package net.minegasm.neoforge;

import net.minegasm.core.GameEventKind;
import net.minegasm.core.MaterialFeel;
import net.minegasm.core.RawGameEvent;
import net.minegasm.observe.ClientStateSnapshot;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Builds a {@link ClientStateSnapshot} from the Minecraft client and derives the discrete events that
 * are most reliable when observed client-side on an unmodified multiplayer server (brief §2.4, §7):
 * attack (attack-key edge on an entity target), block break (targeted block turns to air), placement
 * (block appears at the look position after a use), and a fishing-bite heuristic. Server-authoritative
 * hooks are intentionally avoided so behaviour is identical in singleplayer and multiplayer.
 *
 * <p>State is tracked frame-to-frame; a discovered event is pushed to {@code sink}. Mappings target
 * 26.x and must be validated against the pinned build (see {@code package-info}).
 */
public final class MinecraftSampler {

    /** Sink for discrete events (typically {@code client::recordEvent}). */
    public interface EventSink {
        void record(RawGameEvent event);
    }

    private final AdvancementWatcher advancements = new AdvancementWatcher();

    private boolean prevAttackDown;
    private boolean prevMining;
    private String prevMiningTargetKey;
    private BlockState prevLookedBlock;
    private String prevLookedPos;
    private boolean prevBobberInWater;

    /**
     * Sample the current client state and emit any discrete events observed since the last sample.
     * Returns a snapshot for the continuous-state pipeline.
     */
    public ClientStateSnapshot sample(Minecraft mc, long gameTick, long nowNs, EventSink sink) {
        // Advancements are not pollable from client state; a listener feeds them in here (ADR-014).
        advancements.poll(mc, gameTick, nowNs, sink);

        LocalPlayer player = mc.player;
        boolean worldReady = player != null && mc.level != null;
        boolean paused = mc.isPaused();
        if (!worldReady) {
            resetPerWorld();
            return ClientStateSnapshot.empty(gameTick);
        }

        boolean miningNow = mining(mc);
        String miningKey = miningNow ? blockPosKey(mc) : null;
        MaterialFeel material = miningNow ? classify(lookedBlock(mc)) : MaterialFeel.UNKNOWN;
        float hardness = miningNow ? normalizedHardness(lookedBlock(mc)) : 0f;

        detectAttack(mc, gameTick, nowNs, sink);
        detectBlockBreak(mc, gameTick, nowNs, sink);
        detectPlacement(mc, gameTick, nowNs, sink);
        detectFishingBite(mc, gameTick, nowNs, sink);

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
                Optional.ofNullable(miningKey),
                miningProgress(mc),
                Optional.ofNullable(miningNow ? blockName(lookedBlock(mc)) : null),
                material,
                hardness,
                player.isOnFire(),
                player.isUnderWater(),
                player.fishing != null,
                bobberBite(mc),
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

    private void detectAttack(Minecraft mc, long tick, long nowNs, EventSink sink) {
        boolean down = mc.options.keyAttack.isDown();
        boolean edge = down && !prevAttackDown;
        prevAttackDown = down;
        if (!edge || !(mc.hitResult instanceof EntityHitResult hit)) {
            return;
        }
        Entity target = hit.getEntity();
        float cooldown = mc.player.getAttackStrengthScale(0f); // 1.0 = fully charged
        boolean critical = cooldown > 0.9f && mc.player.fallDistance > 0
                && !mc.player.onGround() && !mc.player.isInWater();
        Map<String, Object> payload = new HashMap<>();
        payload.put("cooldown", cooldown);
        payload.put("critical", critical);
        payload.put("dedupe", target.getId());
        sink.record(new RawGameEvent(GameEventKind.ATTACK, tick, nowNs, payload));
    }

    private void detectBlockBreak(Minecraft mc, long tick, long nowNs, EventSink sink) {
        // The block the player was mining last frame is now air => attribute a break to the player.
        BlockState wasLooking = prevLookedBlock;
        BlockState nowLooking = lookedBlock(mc);
        prevLookedBlock = nowLooking;
        if (prevMining && wasLooking != null && (nowLooking == null || nowLooking.isAir())
                && !wasLooking.isAir()) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("ore", isOre(wasLooking));
            payload.put("hardness", normalizedHardness(wasLooking));
            payload.put("material", classify(wasLooking).name());
            payload.put("dedupe", (prevMiningTargetKey == null ? "" : prevMiningTargetKey).hashCode());
            sink.record(new RawGameEvent(GameEventKind.BLOCK_BROKEN, tick, nowNs, payload));
        }
    }

    private void detectPlacement(Minecraft mc, long tick, long nowNs, EventSink sink) {
        // A new solid block appearing at the looked position while holding a block item.
        String pos = blockPosKey(mc);
        BlockState state = lookedBlock(mc);
        boolean placed = pos != null && !pos.equals(prevLookedPos)
                && state != null && !state.isAir()
                && mc.player.getMainHandItem() != null && isBlockItem(mc.player.getMainHandItem());
        prevLookedPos = pos;
        if (placed) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("material", classify(state).name());
            payload.put("dedupe", pos.hashCode());
            sink.record(new RawGameEvent(GameEventKind.PLACE, tick, nowNs, payload));
        }
    }

    private void detectFishingBite(Minecraft mc, long tick, long nowNs, EventSink sink) {
        boolean bite = bobberBite(mc);
        boolean inWater = mc.player.fishing != null && mc.player.fishing.isInWater();
        boolean edge = bite && !prevBobberInWater;
        prevBobberInWater = inWater && bite;
        if (edge) {
            sink.record(RawGameEvent.of(GameEventKind.FISHING_BITE, tick, nowNs));
        }
    }

    // --- client helpers --------------------------------------------------------------------

    private boolean mining(Minecraft mc) {
        // MultiPlayerGameMode owns the authoritative client-side block-destroy state. Checking
        // only the attack key and crosshair target also reports creative attacks and failed starts.
        return mc.gameMode != null && mc.gameMode.isDestroying()
                && mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK;
    }

    private float miningProgress(Minecraft mc) {
        // In both pinned 26.x mappings this is floor(destroyProgress * 10), or -1 at zero.
        int stage = mc.gameMode == null ? -1 : mc.gameMode.getDestroyStage();
        return stage < 0 ? 0f : Math.min(1f, stage / 10f);
    }

    private BlockState lookedBlock(Minecraft mc) {
        if (mc.hitResult instanceof BlockHitResult block && mc.level != null) {
            return mc.level.getBlockState(block.getBlockPos());
        }
        return null;
    }

    private String blockPosKey(Minecraft mc) {
        if (mc.hitResult instanceof BlockHitResult block && mc.level != null) {
            var p = block.getBlockPos();
            return mc.level.dimension().identifier() + ":" + p.getX() + "," + p.getY() + "," + p.getZ();
        }
        return null;
    }

    private String blockName(BlockState state) {
        return state == null ? null : state.getBlock().getDescriptionId();
    }

    private boolean bobberBite(Minecraft mc) {
        // Legacy heuristic retained as fallback: bobber sinking in fluid with no horizontal drift.
        var hook = mc.player == null ? null : mc.player.fishing;
        if (hook == null) {
            return false;
        }
        var v = hook.getDeltaMovement();
        return v.y < -0.075 && hook.isInWater() && Math.abs(v.x) < 0.01 && Math.abs(v.z) < 0.01;
    }

    private boolean isBlockItem(ItemStack stack) {
        return stack.getItem() instanceof net.minecraft.world.item.BlockItem;
    }

    private boolean isOre(BlockState state) {
        // Prefer a block tag check at kickoff (e.g. a #c:ores tag); name match is a safe fallback.
        String id = state.getBlock().getDescriptionId().toLowerCase(java.util.Locale.ROOT);
        return id.contains("ore");
    }

    private float normalizedHardness(BlockState state) {
        if (state == null) {
            return 0.4f;
        }
        try {
            float h = state.getBlock().defaultDestroyTime();
            return Math.max(0f, Math.min(1f, h / 5.0f)); // ~obsidian(50) saturates; stone(1.5)->0.3
        } catch (RuntimeException unavailable) {
            return 0.4f;
        }
    }

    private MaterialFeel classify(BlockState state) {
        if (state == null) {
            return MaterialFeel.UNKNOWN;
        }
        // A tag-driven classifier is preferred (brief §7.4); this name-based map is the fallback.
        String id = state.getBlock().getDescriptionId().toLowerCase(java.util.Locale.ROOT);
        if (id.contains("ore") || id.contains("stone") || id.contains("deepslate")) {
            return MaterialFeel.STONE_ORE;
        }
        if (id.contains("log") || id.contains("wood") || id.contains("plank")) {
            return MaterialFeel.WOOD;
        }
        if (id.contains("sand") || id.contains("gravel")) {
            return MaterialFeel.SAND_GRAVEL;
        }
        if (id.contains("dirt") || id.contains("clay") || id.contains("mud")) {
            return MaterialFeel.SOIL_CLAY;
        }
        if (id.contains("iron") || id.contains("copper") || id.contains("metal") || id.contains("gold")) {
            return MaterialFeel.METAL;
        }
        if (id.contains("glass") || id.contains("amethyst") || id.contains("crystal")) {
            return MaterialFeel.GLASS_CRYSTAL;
        }
        if (id.contains("wool") || id.contains("wave")) {
            return MaterialFeel.WOOL_SOFT;
        }
        if (id.contains("crop") || id.contains("wheat") || id.contains("leaves") || id.contains("plant")) {
            return MaterialFeel.PLANTS_CROPS;
        }
        return MaterialFeel.UNKNOWN;
    }
}
