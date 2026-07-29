package net.minegasm.buttplug;

/**
 * A first-class stop target (brief §9.10). All selections map to the protocol {@code StopCmd}, which
 * bypasses the timing gap; device/feature selections use its v4 {@code DeviceIndex} /
 * {@code FeatureIndex} fields.
 */
public interface StopSelection {

    final class All implements StopSelection {
        public All() {
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof All;
        }

        @Override
        public int hashCode() {
            return All.class.hashCode();
        }

        @Override
        public String toString() {
            return "All[]";
        }
    }

    final class Device implements StopSelection {
        private final int deviceIndex;

        public Device(int deviceIndex) {
            this.deviceIndex = deviceIndex;
        }

        public int deviceIndex() {
            return deviceIndex;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Device)) {
                return false;
            }
            return deviceIndex == ((Device) o).deviceIndex;
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(deviceIndex);
        }

        @Override
        public String toString() {
            return "Device[deviceIndex=" + deviceIndex + "]";
        }
    }

    final class Feature implements StopSelection {
        private final int deviceIndex;
        private final int featureIndex;

        public Feature(int deviceIndex, int featureIndex) {
            this.deviceIndex = deviceIndex;
            this.featureIndex = featureIndex;
        }

        public int deviceIndex() {
            return deviceIndex;
        }

        public int featureIndex() {
            return featureIndex;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Feature)) {
                return false;
            }
            Feature other = (Feature) o;
            return deviceIndex == other.deviceIndex && featureIndex == other.featureIndex;
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(deviceIndex, featureIndex);
        }

        @Override
        public String toString() {
            return "Feature[deviceIndex=" + deviceIndex + ", featureIndex=" + featureIndex + "]";
        }
    }

    static StopSelection all() {
        return new All();
    }
}
