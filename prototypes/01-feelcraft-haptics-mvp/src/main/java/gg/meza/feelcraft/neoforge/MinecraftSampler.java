package gg.meza.feelcraft.neoforge;

import gg.meza.feelcraft.core.GameHapticEvent;
import gg.meza.feelcraft.core.MaterialFeel;
import gg.meza.feelcraft.runtime.HapticRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Client-side state sampler for the MVP.
 *
 * It deliberately avoids server-only events. Damage is detected by health deltas;
 * mining is detected from attack key + block hit result; block break is approximated
 * by watching a previously targeted block turn into air.
 */
public final class MinecraftSampler {
    private final HapticRuntime runtime;
    private float lastHealth = -1.0f;
    private boolean wasMining = false;
    private BlockPos lastMinedPos = null;
    private BlockState lastMinedState = null;

    public MinecraftSampler(HapticRuntime runtime) {
        this.runtime = runtime;
    }

    public void sampleEndTick(Minecraft minecraft) {
        if (minecraft == null || minecraft.player == null || minecraft.level == null) {
            runtime.stopAll("no_world_or_player");
            lastHealth = -1.0f;
            wasMining = false;
            lastMinedPos = null;
            lastMinedState = null;
            return;
        }

        Player player = minecraft.player;
        sampleHealth(player);
        sampleMining(minecraft, player);
        sampleLowHealth(player);
    }

    private void sampleHealth(Player player) {
        float health = player.getHealth() + player.getAbsorptionAmount();
        if (lastHealth >= 0 && health < lastHealth) {
            float damage = lastHealth - health;
            runtime.record(GameHapticEvent.playerDamaged(damage));
        }
        lastHealth = health;
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
                runtime.record(GameHapticEvent.blockBroken(MaterialFeel.fromBlockState(lastMinedState)));
            }
            lastMinedPos = null;
            lastMinedState = null;
        }

        wasMining = miningNow;
    }

    private void sampleLowHealth(Player player) {
        float max = Math.max(1.0f, player.getMaxHealth());
        float fraction = player.getHealth() / max;
        if (fraction <= 0.30f) {
            runtime.record(GameHapticEvent.lowHealth(fraction));
        }
    }
}
