package net.minegasm.util;

import net.minegasm.device.IntRange;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HapticMathTest {

    @Test
    void clamp01Bounds() {
        assertEquals(0f, HapticMath.clamp01(-0.5f));
        assertEquals(1f, HapticMath.clamp01(2f));
        assertEquals(0.3f, HapticMath.clamp01(0.3f));
    }

    @Test
    void smoothstepEndpointsAndMidpoint() {
        assertEquals(0f, HapticMath.smoothstep(0f));
        assertEquals(1f, HapticMath.smoothstep(1f));
        assertEquals(0.5f, HapticMath.smoothstep(0.5f), 1e-6);
    }

    @Test
    void scaleNormalizedMatchesButtplugExample() {
        // Brief example: level 0.60 in range [0,20] => Value 12.
        assertEquals(12, HapticMath.scaleNormalized(0.60f, new IntRange(0, 20)));
        assertEquals(0, HapticMath.scaleNormalized(0f, new IntRange(0, 20)));
        assertEquals(20, HapticMath.scaleNormalized(1f, new IntRange(0, 20)));
        assertEquals(20, HapticMath.scaleNormalized(5f, new IntRange(0, 20))); // clamped
    }

    @Test
    void scaleSignedMapsCentreAndExtremes() {
        IntRange signed = new IntRange(-100, 100);
        assertEquals(0, HapticMath.scaleSigned(0f, signed));
        assertEquals(100, HapticMath.scaleSigned(1f, signed));
        assertEquals(-100, HapticMath.scaleSigned(-1f, signed));
    }

    @Test
    void variationIsDeterministic() {
        long a = HapticMath.variationSeed("hurt", 42L, 7);
        long b = HapticMath.variationSeed("hurt", 42L, 7);
        long c = HapticMath.variationSeed("hurt", 43L, 7);
        assertEquals(a, b);
        assertTrue(a != c);
    }

    @Test
    void zeroVariationLeavesLevelUnchanged() {
        assertEquals(0.5f, HapticMath.varyLevel(0.5f, 12345L, 0f), 1e-6);
        assertEquals(0f, HapticMath.seededJitter(1L, 1, 0f));
    }

    @Test
    void jitterStaysWithinMagnitude() {
        for (int i = 0; i < 1000; i++) {
            float j = HapticMath.seededJitter(i, 3, 0.1f);
            assertTrue(Math.abs(j) <= 0.1f + 1e-6, "jitter out of bounds: " + j);
        }
    }
}
