package gg.meza.feelcraft.neoforge;

import gg.meza.feelcraft.core.GameHapticEvent;
import gg.meza.feelcraft.core.MaterialFeel;
import gg.meza.feelcraft.runtime.HapticRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Client-side state sampler for the MVP.
 *
 * It deliberately avoids server-only events. Damage is detected by health deltas;
 * mining is detected from attack key + block hit result; block break is approximated
 * by watching a previously targeted block turn into air. Minegasm-compatible attack,
 * XP, harvest, and vitality events are also detected here.
 */
public final class MinecraftSampler {
    private final HapticRuntime runtime;
    private float lastHealth = -1.0f;
    private int lastTotalXp = -1;
    private boolean wasMining = false;
    private boolean attackWasDown = false;
    private long lastVitalityPulseNs = 0L;
    private BlockPos lastMinedPos = null;
    private BlockState lastMinedState = null;

    public MinecraftSampler(HapticRuntime runtime) {
        this.runtime = runtime;
    }

    public void sampleEndTick(Minecraft minecraft) {
        if (minecraft == null || minecraft.player == null || minecraft.level == null) {
            runtime.stopAll("no_world_or_player");
            lastHealth = -1.0f;
            lastTotalXp = -1;
            wasMining = false;
            attackWasDown = false;
            lastMinedPos = null;
            lastMinedState = null;
            return;
        }

        Player player = minecraft.player;
        sampleHealth(player);
        sampleAttack(minecraft);
        sampleMining(minecraft, player);
        sampleXp(player);
        sampleLowHealth(player);
        sampleVitality(player);
    }

    private void sampleHealth(Player player) {
        float health = player.getHealth() + player.getAbsorptionAmount();
        if (lastHealth >= 0 && health < lastHealth) {
            float damage = lastHealth - health;
            runtime.record(GameHapticEvent.playerDamaged(damage));
        }
        lastHealth = health;
    }

    private void sampleAttack(Minecraft minecraft) {
        boolean attackDown = minecraft.options.keyAttack.isDown();
        if (attackDown && !attackWasDown) {
            boolean entityTargeted = minecraft.hitResult instanceof EntityHitResult;
            runtime.record(GameHapticEvent.playerAttack(entityTargeted));
        }
        attackWasDown = attackDown;
    }

    private void sampleMining(Minecraft minecraft, Player player) {
        boolean attackHeld = minecraft.options.keyAttack.isDown();
        HitResult hit = minecraft.hitResult;
        boolean miningNow = false;
        BlockPos pos = null;
        BlockState state = null;

        if (attackHeld && hit instanceof BlockHitResult blockHit && minecraft.level != null) {
            pos = blockHit.getBlockPos();
            state = minecraft.level.getBlockState(pos);
            miningNow = state != null && !state.isAir();
        }

        if (miningNow) {
            MaterialFeel material = MaterialFeel.fromBlockState(state);
            float hardness = state.getDestroySpeed(player.level(), pos);
            runtime.record(GameHapticEvent.miningTick(material, hardness));
            lastMinedPos = pos;
            lastMinedState = state;
        } else if (wasMining && lastMinedPos != null && minecraft.level != null) {
            BlockState now = minecraft.level.getBlockState(lastMinedPos);
            if (now != null && now.isAir()) {
                MaterialFeel material = MaterialFeel.fromBlockState(lastMinedState);
                runtime.record(GameHapticEvent.blockBroken(material));
                if (isHarvestLike(lastMinedState)) runtime.record(GameHapticEvent.harvest(material));
            }
            lastMinedPos = null;
            lastMinedState = null;
        }

        wasMining = miningNow;
    }

    private void sampleXp(Player player) {
        int totalXp = player.totalExperience;
        if (lastTotalXp >= 0 && totalXp > lastTotalXp) {
            runtime.record(GameHapticEvent.xpChanged(totalXp - lastTotalXp));
        }
        lastTotalXp = totalXp;
    }

    private void sampleLowHealth(Player player) {
        float max = Math.max(1.0f, player.getMaxHealth());
        float fraction = player.getHealth() / max;
        if (fraction <= 0.30f) {
            runtime.record(GameHapticEvent.lowHealth(fraction));
        }
    }

    private void sampleVitality(Player player) {
        long now = System.nanoTime();
        if (now - lastVitalityPulseNs < 3_000_000_000L) return;
        float healthFraction = player.getHealth() / Math.max(1.0f, player.getMaxHealth());
        float foodFraction = player.getFoodData().getFoodLevel() / 20.0f;
        boolean healthy = healthFraction >= 0.95f && foodFraction >= 0.95f;
        boolean dying = healthFraction <= 0.15f;
        if (healthy || dying) {
            runtime.record(GameHapticEvent.vitality(healthFraction, foodFraction));
            lastVitalityPulseNs = now;
        }
    }

    private static boolean isHarvestLike(BlockState state) {
        if (state == null) return false;
        String key = state.getBlock().builtInRegistryHolder().key().location().getPath();
        return key.contains("wheat")
                || key.contains("carrot")
                || key.contains("potato")
                || key.contains("beetroot")
                || key.contains("crop")
                || key.contains("melon")
                || key.contains("pumpkin")
                || key.contains("berry")
                || key.contains("cocoa")
                || key.contains("kelp")
                || key.contains("bamboo")
                || key.contains("cactus")
                || key.contains("sugar_cane")
                || key.contains("nether_wart");
    }
}
