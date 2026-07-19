package net.minegasm.render;

import net.minegasm.core.HapticPrimitive;
import net.minegasm.core.HapticPrimitive.Beat;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrimitiveEvaluatorTest {

    private static long ms(long m) {
        return m * 1_000_000L;
    }

    @Test
    void impulseRampsSustainsAndReleases() {
        var impulse = new HapticPrimitive.Impulse(0.8f, 100, 10, 20);
        assertEquals(0f, PrimitiveEvaluator.levelAt(impulse, 0), 1e-4);
        assertEquals(0.8f, PrimitiveEvaluator.levelAt(impulse, ms(10)), 1e-4); // end of attack
        assertEquals(0.8f, PrimitiveEvaluator.levelAt(impulse, ms(50)), 1e-4); // sustain
        assertTrue(PrimitiveEvaluator.levelAt(impulse, ms(90)) < 0.8f);        // in release
        assertEquals(0f, PrimitiveEvaluator.levelAt(impulse, ms(100)), 1e-4);  // expired
        assertEquals(0f, PrimitiveEvaluator.levelAt(impulse, ms(200)), 1e-4);
    }

    @Test
    void holdExpiresToZero() {
        var hold = new HapticPrimitive.Hold(0.5f, 200, 0, 0);
        assertEquals(0.5f, PrimitiveEvaluator.levelAt(hold, ms(100)), 1e-4);
        assertEquals(0f, PrimitiveEvaluator.levelAt(hold, ms(200)), 1e-4);
    }

    @Test
    void beatPatternActivatesInWindows() {
        var pattern = new HapticPrimitive.BeatPattern(List.of(
                new Beat(0, 0.4f, 50),
                new Beat(120, 0.6f, 50)));
        assertTrue(PrimitiveEvaluator.levelAt(pattern, ms(25)) > 0f); // inside first beat
        assertEquals(0f, PrimitiveEvaluator.levelAt(pattern, ms(80)), 1e-4); // gap
        assertTrue(PrimitiveEvaluator.levelAt(pattern, ms(140)) > 0f); // inside second beat
        assertEquals(0f, PrimitiveEvaluator.levelAt(pattern, ms(300)), 1e-4); // after
    }

    @Test
    void negativeElapsedIsZero() {
        var impulse = new HapticPrimitive.Impulse(1f, 50, 5, 5);
        assertEquals(0f, PrimitiveEvaluator.levelAt(impulse, -10));
    }
}
