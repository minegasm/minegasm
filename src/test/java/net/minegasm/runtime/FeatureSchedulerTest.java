package net.minegasm.runtime;

import net.minegasm.buttplug.OutputCommand;
import net.minegasm.core.HapticRole;
import net.minegasm.core.OutputKind;
import net.minegasm.device.DeviceRegistrySnapshot;
import net.minegasm.device.FeatureRef;
import net.minegasm.render.EndpointTarget;
import net.minegasm.testsupport.Devices;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeatureSchedulerTest {

    private static final long MS = 1_000_000L;

    private Map<String, EndpointTarget> target(DeviceRegistrySnapshot snap, float level) {
        FeatureRef ref = new FeatureRef(0, 0, snap.generation());
        EndpointTarget t = new EndpointTarget(ref, OutputKind.VIBRATE, level, null, 90, false,
                HapticRole.IMPACT);
        return Map.of(t.endpointKey(), t);
    }

    @Test
    void scalesLevelToDeviceRange() {
        DeviceRegistrySnapshot snap = Devices.singleVibrate(); // [0,20]
        FeatureScheduler scheduler = new FeatureScheduler();
        List<OutputCommand> commands = scheduler.accept(target(snap, 0.60f), snap, 0);
        assertEquals(1, commands.size());
        assertEquals(12, commands.get(0).value()); // 0.60 * 20 = 12
    }

    @Test
    void deadbandSuppressesUnchangedRepeat() {
        DeviceRegistrySnapshot snap = Devices.singleVibrate();
        FeatureScheduler scheduler = new FeatureScheduler();
        assertEquals(1, scheduler.accept(target(snap, 0.60f), snap, 0).size());
        assertTrue(scheduler.accept(target(snap, 0.60f), snap, MS).isEmpty(),
                "unchanged level within deadband should not re-send");
    }

    @Test
    void heldEndpointDrivenToZeroWhenNoLongerTargeted() {
        DeviceRegistrySnapshot snap = Devices.singleVibrate();
        FeatureScheduler scheduler = new FeatureScheduler();
        scheduler.accept(target(snap, 0.60f), snap, 0);
        List<OutputCommand> stop = scheduler.accept(Map.of(), snap, 50 * MS);
        assertEquals(1, stop.size());
        assertEquals(0, stop.get(0).value(), "held vibration needs an explicit zero");
    }

    @Test
    void staleGenerationTargetDropped() {
        DeviceRegistrySnapshot snap = Devices.singleVibrate(); // generation 1
        FeatureScheduler scheduler = new FeatureScheduler();
        FeatureRef stale = new FeatureRef(0, 0, 999L); // wrong generation
        EndpointTarget t = new EndpointTarget(stale, OutputKind.VIBRATE, 0.6f, null, 90, false,
                HapticRole.IMPACT);
        assertTrue(scheduler.accept(Map.of(t.endpointKey(), t), snap, 0).isEmpty(),
                "a command captured against an old generation must be discarded");
    }

    @Test
    void positionEndpointReleasedWithoutSlammingToZero() {
        // A raw 0 to a position endpoint would move the device to the end of its physical range,
        // outside the calibrated window; release must leave it where the envelope ended.
        DeviceRegistrySnapshot snap = Devices.registryWith(Devices.hwPosition(0, "stroker"));
        FeatureScheduler scheduler = new FeatureScheduler();
        FeatureRef ref = new FeatureRef(0, 0, snap.generation());
        EndpointTarget t = new EndpointTarget(ref, OutputKind.HW_POSITION_WITH_DURATION,
                0.5f, 200, 90, false, HapticRole.IMPACT);
        assertEquals(1, scheduler.accept(Map.of(t.endpointKey(), t), snap, 0).size());
        assertTrue(scheduler.accept(Map.of(), snap, 50 * MS).isEmpty(),
                "position endpoints hold on release; no zero command");
    }

    @Test
    void newRegistryGenerationForcesReEmit() {
        // A reused index in a new DeviceList is a new physical device: identical values must be
        // re-sent, never suppressed by stale local endpoint state.
        net.minegasm.buttplug.DeviceRegistry registry = new net.minegasm.buttplug.DeviceRegistry();
        DeviceRegistrySnapshot gen1 = registry.accept(
                java.util.List.of(Devices.vibrate(0, "toy", 0, 0, 20)));
        FeatureScheduler scheduler = new FeatureScheduler();
        assertEquals(1, scheduler.accept(target(gen1, 0.60f), gen1, 0).size());

        DeviceRegistrySnapshot gen2 = registry.accept(
                java.util.List.of(Devices.vibrate(0, "toy", 0, 0, 20)));
        List<OutputCommand> resent = scheduler.accept(target(gen2, 0.60f), gen2, MS);
        assertEquals(1, resent.size(), "same value on a new generation must be dispatched");
        assertEquals(12, resent.get(0).value());
    }

    @Test
    void timingGapDefersUpdate() {
        DeviceRegistrySnapshot snap = Devices.registryWith(Devices.vibrate(0, "gap", 100, 0, 20));
        FeatureScheduler scheduler = new FeatureScheduler();
        assertEquals(1, scheduler.accept(target(snap, 0.30f), snap, 0).size());
        // 50 ms later, inside the 100 ms gap: suppressed even though the value changed.
        assertTrue(scheduler.accept(target(snap, 0.90f), snap, 50 * MS).isEmpty());
        // 150 ms later, gap elapsed: emitted.
        assertEquals(1, scheduler.accept(target(snap, 0.90f), snap, 150 * MS).size());
    }
}
