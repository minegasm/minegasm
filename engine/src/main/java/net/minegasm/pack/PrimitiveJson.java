package net.minegasm.pack;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.minegasm.core.HapticPrimitive;

/**
 * Shared device-neutral JSON encoding of a {@link HapticPrimitive}, a tagged union keyed on a
 * {@code type} field. Used by both {@link ScenePackCodec} (shareable pack files) and the haptic bridge
 * so the two never drift. The trailing throw keeps the encoder exhaustive, so a new core primitive
 * fails loudly here rather than silently serializing to nothing (mirrors {@code PrimitiveEvaluator}).
 */
public final class PrimitiveJson {

    private PrimitiveJson() {}

    public static JsonObject toJson(HapticPrimitive p) {
        JsonObject o = new JsonObject();
        // instanceof chain, not a switch: the engine also compiles as Java 8 for Classic.
        if (p instanceof HapticPrimitive.Impulse) {
            HapticPrimitive.Impulse i = (HapticPrimitive.Impulse) p;
            o.addProperty("type", "impulse");
            o.addProperty("level", i.level());
            o.addProperty("durationMs", i.durationMs());
            o.addProperty("attackMs", i.attackMs());
            o.addProperty("releaseMs", i.releaseMs());
        } else if (p instanceof HapticPrimitive.Texture) {
            HapticPrimitive.Texture t = (HapticPrimitive.Texture) p;
            o.addProperty("type", "texture");
            o.addProperty("level", t.level());
            o.addProperty("durationMs", t.durationMs());
            o.addProperty("grain", t.grain());
            o.addProperty("density", t.density());
            o.addProperty("irregularity", t.irregularity());
        } else if (p instanceof HapticPrimitive.Rumble) {
            HapticPrimitive.Rumble r = (HapticPrimitive.Rumble) p;
            o.addProperty("type", "rumble");
            o.addProperty("level", r.level());
            o.addProperty("durationMs", r.durationMs());
            o.addProperty("roughness", r.roughness());
            o.addProperty("decay", r.decay());
        } else if (p instanceof HapticPrimitive.Sweep) {
            HapticPrimitive.Sweep s = (HapticPrimitive.Sweep) p;
            o.addProperty("type", "sweep");
            o.addProperty("from", s.from());
            o.addProperty("to", s.to());
            o.addProperty("durationMs", s.durationMs());
            o.addProperty("easing", s.easing().name());
        } else if (p instanceof HapticPrimitive.BeatPattern) {
            HapticPrimitive.BeatPattern bp = (HapticPrimitive.BeatPattern) p;
            o.addProperty("type", "beat");
            JsonArray beats = new JsonArray();
            for (HapticPrimitive.Beat b : bp.beats()) {
                JsonObject bo = new JsonObject();
                bo.addProperty("atMs", b.atMs());
                bo.addProperty("level", b.level());
                bo.addProperty("durationMs", b.durationMs());
                beats.add(bo);
            }
            o.add("beats", beats);
        } else if (p instanceof HapticPrimitive.Hold) {
            HapticPrimitive.Hold h = (HapticPrimitive.Hold) p;
            o.addProperty("type", "hold");
            o.addProperty("level", h.level());
            o.addProperty("durationMs", h.durationMs());
            o.addProperty("fadeInMs", h.fadeInMs());
            o.addProperty("fadeOutMs", h.fadeOutMs());
        } else if (p instanceof HapticPrimitive.Oscillation) {
            HapticPrimitive.Oscillation osc = (HapticPrimitive.Oscillation) p;
            o.addProperty("type", "oscillation");
            o.addProperty("level", osc.level());
            o.addProperty("periodMs", osc.periodMs());
            o.addProperty("durationMs", osc.durationMs());
        } else {
            throw new IllegalStateException("Unknown HapticPrimitive: " + p);
        }
        return o;
    }
}
