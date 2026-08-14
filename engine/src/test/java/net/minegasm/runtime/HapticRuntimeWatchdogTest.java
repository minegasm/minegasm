package net.minegasm.runtime;

import net.minegasm.buttplug.ButtplugProvider;
import net.minegasm.config.RuntimeConfig;
import net.minegasm.core.MaterialFeel;
import net.minegasm.observe.ClientStateSnapshot;
import net.minegasm.pack.PackRegistry;
import net.minegasm.testsupport.FakeButtplugServer;
import net.minegasm.time.FakeClock;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The client tick is a fast-path watchdog observer and the runtime scheduler is the independent observer.
 * Both must stop output without taking the worker monitor.
 */
class HapticRuntimeWatchdogTest {

    private final FakeClock clock = new FakeClock(1_000_000_000L);

    private static ClientStateSnapshot active(long tick) {
        return new ClientStateSnapshot(1f, 0f, 20, 0, 0f, 0, false, Optional.empty(), 0f,
                Optional.empty(), MaterialFeel.UNKNOWN, 0f, false, false, false, false,
                false /* paused */, true /* worldReady */, tick);
    }

    @Test
    void clientTickReachesTheWatchdogWhileTheWorkerMonitorIsHeld() throws Exception {
        HapticRuntime rt = new HapticRuntime(new ButtplugProvider(new FakeButtplugServer(), "test"),
                clock, () -> RuntimeConfig.defaults(), new PackRegistry(), null);

        rt.pump(clock.nanoTime()); // a healthy heartbeat
        rt.onClientTickEnd(active(1)); // mark the game active (this first tick resumes)
        clock.advanceMillis(3000); // stall past the watchdog threshold

        // Hold the worker monitor from another thread, standing in for a backend hung inside a cycle.
        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread holder = new Thread(() -> {
            synchronized (rt.worker()) {
                held.countDown();
                try {
                    release.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "monitor-holder");
        holder.setDaemon(true);
        holder.start();
        assertTrue(held.await(2, TimeUnit.SECONDS), "the monitor is held");

        // The tick must return promptly and the watchdog must have latched output off.
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> rt.onClientTickEnd(active(2)));
        assertFalse(rt.worker().isOutputEnabled(), "the watchdog fired despite the held monitor");
        assertTrue(rt.worker().isWatchdogStopped());

        release.countDown();
        holder.join(2000);
    }

    @Test
    void independentSchedulerFiresWithoutAClientTickAndStartIsIdempotent() throws Exception {
        HapticRuntime rt = new HapticRuntime(new ButtplugProvider(new FakeButtplugServer(), "test"),
                clock, () -> RuntimeConfig.defaults(), new PackRegistry(), null);
        rt.start();
        rt.start();
        rt.pump(clock.nanoTime());

        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread holder = new Thread(() -> {
            synchronized (rt.worker()) {
                held.countDown();
                try {
                    release.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "scheduled-watchdog-monitor-holder");
        holder.setDaemon(true);
        holder.start();
        assertTrue(held.await(2, TimeUnit.SECONDS));
        clock.advanceMillis(3_000);

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (!rt.worker().isWatchdogStopped() && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertTrue(rt.worker().isWatchdogStopped(),
                "the independent timer fired while no client tick ran and the worker monitor was held");

        release.countDown();
        holder.join(2_000);
        rt.shutdown();
    }
}
