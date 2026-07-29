package net.minegasm.render;

import net.minegasm.core.HapticRole;
import net.minegasm.core.OutputKind;
import net.minegasm.device.FeatureRef;

import java.util.Objects;

/**
 * The desired instantaneous output for one feature endpoint at a moment in time, as computed by the
 * mixer/renderer: a normalized level (0..1), the output kind, an optional movement duration (for
 * {@code HwPositionWithDuration}), and the priority/exclusivity that produced it. The scheduler
 * turns this into an actual {@link net.minegasm.buttplug.OutputCommand} with range scaling and caps.
 */
public final class EndpointTarget {

    private final FeatureRef ref;
    private final OutputKind kind;
    private final float level;
    private final Integer durationMs;
    private final int priority;
    private final boolean exclusive;
    private final HapticRole role;

    public EndpointTarget(FeatureRef ref, OutputKind kind, float level, Integer durationMs,
                          int priority, boolean exclusive, HapticRole role) {
        this.ref = ref;
        this.kind = kind;
        this.level = level;
        this.durationMs = durationMs;
        this.priority = priority;
        this.exclusive = exclusive;
        this.role = role;
    }

    public FeatureRef ref() {
        return ref;
    }

    public OutputKind kind() {
        return kind;
    }

    public float level() {
        return level;
    }

    public Integer durationMs() {
        return durationMs;
    }

    public int priority() {
        return priority;
    }

    public boolean exclusive() {
        return exclusive;
    }

    public HapticRole role() {
        return role;
    }

    /** Stable per-endpoint key (device:feature:kind), independent of registry generation. */
    public String endpointKey() {
        return ref.deviceIndex() + ":" + ref.featureIndex() + ":" + kind.name();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof EndpointTarget)) {
            return false;
        }
        EndpointTarget other = (EndpointTarget) o;
        return Float.compare(level, other.level) == 0
                && priority == other.priority
                && exclusive == other.exclusive
                && Objects.equals(ref, other.ref)
                && kind == other.kind
                && Objects.equals(durationMs, other.durationMs)
                && role == other.role;
    }

    @Override
    public int hashCode() {
        return Objects.hash(ref, kind, level, durationMs, priority, exclusive, role);
    }

    @Override
    public String toString() {
        return "EndpointTarget[ref=" + ref + ", kind=" + kind + ", level=" + level
                + ", durationMs=" + durationMs + ", priority=" + priority + ", exclusive=" + exclusive
                + ", role=" + role + "]";
    }
}
