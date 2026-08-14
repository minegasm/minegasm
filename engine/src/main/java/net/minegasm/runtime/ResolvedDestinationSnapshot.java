package net.minegasm.runtime;

import net.minegasm.core.LogicalDestination;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable, time-sampled intended output after central timing, competition, and fatigue. */
public final class ResolvedDestinationSnapshot {

    private final long generation;
    private final long sampledAtNs;
    private final Map<LogicalDestination, Float> levels;

    public ResolvedDestinationSnapshot(long generation, long sampledAtNs,
                                       Map<LogicalDestination, Float> levels) {
        this.generation = generation;
        this.sampledAtNs = sampledAtNs;
        this.levels = Collections.unmodifiableMap(new LinkedHashMap<>(levels));
    }

    public long generation() {
        return generation;
    }

    public long sampledAtNs() {
        return sampledAtNs;
    }

    /** Positive current levels. Absence is authoritative zero. */
    public Map<LogicalDestination, Float> levels() {
        return levels;
    }

    public float level(LogicalDestination destination) {
        Float level = levels.get(destination);
        return level == null ? 0f : level;
    }

    public boolean isAllZero() {
        return levels.isEmpty();
    }
}
