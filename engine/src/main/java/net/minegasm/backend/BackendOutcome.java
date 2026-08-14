package net.minegasm.backend;

/** Immutable result suitable for coordinator health and user-facing action feedback. */
public final class BackendOutcome {

    private final String backendId;
    private final BackendOperation operation;
    private final BackendOutcomeState state;
    private final long generation;
    private final long occurredAtNs;
    private final String detail;

    public BackendOutcome(String backendId, BackendOperation operation, BackendOutcomeState state,
                          long generation, long occurredAtNs, String detail) {
        this.backendId = backendId;
        this.operation = operation;
        this.state = state;
        this.generation = generation;
        this.occurredAtNs = occurredAtNs;
        this.detail = detail;
    }

    public String backendId() { return backendId; }
    public BackendOperation operation() { return operation; }
    public BackendOutcomeState state() { return state; }
    public long generation() { return generation; }
    public long occurredAtNs() { return occurredAtNs; }
    public String detail() { return detail; }

    public boolean unresolvedFault() {
        return state == BackendOutcomeState.FAILED || state == BackendOutcomeState.TIMED_OUT;
    }

    @Override
    public String toString() {
        return operation + " " + state + (detail == null || detail.isEmpty() ? "" : ": " + detail);
    }
}
