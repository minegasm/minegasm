package net.minegasm.core;

import java.util.Objects;

/** A backend-neutral output address used for competition, fatigue, bridging, and status. */
public final class LogicalDestination {

    private final HapticRole role;
    private final BodyRegion bodyRegion;
    private final OutputClass outputClass;

    public LogicalDestination(HapticRole role, BodyRegion bodyRegion, OutputClass outputClass) {
        this.role = Objects.requireNonNull(role, "role");
        this.bodyRegion = Objects.requireNonNull(bodyRegion, "bodyRegion");
        this.outputClass = Objects.requireNonNull(outputClass, "outputClass");
    }

    public HapticRole role() {
        return role;
    }

    public BodyRegion bodyRegion() {
        return bodyRegion;
    }

    public OutputClass outputClass() {
        return outputClass;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof LogicalDestination)) {
            return false;
        }
        LogicalDestination other = (LogicalDestination) o;
        return role == other.role && bodyRegion == other.bodyRegion && outputClass == other.outputClass;
    }

    @Override
    public int hashCode() {
        return Objects.hash(role, bodyRegion, outputClass);
    }

    @Override
    public String toString() {
        return role + "/" + bodyRegion + "/" + outputClass;
    }
}
