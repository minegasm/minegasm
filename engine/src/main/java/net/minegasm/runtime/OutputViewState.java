package net.minegasm.runtime;

import net.minegasm.backend.BackendOutcome;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Shared global gate and per-integration delivery health for every command and screen. */
public final class OutputViewState {

    private final OutputStatus global;
    private final boolean bodyDriving;
    private final Map<String, BackendOutcome> latestOutcomes;
    private final Map<String, BackendOutcome> unresolvedFailures;

    public OutputViewState(OutputStatus global, boolean bodyDriving,
                           Map<String, BackendOutcome> latestOutcomes,
                           Map<String, BackendOutcome> unresolvedFailures) {
        this.global = global;
        this.bodyDriving = bodyDriving;
        this.latestOutcomes = Collections.unmodifiableMap(new LinkedHashMap<>(latestOutcomes));
        this.unresolvedFailures = Collections.unmodifiableMap(new LinkedHashMap<>(unresolvedFailures));
    }

    public OutputStatus global() { return global; }
    public boolean bodyDriving() { return bodyDriving; }
    public Map<String, BackendOutcome> latestOutcomes() { return latestOutcomes; }
    public Map<String, BackendOutcome> unresolvedFailures() { return unresolvedFailures; }
    public BackendOutcome latest(String backendId) { return latestOutcomes.get(backendId); }
    public BackendOutcome failure(String backendId) { return unresolvedFailures.get(backendId); }
}
