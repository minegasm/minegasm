package net.minegasm.render;

import net.minegasm.core.HapticRole;
import net.minegasm.core.OutputKind;
import net.minegasm.device.FeatureRef;

/**
 * The desired instantaneous output for one feature endpoint at a moment in time, as computed by the
 * mixer/renderer: a normalized level (0..1), the output kind, an optional movement duration (for
 * {@code HwPositionWithDuration}), and the priority/exclusivity that produced it. The scheduler
 * turns this into an actual {@link net.minegasm.buttplug.OutputCommand} with range scaling and caps.
 */
public record EndpointTarget(
        FeatureRef ref,
        OutputKind kind,
        float level,
        Integer durationMs,
        int priority,
        boolean exclusive,
        HapticRole role) {

    /** Stable per-endpoint key (device:feature:kind), independent of registry generation. */
    public String endpointKey() {
        return ref.deviceIndex() + ":" + ref.featureIndex() + ":" + kind.name();
    }
}
