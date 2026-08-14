package net.minegasm.runtime;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * An immutable snapshot of why output is or isn't flowing, so every screen and command reads one truth
 * instead of each recomputing from a boolean. Global causes gate output; a backend fault is scoped health
 * information and does not claim healthy integrations stopped.
 */
public final class OutputStatus {

    private final EnumSet<StopCause> causes;

    private OutputStatus(EnumSet<StopCause> causes) {
        this.causes = causes;
    }

    public static OutputStatus of(Set<StopCause> causes) {
        EnumSet<StopCause> copy = causes.isEmpty()
                ? EnumSet.noneOf(StopCause.class) : EnumSet.copyOf(causes);
        return new OutputStatus(copy);
    }

    /** Whether the global gate permits output. Per-backend quarantine is deliberately not a global gate. */
    public boolean permitted() {
        return !causes.contains(StopCause.USER_STOP)
                && !causes.contains(StopCause.WATCHDOG)
                && !causes.contains(StopCause.DISABLED);
    }

    public boolean has(StopCause cause) {
        return causes.contains(cause);
    }

    public boolean userStopped() {
        return causes.contains(StopCause.USER_STOP);
    }

    public boolean watchdogStopped() {
        return causes.contains(StopCause.WATCHDOG);
    }

    public boolean disabled() {
        return causes.contains(StopCause.DISABLED);
    }

    public boolean backendFault() {
        return causes.contains(StopCause.BACKEND_FAULT);
    }

    public Set<StopCause> causes() {
        return Collections.unmodifiableSet(causes);
    }

    /**
     * Whether a user resume is the right affordance: only when a user stop is the reason, so a watchdog
     * stall or a disabled toggle never offers a one-click resume that would clear the wrong cause.
     */
    public boolean userResumable() {
        return causes.contains(StopCause.USER_STOP)
                && !causes.contains(StopCause.WATCHDOG)
                && !causes.contains(StopCause.DISABLED);
    }

    /** A short status string that retains every active cause. */
    public String reason() {
        if (causes.isEmpty()) {
            return "running";
        }
        java.util.List<String> active = new java.util.ArrayList<>();
        if (causes.contains(StopCause.WATCHDOG)) {
            active.add("watchdog stopped");
        }
        if (causes.contains(StopCause.USER_STOP)) {
            active.add("user stopped");
        }
        if (causes.contains(StopCause.BACKEND_FAULT)) {
            active.add("backend fault");
        }
        if (causes.contains(StopCause.DISABLED)) {
            active.add("disabled");
        }
        return String.join(", ", active);
    }

    @Override
    public String toString() {
        return "OutputStatus" + causes;
    }
}
