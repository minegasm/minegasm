package net.minegasm.runtime;

import net.minegasm.core.BodyRegion;
import net.minegasm.core.CouplingMode;
import net.minegasm.core.GameEventKind;
import net.minegasm.core.HapticLayer;
import net.minegasm.core.HapticPrimitive;
import net.minegasm.core.HapticRole;
import net.minegasm.core.HapticRoute;
import net.minegasm.core.HapticScene;
import net.minegasm.core.LogicalDestination;
import net.minegasm.core.OutputClass;
import net.minegasm.render.PrimitiveEvaluator;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResolvedDestinationSnapshotTest {

    private static final long MS = 1_000_000L;
    private static final LogicalDestination DESTINATION = new LogicalDestination(
            HapticRole.IMPACT, BodyRegion.GENITAL, OutputClass.STRENGTH);

    @Test
    void snapshotSamplesEveryPrimitiveThroughTheSharedEvaluator() {
        assertSample(new HapticPrimitive.Impulse(0.8f, 100, 10, 20), 5);
        assertSample(new HapticPrimitive.Texture(0.7f, 300, 0.3f, 0.4f, 0.2f), 77);
        assertSample(new HapticPrimitive.Rumble(0.8f, 500, 0.6f, true), 123);
        assertSample(new HapticPrimitive.Sweep(0.2f, 0.9f, 400,
                HapticPrimitive.Easing.EASE_IN_OUT), 200);
        assertSample(new HapticPrimitive.BeatPattern(Arrays.asList(
                new HapticPrimitive.Beat(0, 0.5f, 50),
                new HapticPrimitive.Beat(120, 0.8f, 50))), 140);
        assertSample(new HapticPrimitive.Hold(0.6f, 500, 20, 60), 100);
        assertSample(new HapticPrimitive.Oscillation(0.75f, 700, 3_000), 900);
    }

    @Test
    void aBeatGapAndPrimitiveExpiryAreAuthoritativeZero() {
        HapticPrimitive.BeatPattern beat = new HapticPrimitive.BeatPattern(Arrays.asList(
                new HapticPrimitive.Beat(0, 0.5f, 50),
                new HapticPrimitive.Beat(120, 0.8f, 50)));
        assertZero(beat, 80);
        assertZero(new HapticPrimitive.Sweep(0.2f, 0.9f, 100,
                HapticPrimitive.Easing.LINEAR), 100);
    }

    private static void assertSample(HapticPrimitive primitive, long elapsedMs) {
        ResolvedDestinationSnapshot snapshot = sample(primitive, elapsedMs);
        assertEquals(PrimitiveEvaluator.levelAt(primitive, elapsedMs * MS),
                snapshot.level(DESTINATION), 1e-6f, primitive.getClass().getSimpleName());
    }

    private static void assertZero(HapticPrimitive primitive, long elapsedMs) {
        ResolvedDestinationSnapshot snapshot = sample(primitive, elapsedMs);
        assertTrue(snapshot.isAllZero(), primitive.getClass().getSimpleName());
    }

    private static ResolvedDestinationSnapshot sample(HapticPrimitive primitive, long elapsedMs) {
        long created = 1_000 * MS;
        HapticLayer layer = new HapticLayer("sample", HapticRole.IMPACT, primitive,
                HapticRoute.buzzAll(), CouplingMode.MAX, 10, 0, 10_000 * MS, null,
                BodyRegion.GENITAL);
        HapticScene scene = new HapticScene("sample", GameEventKind.AMBIENT, 10,
                Collections.singletonList(layer), created, created + 10_000 * MS, null);
        SceneGovernor governor = new SceneGovernor();
        governor.submit(scene, created);
        return governor.resolve(created + elapsedMs * MS, false, false).destinations();
    }
}
