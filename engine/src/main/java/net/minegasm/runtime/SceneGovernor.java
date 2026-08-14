package net.minegasm.runtime;

import net.minegasm.core.CouplingMode;
import net.minegasm.core.BodyRegion;
import net.minegasm.core.HapticLayer;
import net.minegasm.core.HapticRole;
import net.minegasm.core.HapticScene;
import net.minegasm.core.LogicalDestination;
import net.minegasm.core.OutputClass;
import net.minegasm.render.PrimitiveEvaluator;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashMap;

/**
 * The central scene-governance stage that ADR-018 lifts out of the per-cycle renderer. It owns the
 * active {@link SceneStore} and is the single point where scenes are held, coalesced, and expired
 * before they fan out to any backend. Submitting happens on the client thread; the worker pulls a
 * governed snapshot on its own cadence. One monitor guards the store so those two threads never see it
 * half-updated.
 *
 * <p>This is where the aggregate stages accrue over the roadmap. {@link #resolve} decays and accounts
 * fatigue centrally and bakes per-role, per-region attenuation into the scene's primitives. Each cycle
 * produces one {@link GovernedOutput}: active scenes remain available for physical route refinement,
 * while semantic backends consume the same time-sampled destination snapshot.
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
    private boolean hasGoverned;
    private long snapshotGeneration;

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
     * Compatibility scene view of the central governance result. New backends consume {@link #resolve}
     * so they also receive the authoritative destination snapshot.
     *
     * @param fatigueOn   whether fatigue attenuation is applied (the user's protection toggle); load is
     *                    still accounted when off, so enabling protection sees the accumulated history
     * @param accountLoad whether to add this cycle's output to the fatigue budgets; the caller passes
     *                    false when output is suppressed (disabled, panicked) so idle time never fatigues
     */
    public synchronized List<HapticScene> govern(long nowNs, boolean fatigueOn, boolean accountLoad) {
        return resolve(nowNs, fatigueOn, accountLoad).scenes();
    }

    /**
     * Resolve one immutable, time-aware output result. Layers outside their window or currently at zero
     * are absent for this cycle. Multi-family routes are split before competition, preventing an
     * exclusive motion layer from suppressing independent strength output. Fatigue is applied and
     * recorded only after competition, by role and region.
     */
    public synchronized GovernedOutput resolve(long nowNs, boolean fatigueOn, boolean accountLoad) {
        store.update(nowNs);
        fatigue.update(nowNs);
        long dt = hasGoverned ? Math.max(0L, nowNs - lastGovernNs) : 0L;
        lastGovernNs = nowNs;
        hasGoverned = true;

        List<HapticScene> held = activeAndClassified(store.snapshot(), nowNs);
        List<HapticScene> resolved = resolveExclusivity(held);
        List<HapticScene> governed = new ArrayList<>(resolved.size());
        Map<LogicalDestination, Float> levels = new LinkedHashMap<>();
        EnumMap<HapticRole, EnumMap<BodyRegion, Float>> achieved = new EnumMap<>(HapticRole.class);
        for (HapticScene scene : resolved) {
            List<HapticLayer> layers = scene.layers();
            List<HapticLayer> rebuilt = null;
            for (int i = 0; i < layers.size(); i++) {
                HapticLayer layer = layers.get(i);
                float factor = fatigueOn ? fatigue.factor(layer.role(), layer.bodyRegion()) : 1f;
                long layerStart = scene.createdAtNs() + layer.startOffsetNs();
                float current = PrimitiveEvaluator.levelAt(layer.primitive(), nowNs - layerStart) * factor;
                if (current > 0f) {
                    for (OutputClass outputClass : layer.route().outputClasses()) {
                        LogicalDestination destination = new LogicalDestination(layer.role(),
                                layer.bodyRegion(), outputClass);
                        mergeMax(levels, destination, current);
                    }
                    EnumMap<BodyRegion, Float> byRegion = achieved.get(layer.role());
                    if (byRegion == null) {
                        byRegion = new EnumMap<>(BodyRegion.class);
                        achieved.put(layer.role(), byRegion);
                    }
                    mergeMax(byRegion, layer.bodyRegion(), current);
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
            for (Map.Entry<HapticRole, EnumMap<BodyRegion, Float>> role : achieved.entrySet()) {
                for (Map.Entry<BodyRegion, Float> region : role.getValue().entrySet()) {
                    fatigue.record(role.getKey(), region.getKey(), region.getValue(), dt);
                }
            }
        }
        ResolvedDestinationSnapshot destinations = new ResolvedDestinationSnapshot(
                ++snapshotGeneration, nowNs, levels);
        return new GovernedOutput(governed, destinations);
    }

    /** Keep only currently active, non-zero layers and split routes into one layer per output family. */
    private static List<HapticScene> activeAndClassified(List<HapticScene> held, long nowNs) {
        List<HapticScene> out = new ArrayList<>(held.size());
        for (HapticScene scene : held) {
            List<HapticLayer> active = new ArrayList<>();
            for (HapticLayer layer : scene.layers()) {
                long start = scene.createdAtNs() + layer.startOffsetNs();
                long end = saturatingAdd(start, layer.expiresAfterNs());
                if (nowNs < start || nowNs >= end
                        || PrimitiveEvaluator.levelAt(layer.primitive(), nowNs - start) <= 0f) {
                    continue;
                }
                Set<OutputClass> classes = layer.route().outputClasses();
                for (OutputClass outputClass : classes) {
                    active.add(classes.size() == 1 ? layer
                            : layer.withRoute(layer.route().restrictedTo(outputClass)));
                }
            }
            if (!active.isEmpty()) {
                out.add(active.size() == scene.layers().size() && active.equals(scene.layers())
                        ? scene : scene.withLayers(active));
            }
        }
        return out;
    }

    private static long saturatingAdd(long a, long b) {
        if (b > 0L && a > Long.MAX_VALUE - b) {
            return Long.MAX_VALUE;
        }
        if (b < 0L && a < Long.MIN_VALUE - b) {
            return Long.MIN_VALUE;
        }
        return a + b;
    }

    private static <K> void mergeMax(Map<K, Float> levels, K key, float value) {
        Float previous = levels.get(key);
        if (previous == null || value > previous) {
            levels.put(key, value);
        }
    }

    /**
     * Resolve priority and exclusivity once, centrally. Competition requires the same role and output
     * class, plus a containing body region. A region-scoped exclusive cannot delete a whole-body layer
     * because it owns only part of that layer's reach. Physical renderers refine that partial overlap
     * against device placement, while the bridge preserves both destinations for its adapter.
     */
    private static List<HapticScene> resolveExclusivity(List<HapticScene> governed) {
        List<HapticLayer> exclusives = new ArrayList<>();
        for (HapticScene scene : governed) {
            for (HapticLayer layer : scene.layers()) {
                if (layer.coupling() == CouplingMode.EXCLUSIVE) {
                    exclusives.add(layer);
                }
            }
        }
        if (exclusives.isEmpty()) {
            return governed;
        }
        List<HapticScene> out = new ArrayList<>(governed.size());
        for (HapticScene scene : governed) {
            List<HapticLayer> kept = new ArrayList<>(scene.layers().size());
            for (HapticLayer layer : scene.layers()) {
                if (!suppressed(layer, exclusives)) {
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

    /** A layer is suppressed only by a higher-priority exclusive on the same complete destination. */
    private static boolean suppressed(HapticLayer layer, List<HapticLayer> exclusives) {
        for (HapticLayer e : exclusives) {
            if (e.role() == layer.role()
                    && e.priority() > layer.priority()
                    && e.bodyRegion().contains(layer.bodyRegion())
                    && intersects(e.route().outputClasses(), layer.route().outputClasses())) {
                return true;
            }
        }
        return false;
    }

    private static boolean intersects(Set<OutputClass> a, Set<OutputClass> b) {
        for (OutputClass outputClass : a) {
            if (b.contains(outputClass)) {
                return true;
            }
        }
        return false;
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
        hasGoverned = false;
        snapshotGeneration++;
    }

    /** Shift every held scene and the fatigue clock forward after a real-time pause. */
    public synchronized void shiftTime(long deltaNs) {
        store.shiftTime(deltaNs);
        fatigue.shiftTime(deltaNs);
        if (hasGoverned && deltaNs > 0L) {
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

    /** Current decayed load for diagnostics and deterministic governance tests. */
    double fatigueLoadFor(HapticRole role, BodyRegion region) {
        return fatigue.loadFor(role, region);
    }
}
