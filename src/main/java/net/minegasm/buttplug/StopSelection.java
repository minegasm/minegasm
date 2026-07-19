package net.minegasm.buttplug;

/**
 * A first-class stop target (brief §9.10). All selections map to the protocol {@code StopCmd}, which
 * bypasses the timing gap; device/feature selections use its v4 {@code DeviceIndex} /
 * {@code FeatureIndex} fields.
 */
public sealed interface StopSelection {

    record All() implements StopSelection {}

    record Device(int deviceIndex) implements StopSelection {}

    record Feature(int deviceIndex, int featureIndex) implements StopSelection {}

    static StopSelection all() {
        return new All();
    }
}
