package net.minegasm.runtime;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * An immutable snapshot of why output is or isn't flowing, so every screen and command reads one truth
 * instead of each recomputing from a boolean (second follow-up review P1-2, UX #1). Output is permitted
 * only when no blocking {@link StopCause} is active.
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

    /** Output flows only when nothing is holding it off. */
    public boolean permitted() {
        return causes.isEmpty();
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
        return causes.contains(StopCause.USER_STOP);
    }

    /** A short reason string for a status line, most safety-relevant cause first. */
    public String reason() {
        if (causes.isEmpty()) {
            return "running";
        }
        if (causes.contains(StopCause.WATCHDOG)) {
            return "watchdog stopped";
        }
        if (causes.contains(StopCause.USER_STOP)) {
            return "user stopped";
        }
        if (causes.contains(StopCause.BACKEND_FAULT)) {
            return "backend fault";
        }
        return "disabled";
    }

    @Override
    public String toString() {
        return "OutputStatus" + causes;
    }
}
