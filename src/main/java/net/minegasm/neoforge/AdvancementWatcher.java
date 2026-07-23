package net.minegasm.neoforge;

import net.minegasm.core.GameEventKind;
import net.minegasm.core.RawGameEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

//? if >=1.21.1 {
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementNode;
//?} else {
/*import net.minecraft.advancements.Advancement;
*///?}
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import net.minecraft.client.multiplayer.ClientAdvancements;
//? if >=26.1.2 {
import net.minecraft.resources.Identifier;
//?} else {
/*import net.minecraft.resources.ResourceLocation;
*///?}

/**
 * Client-side detector for the {@link GameEventKind#ADVANCEMENT} event (brief §3.2 parity). Minecraft
 * exposes no pollable "advancement earned" state and no loader event that fires against a remote
 * vanilla server, so this attaches to the vanilla {@link ClientAdvancements.Listener} slot, the one
 * public, mapping-portable hook that works identically on singleplayer and unmodified multiplayer
 * servers (docs/adr/ADR-014). The single listener slot is shared with the vanilla
 * {@link AdvancementsScreen}: this yields it while that screen is open and re-asserts it otherwise.
 *
 * <p>Vanilla-only types, so it lives in shared {@code src} with no loader guard, like
 * {@link MinecraftSampler}; the only version-specific access (the current screen) goes through
 * {@link McCompat}. Emissions are queued off the packet/listener callback and drained on the client
 * tick by {@link #poll}, so advancement events enter the pipeline on the same cadence as every other
 * observation.
 */
final class AdvancementWatcher implements ClientAdvancements.Listener {

    /**
     * Ticks to ignore after (re)attaching before emitting. Joining a world (or a datapack reload)
     * replays every already-earned advancement through the listener; this window records those as
     * pre-existing without firing, so only advancements earned live produce a pulse. Biased to
     * under-fire: an advancement earned within the first few seconds of joining is treated as replay.
     */
    private static final int SETTLE_TICKS = 60;

    //? if >=26.1.2 {
    private final Set<Identifier> earned = new HashSet<>();
    //?} else {
    /*private final Set<ResourceLocation> earned = new HashSet<>();
    *///?}
    private final Queue<Pending> pending = new ConcurrentLinkedQueue<>();

    private ClientAdvancements bound;
    private boolean installed;
    private long attachedTick = Long.MIN_VALUE;
    private boolean primed;
    private boolean needsReprime;

    private record Pending(int dedupe, String frame) {
    }

    /**
     * Attach to (or re-assert ownership of) the current connection's advancement listener and drain
     * any advancements earned since the last tick to {@code sink}. Safe to call every client tick,
     * including with no world loaded.
     */
    void poll(Minecraft mc, long gameTick, long nowNs, MinecraftSampler.EventSink sink) {
        var connection = mc.getConnection();
        ClientAdvancements advancements = connection == null ? null : connection.getAdvancements();
        if (advancements == null) {
            reset();
            return;
        }
        if (advancements != bound) {
            // New connection: its listener slot is empty and its progress will replay on attach.
            reset();
            bound = advancements;
        }

        if (McCompat.currentScreen(mc) instanceof AdvancementsScreen) {
            // The vanilla advancements screen owns the single listener slot while it is open; step
            // aside so live progress still renders there, and re-attach once it closes.
            installed = false;
        } else if (!installed) {
            advancements.setListener(this); // replays current progress synchronously
            installed = true;
            // Settle only from the first attach on this connection. Re-attaching after the screen
            // closes replays already-earned advancements, but the persistent seen-set dedups those,
            // so there is no need to re-open a dead window each time.
            if (attachedTick == Long.MIN_VALUE) {
                attachedTick = gameTick;
            }
        }

        if (needsReprime) {
            attachedTick = gameTick;
            primed = false;
            needsReprime = false;
        }
        if (!primed && attachedTick != Long.MIN_VALUE && gameTick - attachedTick >= SETTLE_TICKS) {
            primed = true;
        }

        for (Pending p; (p = pending.poll()) != null; ) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("frame", p.frame());
            payload.put("dedupe", p.dedupe());
            sink.record(new RawGameEvent(GameEventKind.ADVANCEMENT, gameTick, nowNs, payload));
        }
    }

    void reset() {
        earned.clear();
        pending.clear();
        bound = null;
        installed = false;
        primed = false;
        needsReprime = false;
        attachedTick = Long.MIN_VALUE;
    }

    // The vanilla advancement types diverged in 1.20.2: 1.21.1 and the 26.x lines carry the earned
    // advancement as an AdvancementNode wrapping an AdvancementHolder (id + value), whereas 1.20.1
    // passes the Advancement directly, with getId()/getDisplay() on it and a nullable DisplayInfo
    // (getFrame().getName(), which returns the same serialized "task"/"goal"/"challenge" as the newer
    // getType().getSerializedName()) rather than an Optional AdvancementType. The listener method shapes
    // below therefore split by era; onAdvancementsCleared() is identical on both and stays unguarded.
    // The >=1.21.1 guard is a proxy for "has the 1.20.2 advancement rework": it holds only because no
    // 1.20.2-1.20.6 variant is registered; adding one would need this boundary revisited.
    //? if >=1.21.1 {
    @Override
    public void onUpdateAdvancementProgress(AdvancementNode node, AdvancementProgress progress) {
        if (!progress.isDone()) {
            return;
        }
        AdvancementHolder holder = node.holder();
        var display = holder.value().display();
        if (display.isEmpty()) {
            // Recipe-unlock and other display-less advancements are internal; they never pulse.
            return;
        }
        if (!earned.add(holder.id())) {
            return; // already counted this session
        }
        if (!primed) {
            return; // join/reload replay: recorded above, but suppressed
        }
        pending.add(new Pending(holder.id().hashCode(), display.get().getType().getSerializedName()));
    }
    //?} else {
    /*@Override
    public void onUpdateAdvancementProgress(Advancement advancement, AdvancementProgress progress) {
        if (!progress.isDone()) {
            return;
        }
        var display = advancement.getDisplay();
        if (display == null) {
            // Recipe-unlock and other display-less advancements are internal; they never pulse.
            return;
        }
        if (!earned.add(advancement.getId())) {
            return; // already counted this session
        }
        if (!primed) {
            return; // join/reload replay: recorded above, but suppressed
        }
        pending.add(new Pending(advancement.getId().hashCode(), display.getFrame().getName()));
    }
    *///?}

    @Override
    public void onAdvancementsCleared() {
        // Server reset advancements (e.g. datapack reload): forget history and re-settle the replay.
        earned.clear();
        needsReprime = true;
    }

    //? if >=1.21.1 {
    @Override
    public void onSelectedTabChanged(AdvancementHolder holder) {
    }

    @Override
    public void onAddAdvancementRoot(AdvancementNode node) {
    }

    @Override
    public void onRemoveAdvancementRoot(AdvancementNode node) {
    }

    @Override
    public void onAddAdvancementTask(AdvancementNode node) {
    }

    @Override
    public void onRemoveAdvancementTask(AdvancementNode node) {
    }
    //?} else {
    /*@Override
    public void onSelectedTabChanged(Advancement advancement) {
    }

    @Override
    public void onAddAdvancementRoot(Advancement advancement) {
    }

    @Override
    public void onRemoveAdvancementRoot(Advancement advancement) {
    }

    @Override
    public void onAddAdvancementTask(Advancement advancement) {
    }

    @Override
    public void onRemoveAdvancementTask(Advancement advancement) {
    }
    *///?}
}
