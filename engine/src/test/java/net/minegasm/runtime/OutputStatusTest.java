package net.minegasm.runtime;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutputStatusTest {

    @Test
    void backendFaultDoesNotPretendTheGlobalGateIsClosed() {
        OutputStatus status = OutputStatus.of(EnumSet.of(StopCause.BACKEND_FAULT));
        assertTrue(status.permitted(), "healthy backends may continue while one integration is quarantined");
    }

    @Test
    void compoundWatchdogAndUserStopDoesNotOfferResume() {
        OutputStatus status = OutputStatus.of(EnumSet.of(StopCause.USER_STOP, StopCause.WATCHDOG));
        assertFalse(status.permitted());
        assertFalse(status.userResumable(),
                "clearing the user cause would not restore output while watchdog remains active");
    }
}
