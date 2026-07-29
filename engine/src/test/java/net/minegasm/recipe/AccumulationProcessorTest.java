package net.minegasm.recipe;

import net.minegasm.config.AccumulationParams;
import net.minegasm.core.GameEventKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccumulationProcessorTest {

    private static long secs(double s) {
        return (long) (s * 1_000_000_000L);
    }

    @Test
    void contributionRaisesChargeThenDecaysOverRealTime() {
        var params = AccumulationParams.defaults(); // capacity 100, decay 1.5/s
        var acc = new AccumulationProcessor();
        acc.update(params, 0);
        acc.contribute(params, GameEventKind.HURT, false, 1f); // +10
        assertEquals(10.0, acc.charge(), 1e-6);
        acc.update(params, secs(2)); // decay 1.5*2 = 3
        assertEquals(7.0, acc.charge(), 1e-6);
    }

    @Test
    void chargeIsBoundedByCapacity() {
        var params = AccumulationParams.defaults();
        var acc = new AccumulationProcessor();
        acc.update(params, 0);
        for (int i = 0; i < 1000; i++) {
            acc.contribute(params, GameEventKind.HURT, false, 1f);
        }
        assertTrue(acc.charge() <= params.capacity());
        assertEquals(params.capacity(), acc.charge(), 1e-6);
    }

    @Test
    void oreBreakContributesMoreThanPlainBreak() {
        var params = AccumulationParams.defaults();
        var ore = new AccumulationProcessor();
        var plain = new AccumulationProcessor();
        ore.update(params, 0);
        plain.update(params, 0);
        ore.contribute(params, GameEventKind.BLOCK_BROKEN, true, 0.5f);
        plain.contribute(params, GameEventKind.BLOCK_BROKEN, false, 0.5f);
        assertTrue(ore.charge() > plain.charge());
    }

    @Test
    void levelFollowsCurveAndResetClears() {
        var params = AccumulationParams.defaults();
        var acc = new AccumulationProcessor();
        acc.update(params, 0);
        acc.contribute(params, GameEventKind.HURT, false, 1f);
        assertTrue(acc.level(params) > 0f);
        acc.reset();
        assertEquals(0.0, acc.charge(), 1e-6);
        assertEquals(0f, acc.level(params));
    }
}
