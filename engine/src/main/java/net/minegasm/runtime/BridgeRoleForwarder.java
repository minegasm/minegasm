package net.minegasm.runtime;

import net.minegasm.core.HapticLayer;
import net.minegasm.core.HapticRole;
import net.minegasm.core.HapticScene;

import java.util.EnumMap;
import java.util.List;
import java.util.function.Predicate;

/**
 * Renders the governed scene set into one authoritative level per role and forwards that whole snapshot
 * to the bridge whenever it changes (second follow-up review P1-3). This replaces per-scene effect frames
 * that an adapter combined and could never retract: because every frame is the full, current per-role
 * state, a scene ending or being suppressed drops its role's level in the next snapshot, so removal is as
 * meaningful as addition and a lower-priority scene can't linger at the adapter after a higher-priority
 * exclusive takes over.
 *
 * <p>Priority and exclusivity are already resolved centrally in {@link SceneGovernor#govern}, so both
 * backends see one resolved set; this stage only takes the peak surviving level per role, the bridge's
 * device-neutral rendering (the Buttplug mixer renders the same resolved set per physical feature).
 *
 * <p>Confined to the worker thread, like {@link SceneGovernor#govern}. The sink reports delivery, so a
 * snapshot dropped while disconnected is retried; {@link #reset} forces the next snapshot to send afresh
 * (called on a stop or a fresh connection).
 */
public final class BridgeRoleForwarder {

    /** Level change below this is treated as steady (absorbs per-cycle fatigue drift). */
    private static final float LEVEL_EPSILON = 0.02f;

    /** Re-send the steady snapshot at least this often, so the adapter's output TTL keeps refreshing. */
    private static final long REARM_INTERVAL_NS = 2_000_000_000L;

    private final Predicate<EnumMap<HapticRole, Float>> sink;
    private EnumMap<HapticRole, Float> lastSent; // null until the first snapshot is delivered
    private long rearmAtNs;

    public BridgeRoleForwarder(Predicate<EnumMap<HapticRole, Float>> sink) {
        this.sink = sink;
    }

    /**
     * Compute the authoritative per-role levels and send the snapshot if it changed, if none has been sent
     * yet, or if the steady snapshot is due for a re-send (which refreshes the adapter's TTL so a long
     * steady effect never lapses at the adapter). Recorded only on an accepted send, so a snapshot dropped
     * while the link is down is retried next cycle.
     */
    public void forward(List<HapticScene> governed, long nowNs) {
        EnumMap<HapticRole, Float> resolved = resolve(governed);
        boolean due = lastSent == null || changed(resolved, lastSent) || nowNs >= rearmAtNs;
        if (due && sink.test(new EnumMap<>(resolved))) {
            lastSent = resolved;
            rearmAtNs = nowNs + REARM_INTERVAL_NS;
        }
    }

    /** Forget the last snapshot so the next one is sent afresh (stop, or a fresh connection). */
    public void reset() {
        lastSent = null;
        rearmAtNs = 0L;
    }

    /** The authoritative level per role: the peak level among that role's surviving governed layers. */
    private static EnumMap<HapticRole, Float> resolve(List<HapticScene> governed) {
        EnumMap<HapticRole, Float> out = new EnumMap<>(HapticRole.class);
        for (HapticRole role : HapticRole.values()) {
            out.put(role, 0f);
        }
        for (HapticScene scene : governed) {
            for (HapticLayer layer : scene.layers()) {
                float level = layer.primitive().level();
                if (level > out.get(layer.role())) {
                    out.put(layer.role(), level);
                }
            }
        }
        return out;
    }

    private static boolean changed(EnumMap<HapticRole, Float> a, EnumMap<HapticRole, Float> b) {
        for (HapticRole role : HapticRole.values()) {
            float x = a.get(role);
            float y = b.get(role);
            // A crossing of the zero boundary always sends (off must reach the adapter exactly).
            if ((x <= 0f) != (y <= 0f) || Math.abs(x - y) > LEVEL_EPSILON) {
                return true;
            }
        }
        return false;
    }
}
