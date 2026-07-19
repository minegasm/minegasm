package net.minegasm.config;

import java.util.Collections;
import java.util.Map;

/**
 * Accumulation-mode parameters (brief §11.5). Charge accumulates from event contributions and
 * decays over real time; output level is a curve of {@code charge / capacity}. Legacy Minegasm
 * accumulation is reproduced by the default contributions (attack 5, hurt 10, ore 1, break 0.25,
 * place 0.5, xp/5, advancement 1) and a real-time decay.
 */
public record AccumulationParams(
        double capacity,
        double decayPerSecond,
        String outputCurve,
        Map<String, Double> contributions) {

    public AccumulationParams {
        if (capacity <= 0) {
            capacity = 100.0;
        }
        if (decayPerSecond < 0) {
            decayPerSecond = 0;
        }
        outputCurve = outputCurve == null || outputCurve.isBlank() ? "smoothstep" : outputCurve;
        contributions = contributions == null
                ? Map.of()
                : Collections.unmodifiableMap(Map.copyOf(contributions));
    }

    public double contribution(String eventKey) {
        return contributions.getOrDefault(eventKey, 0.0);
    }

    /** Defaults tuned to reproduce legacy Minegasm accumulation behaviour (brief §A, legacy source). */
    public static AccumulationParams defaults() {
        return new AccumulationParams(100.0, 1.5, "smoothstep", Map.ofEntries(
                Map.entry("attack", 5.0),
                Map.entry("hurt", 10.0),
                Map.entry("blockBreak", 0.25),
                Map.entry("oreBreak", 1.0),
                Map.entry("place", 0.5),
                Map.entry("xpPerFive", 1.0),
                Map.entry("advancement", 1.0),
                Map.entry("harvest", 0.5)));
    }
}
