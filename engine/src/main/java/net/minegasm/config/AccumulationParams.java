package net.minegasm.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Accumulation-mode parameters (brief §11.5). Charge accumulates from event contributions and
 * decays over real time; output level is a curve of {@code charge / capacity}. Legacy Minegasm
 * accumulation is reproduced by the default contributions (attack 5, hurt 10, ore 1, break 0.25,
 * place 0.5, xp/5, advancement 1) and a real-time decay.
 */
public final class AccumulationParams implements ConfigValue {

    private final double capacity;
    private final double decayPerSecond;
    private final String outputCurve;
    private final Map<String, Double> contributions;

    public AccumulationParams(double capacity, double decayPerSecond, String outputCurve,
                              Map<String, Double> contributions) {
        this.capacity = capacity <= 0 ? 100.0 : capacity;
        this.decayPerSecond = decayPerSecond < 0 ? 0 : decayPerSecond;
        this.outputCurve = outputCurve == null || outputCurve.trim().isEmpty()
                ? "smoothstep" : outputCurve;
        this.contributions = contributions == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(contributions));
    }

    public double capacity() {
        return capacity;
    }

    public double decayPerSecond() {
        return decayPerSecond;
    }

    public String outputCurve() {
        return outputCurve;
    }

    public Map<String, Double> contributions() {
        return contributions;
    }

    public double contribution(String eventKey) {
        return contributions.getOrDefault(eventKey, 0.0);
    }

    /** Defaults tuned to reproduce legacy Minegasm accumulation behaviour (brief §A, legacy source). */
    public static AccumulationParams defaults() {
        Map<String, Double> contributions = new LinkedHashMap<>();
        contributions.put("attack", 5.0);
        contributions.put("hurt", 10.0);
        contributions.put("blockBreak", 0.25);
        contributions.put("oreBreak", 1.0);
        contributions.put("place", 0.5);
        contributions.put("xpPerFive", 1.0);
        contributions.put("advancement", 1.0);
        contributions.put("harvest", 0.5);
        return new AccumulationParams(100.0, 1.5, "smoothstep", contributions);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AccumulationParams)) {
            return false;
        }
        AccumulationParams other = (AccumulationParams) o;
        return Double.compare(capacity, other.capacity) == 0
                && Double.compare(decayPerSecond, other.decayPerSecond) == 0
                && Objects.equals(outputCurve, other.outputCurve)
                && Objects.equals(contributions, other.contributions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(capacity, decayPerSecond, outputCurve, contributions);
    }

    @Override
    public String toString() {
        return "AccumulationParams[capacity=" + capacity + ", decayPerSecond=" + decayPerSecond
                + ", outputCurve=" + outputCurve + ", contributions=" + contributions + "]";
    }
}
