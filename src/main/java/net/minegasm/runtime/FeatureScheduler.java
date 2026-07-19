package net.minegasm.runtime;

import net.minegasm.buttplug.OutputCommand;
import net.minegasm.core.OutputKind;
import net.minegasm.device.DeviceRegistrySnapshot;
import net.minegasm.device.FeatureRef;
import net.minegasm.device.HapticDevice;
import net.minegasm.device.HapticFeature;
import net.minegasm.device.IntRange;
import net.minegasm.device.OutputCapability;
import net.minegasm.render.EndpointTarget;
import net.minegasm.util.HapticMath;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-feature output scheduling (brief §6.2 structure D, §9.8, §10.5). Converts desired endpoint
 * targets into concrete {@link OutputCommand}s, applying: registry-generation validation, integer
 * range scaling, a normalized deadband so tiny changes are not spammed, the device's advertised
 * timing gap, and — crucially — a planned zero command when a held vibration-like endpoint is no
 * longer targeted (such outputs hold their level until changed, so every gesture needs a stop).
 * Position-like endpoints are released without a zero: see {@link #needsZeroOnRelease}.
 *
 * <p>Confined to the worker thread; not synchronised.
 */
public final class FeatureScheduler {

    /** Minimum normalized change before a non-zero, non-motion update is re-sent (brief §10.4). */
    private static final float DEADBAND = 0.03f;

    private final Map<String, EndpointState> states = new LinkedHashMap<>();

    private static final class EndpointState {
        float lastLevel;
        int lastValue;
        long lastDispatchNs;
        long generation;
        boolean active;
    }

    /**
     * Produce the commands to send this cycle. Targets not backed by the current registry generation
     * are ignored; vibration-like endpoints previously active but absent now are driven to zero.
     */
    public List<OutputCommand> accept(Map<String, EndpointTarget> targets,
                                      DeviceRegistrySnapshot snapshot, long nowNs) {
        List<OutputCommand> commands = new ArrayList<>();

        for (EndpointTarget target : targets.values()) {
            HapticFeature feature = snapshot.resolve(
                    target.ref(), target.kind()).orElse(null);
            if (feature == null) {
                continue; // generation mismatch, device/feature gone, or kind no longer advertised
            }
            OutputCapability cap = feature.outputs().get(target.kind());
            if (cap == null) {
                continue;
            }
            int value = scale(target.level(), cap);
            EndpointState state = states.computeIfAbsent(target.endpointKey(), k -> new EndpointState());
            if (state.generation != snapshot.generation()) {
                // A new generation can mean a different physical device behind the same index; drop
                // held-endpoint assumptions so the first command to it is always dispatched (§9.5).
                state.active = false;
            }
            long gapNs = timingGapNs(snapshot, target.ref().deviceIndex());
            boolean changedEnough = !state.active
                    || Math.abs(target.level() - state.lastLevel) >= DEADBAND
                    || value != state.lastValue;
            boolean gapElapsed = nowNs - state.lastDispatchNs >= gapNs;
            boolean motion = target.kind().carriesDuration();

            // Motion re-issues only for a new position; both paths respect the device timing gap
            // except for a first command to an idle endpoint.
            boolean emit = motion
                    ? ((!state.active || value != state.lastValue) && (gapElapsed || !state.active))
                    : (changedEnough && (gapElapsed || !state.active));
            if (!emit) {
                continue;
            }
            commands.add(new OutputCommand(target.ref().deviceIndex(), target.ref().featureIndex(),
                    target.kind(), value, target.durationMs(), snapshot.generation()));
            state.active = true;
            state.lastLevel = target.level();
            state.lastValue = value;
            state.lastDispatchNs = nowNs;
            state.generation = snapshot.generation();
        }

        // Release pass: endpoints that were active but are no longer targeted.
        for (Iterator<Map.Entry<String, EndpointState>> it = states.entrySet().iterator(); it.hasNext();) {
            Map.Entry<String, EndpointState> e = it.next();
            EndpointState state = e.getValue();
            if (!state.active || targets.containsKey(e.getKey())) {
                continue;
            }
            Release release = release(e.getKey(), snapshot);
            if (release.zero() != null) {
                commands.add(release.zero());
                state.lastValue = 0;
            }
            // Whether or not a zero was sent, the local endpoint is now idle.
            state.active = false;
            state.lastLevel = 0f;
            state.lastDispatchNs = nowNs;
            if (!release.devicePresent()) {
                it.remove(); // device gone; forget it
            }
        }
        return commands;
    }

    /** Forget all endpoint state (on stop/registry invalidation) so nothing is reasserted. */
    public void reset() {
        states.clear();
    }

    private record Release(OutputCommand zero, boolean devicePresent) {}

    private Release release(String endpointKey, DeviceRegistrySnapshot snapshot) {
        // endpointKey = deviceIndex:featureIndex:KIND
        String[] parts = endpointKey.split(":");
        if (parts.length != 3) {
            return new Release(null, false);
        }
        int deviceIndex = Integer.parseInt(parts[0]);
        int featureIndex = Integer.parseInt(parts[1]);
        OutputKind kind = OutputKind.valueOf(parts[2]);
        FeatureRef ref = new FeatureRef(deviceIndex, featureIndex, snapshot.generation());
        boolean present = snapshot.resolve(ref, kind).isPresent();
        if (!present || !needsZeroOnRelease(kind)) {
            return new Release(null, present);
        }
        return new Release(
                new OutputCommand(deviceIndex, featureIndex, kind, 0, null, snapshot.generation()),
                true);
    }

    /**
     * Which held kinds must be driven to zero when their layer ends. Vibration-like outputs keep
     * stimulating until changed, so a planned zero is mandatory (brief §9.8). Position-like outputs
     * merely hold a location: commanding a raw 0 would slam the device to an end of its physical
     * range, outside the calibrated window — release must leave them where the envelope ended
     * (recipes end motion at/near neutral; {@code StopCmd} covers emergencies).
     */
    private static boolean needsZeroOnRelease(OutputKind kind) {
        return switch (kind) {
            case VIBRATE, OSCILLATE, ROTATE, CONSTRICT -> true;
            default -> false;
        };
    }

    private int scale(float level, OutputCapability cap) {
        IntRange range = cap.valueRange();
        if (range.isSigned()) {
            // A 0..1 magnitude maps to the positive half of a signed range; a signed output needs an
            // explicit direction decision before using the negative half (brief §9.7).
            return Math.round(HapticMath.clamp01(level) * range.max());
        }
        return HapticMath.scaleNormalized(level, range);
    }

    private long timingGapNs(DeviceRegistrySnapshot snapshot, int deviceIndex) {
        return snapshot.device(deviceIndex)
                .map(HapticDevice::messageTimingGapMs)
                .map(ms -> ms * 1_000_000L)
                .orElse(0L);
    }
}
