package net.minegasm.runtime;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test
    void blockedReasonNamesEveryGatingCauseButNotABackendFault() {
        OutputStatus status = OutputStatus.of(EnumSet.of(
                StopCause.WATCHDOG, StopCause.USER_STOP, StopCause.BACKEND_FAULT));
        assertEquals("watchdog, user panic", status.blockedReason(),
                "the banner shows every gating cause, and not the backend fault which does not gate output");
    }

    @Test
    void blockedReasonIsEmptyWhenPermitted() {
        assertEquals("", OutputStatus.of(EnumSet.noneOf(StopCause.class)).blockedReason());
        assertEquals("", OutputStatus.of(EnumSet.of(StopCause.BACKEND_FAULT)).blockedReason(),
                "a lone backend fault does not stop the gate, so the banner stays empty");
    }
}
