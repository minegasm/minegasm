package net.minegasm.config;

import net.minegasm.util.HapticMath;

/**
 * Per-feature calibration for experimental position output (brief §9.9, §11.2). All values are
 * normalized {@code [0, 1]} positions on the device's travel. Disabled by default; gameplay must
 * never move a position feature before calibration and explicit opt-in.
 */
public record PositionCalibration(
        boolean enabled,
        double minimum,
        double maximum,
        double neutral,
        boolean invert,
        double gameplayTravelFraction,
        boolean requireReturnToNeutral) {

    public PositionCalibration {
        minimum = HapticMath.clamp(minimum, 0.0, 1.0);
        maximum = HapticMath.clamp(maximum, 0.0, 1.0);
        if (maximum < minimum) {
            double t = minimum;
            minimum = maximum;
            maximum = t;
        }
        neutral = HapticMath.clamp(neutral, minimum, maximum);
        // Gameplay never uses full travel; hard cap at 20% of the calibrated span (brief §9.9).
        gameplayTravelFraction = HapticMath.clamp(gameplayTravelFraction, 0.0, 0.20);
    }

    public static PositionCalibration disabled() {
        return new PositionCalibration(false, 0.20, 0.80, 0.50, false, 0.20, true);
    }

    /** Calibrated span available to gameplay (already capped). */
    public double gameplaySpan() {
        return (maximum - minimum) * gameplayTravelFraction;
    }
}
