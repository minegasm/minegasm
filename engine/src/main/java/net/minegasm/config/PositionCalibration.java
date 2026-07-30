package net.minegasm.config;

import net.minegasm.util.HapticMath;

import java.util.Objects;

/**
 * Per-feature calibration for position (stroker) output (brief §9.9, §11.2). All values are
 * normalized {@code [0, 1]} positions on the device's travel. Calibration is optional: gameplay moves a
 * position feature within a conservative {@link #safeDefault()} when no enabled calibration is set, and
 * an enabled calibration widens or reshapes that window.
 */
public final class PositionCalibration implements ConfigValue {

    private final boolean enabled;
    private final double minimum;
    private final double maximum;
    private final double neutral;
    private final boolean invert;
    private final double gameplayTravelFraction;
    private final boolean requireReturnToNeutral;

    public PositionCalibration(
            boolean enabled,
            double minimum,
            double maximum,
            double neutral,
            boolean invert,
            double gameplayTravelFraction,
            boolean requireReturnToNeutral) {
        this.enabled = enabled;
        double min = HapticMath.clamp(minimum, 0.0, 1.0);
        double max = HapticMath.clamp(maximum, 0.0, 1.0);
        if (max < min) {
            double t = min;
            min = max;
            max = t;
        }
        this.minimum = min;
        this.maximum = max;
        this.neutral = HapticMath.clamp(neutral, min, max);
        this.invert = invert;
        // Gameplay never uses full travel; hard cap at 20% of the calibrated span (brief §9.9).
        this.gameplayTravelFraction = HapticMath.clamp(gameplayTravelFraction, 0.0, 0.20);
        this.requireReturnToNeutral = requireReturnToNeutral;
    }

    public boolean enabled() {
        return enabled;
    }

    public double minimum() {
        return minimum;
    }

    public double maximum() {
        return maximum;
    }

    public double neutral() {
        return neutral;
    }

    public boolean invert() {
        return invert;
    }

    public double gameplayTravelFraction() {
        return gameplayTravelFraction;
    }

    public boolean requireReturnToNeutral() {
        return requireReturnToNeutral;
    }

    public static PositionCalibration disabled() {
        return new PositionCalibration(false, 0.20, 0.80, 0.50, false, 0.20, true);
    }

    /**
     * Conservative motion used when the device has no explicit calibration: centered neutral, a narrow
     * travel window, and the gameplay travel fraction already at its hard cap. Lets strokers move out of
     * the box within safe bounds; an explicit calibration can widen or reshape it later.
     */
    public static PositionCalibration safeDefault() {
        return new PositionCalibration(true, 0.20, 0.80, 0.50, false, 0.20, true);
    }

    /** Calibrated span available to gameplay (already capped). */
    public double gameplaySpan() {
        return (maximum - minimum) * gameplayTravelFraction;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PositionCalibration)) {
            return false;
        }
        PositionCalibration other = (PositionCalibration) o;
        return enabled == other.enabled
                && Double.compare(minimum, other.minimum) == 0
                && Double.compare(maximum, other.maximum) == 0
                && Double.compare(neutral, other.neutral) == 0
                && invert == other.invert
                && Double.compare(gameplayTravelFraction, other.gameplayTravelFraction) == 0
                && requireReturnToNeutral == other.requireReturnToNeutral;
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, minimum, maximum, neutral, invert, gameplayTravelFraction,
                requireReturnToNeutral);
    }

    @Override
    public String toString() {
        return "PositionCalibration[enabled=" + enabled + ", minimum=" + minimum + ", maximum=" + maximum
                + ", neutral=" + neutral + ", invert=" + invert
                + ", gameplayTravelFraction=" + gameplayTravelFraction
                + ", requireReturnToNeutral=" + requireReturnToNeutral + "]";
    }
}
