package net.minegasm.runtime;

import net.minegasm.core.CouplingMode;
import net.minegasm.core.HapticLayer;
import net.minegasm.core.HapticRole;
import net.minegasm.core.HapticScene;
import net.minegasm.render.PrimitiveEvaluator;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The central scene-governance stage that ADR-018 lifts out of the per-cycle renderer. It owns the
 * active {@link SceneStore} and is the single point where scenes are held, coalesced, and expired
 * before they fan out to any backend. Submitting happens on the client thread; the worker pulls a
 * governed snapshot on its own cadence. One monitor guards the store so those two threads never see it
 * half-updated.
 *
 * <p>This is where the aggregate stages accrue over the roadmap. {@link #govern} decays and accounts
 * fatigue centrally and bakes the per-role attenuation into the scene's primitives before returning it,
 * so every backend, the worker and any semantic backend alike, consumes an already-governed scene
 * rather than each applying its own attenuation. The Phase-6 body budget slots in the same way. The
 * fan-out currency stays the device-neutral {@link HapticScene}.
 *
 * <p><b>Stop ordering.</b> This lock keeps the store consistent across threads, but it is not what makes
 * a stop safe on its own. The worker takes a snapshot and then renders and dispatches it while holding
 * its own monitor, and {@code requestStop} clears this store under that same worker monitor, so a stop
 * and a cycle never interleave: after a stop returns, the store is empty and the next cycle renders
 * nothing. See {@link HapticWorker}.
 */
public final class SceneGovernor {

    /** Maximum concurrent discrete scenes before the overflow policy drops one (brief §6.2). */
    public static final int DEFAULT_CAPACITY = 64;

    private final SceneStore store = new SceneStore();
    private final FatigueGovernor fatigue = new FatigueGovernor();
    private final int capacity;
    private long droppedCount;
    private long lastGovernNs;

    public SceneGovernor() {
        this(DEFAULT_CAPACITY);
    }

    public SceneGovernor(int capacity) {
        this.capacity = Math.max(1, capacity);
    }

    /**
     * Add a scene to the held set. Continuous scenes coalesce latest-wins by key and are never dropped;
     * discrete scenes are bounded, dropping an expired or lowest-priority scene on overflow so memory
     * cannot grow without bound.
     */
    public synchronized void submit(HapticScene scene, long nowNs) {
        if (scene == null) {
            return;
        }
        if (scene.isContinuous()) {
            store.add(scene);
            return;
        }
        if (store.discreteCount() >= capacity && store.evictOneDiscrete(nowNs, scene.priority())) {
            droppedCount++; // a held scene was dropped to make room
        }
        if (store.discreteCount() >= capacity) {
            droppedCount++; // no room and nothing weaker to evict: reject the incoming scene
            return;
        }
        store.add(scene);
    }

    /**
     * Expire stale scenes and return the current held snapshot, untouched by fatigue. For tests and
     * diagnostics; the render/fan-out path uses {@link #govern}.
     */
    public synchronized List<HapticScene> snapshot(long nowNs) {
        store.update(nowNs);
        return store.snapshot();
    }

    /**
     * The central governance step: expire stale scenes, decay and account fatigue, and return the held
     * scenes with the per-role fatigue attenuation baked into their primitives.
     *
     * @param fatigueOn   whether fatigue attenuation is applied (the user's protection toggle); load is
     *                    still accounted when off, so enabling protection sees the accumulated history
     * @param accountLoad whether to add this cycle's output to the fatigue budgets; the caller passes
     *                    false when output is suppressed (disabled, panicked) so idle time never fatigues
     */
    public synchronized List<HapticScene> govern(long nowNs, boolean fatigueOn, boolean accountLoad) {
        store.update(nowNs);
        fatigue.update(nowNs);
        long dt = lastGovernNs == 0L ? 0L : nowNs - lastGovernNs;
        lastGovernNs = nowNs;

        List<HapticScene> held = store.snapshot();
        EnumMap<HapticRole, Float> achievedByRole = new EnumMap<>(HapticRole.class);
        List<HapticScene> governed = new ArrayList<>(held.size());
        for (HapticScene scene : held) {
            List<HapticLayer> layers = scene.layers();
            List<HapticLayer> rebuilt = null;
            for (int i = 0; i < layers.size(); i++) {
                HapticLayer layer = layers.get(i);
                float factor = fatigueOn ? fatigue.factor(layer.role()) : 1f;
                long layerStart = scene.createdAtNs() + layer.startOffsetNs();
                long layerEnd = layerStart + layer.expiresAfterNs();
                if (nowNs >= layerStart && nowNs < layerEnd) {
                    float achieved =
                            PrimitiveEvaluator.levelAt(layer.primitive(), nowNs - layerStart) * factor;
                    if (achieved > 0f) {
                        achievedByRole.merge(layer.role(), achieved, Math::max);
                    }
                }
                if (factor < 1f) {
                    if (rebuilt == null) {
                        rebuilt = new ArrayList<>(layers);
                    }
                    rebuilt.set(i, layer.withPrimitive(layer.primitive().scaled(factor)));
                }
            }
            governed.add(rebuilt == null ? scene : scene.withLayers(rebuilt));
        }
        if (accountLoad && dt > 0L) {
            for (Map.Entry<HapticRole, Float> e : achievedByRole.entrySet()) {
                fatigue.record(e.getKey(), e.getValue(), dt);
            }
        }
        return resolveExclusivity(governed);
    }

    /**
     * Resolve priority and exclusivity once, centrally, so every backend consumes one already-resolved set
     * (second follow-up review P1-3). Within a role, the highest-priority EXCLUSIVE layer suppresses every
     * strictly lower-priority layer of that role; a scene left with no layers drops out. Doing this here,
     * rather than separately in each backend, is what keeps the bridge and the Buttplug mixer from
     * resolving the same governed set differently and driving hardware inconsistently.
     *
     * <p>Role is the device-neutral granularity the governor owns. Cross-role collisions on one physical
     * feature stay the Buttplug mixer's job, since only it has a device model; the per-role bridge has no
     * such collision. Logical body regions join the key in a later phase (device-config work), so for now
     * an exclusive layer owns its whole role rather than a region of it.
     */
    private static List<HapticScene> resolveExclusivity(List<HapticScene> governed) {
        EnumMap<HapticRole, Integer> exclusiveFloor = new EnumMap<>(HapticRole.class);
        for (HapticScene scene : governed) {
            for (HapticLayer layer : scene.layers()) {
                if (layer.coupling() == CouplingMode.EXCLUSIVE) {
                    exclusiveFloor.merge(layer.role(), layer.priority(), Math::max);
                }
            }
        }
        if (exclusiveFloor.isEmpty()) {
            return governed;
        }
        List<HapticScene> out = new ArrayList<>(governed.size());
        for (HapticScene scene : governed) {
            List<HapticLayer> kept = new ArrayList<>(scene.layers().size());
            for (HapticLayer layer : scene.layers()) {
                Integer floor = exclusiveFloor.get(layer.role());
                if (floor == null || layer.priority() >= floor) {
                    kept.add(layer);
                }
            }
            if (kept.size() == scene.layers().size()) {
                out.add(scene);
            } else if (!kept.isEmpty()) {
                out.add(scene.withLayers(kept));
            } // else: every layer suppressed, so the scene contributes nothing and is dropped
        }
        return out;
    }

    /** Drop every held scene, keeping fatigue history. Used by the scenes-only test paths. */
    public synchronized void clear() {
        store.clear();
    }

    /** Drop every held scene and forget fatigue load and timing. Used by stop, world exit, and panic. */
    public synchronized void reset() {
        store.clear();
        fatigue.reset();
        lastGovernNs = 0L;
    }

    /** Shift every held scene and the fatigue clock forward after a real-time pause. */
    public synchronized void shiftTime(long deltaNs) {
        store.shiftTime(deltaNs);
        fatigue.shiftTime(deltaNs);
        if (lastGovernNs > 0L && deltaNs > 0L) {
            lastGovernNs += deltaNs;
        }
    }

    public synchronized int activeSceneCount() {
        return store.activeSceneCount();
    }

    public synchronized boolean isEmpty() {
        return store.isEmpty();
    }

    public synchronized long droppedCount() {
        return droppedCount;
    }
}
