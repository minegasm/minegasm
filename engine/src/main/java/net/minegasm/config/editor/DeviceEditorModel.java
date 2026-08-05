package net.minegasm.config.editor;

import net.minegasm.client.MinegasmClient;
import net.minegasm.config.DeviceSetting;
import net.minegasm.config.FeatureSetting;
import net.minegasm.config.HapticConfig;
import net.minegasm.config.PositionCalibration;
import net.minegasm.core.OutputKind;
import net.minegasm.device.HapticDevice;
import net.minegasm.device.HapticFeature;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minecraft-free staged editor for the per-device settings ({@code devices}, keyed by
 * {@link HapticDevice#identityKey()}) and {@code positionCalibrations} no existing screen exposes.
 *
 * <p>Built once per currently-known device list; a device absent from that list (disconnected, or
 * never seen this session) gets no {@link DeviceRow} and so is never touched, which is what lets a
 * saved setting for a currently-disconnected device survive {@link #toConfig()} unchanged instead of
 * being dropped whenever the map gets rebuilt. Each row only overwrites its config entry once the
 * screen actually edits it ({@link DeviceRow#deviceTouched} / {@link DeviceRow#calibrationTouched}),
 * so opening this screen and closing it without touching anything is a no-op.
 */
public final class DeviceEditorModel {

    private final HapticConfig original;
    private final Map<String, DeviceRow> rows = new LinkedHashMap<>();

    public DeviceEditorModel(HapticConfig original, List<HapticDevice> devices) {
        this.original = original;
        for (HapticDevice device : devices) {
            String identityKey = device.identityKey();
            DeviceSetting setting = original.devices().getOrDefault(identityKey, DeviceSetting.defaultOn());

            List<FeatureRow> features = new ArrayList<>();
            boolean calibrationApplies = false;
            for (HapticFeature feature : device.features().values()) {
                for (OutputKind kind : feature.outputs().keySet()) {
                    String featureKey = featureKey(kind, feature);
                    FeatureSetting featureSetting = setting.feature(featureKey);
                    features.add(new FeatureRow(feature.featureIndex(), feature.description(), kind,
                            featureSetting.enabled(), featureSetting.multiplier()));
                    if (kind == OutputKind.POSITION || kind == OutputKind.HW_POSITION_WITH_DURATION) {
                        calibrationApplies = true;
                    }
                }
            }

            PositionCalibration existingCalibration = original.positionCalibrations().get(identityKey);
            PositionCalibrationRow calibration = new PositionCalibrationRow(
                    existingCalibration != null ? existingCalibration : PositionCalibration.safeDefault());

            DeviceRow row = new DeviceRow(identityKey, device.label(), setting.enabled(),
                    setting.minLevel(), setting.maxLevel(), features, calibrationApplies, calibration);
            rows.put(identityKey, row);
        }
    }

    public List<DeviceRow> rows() {
        return new ArrayList<>(rows.values());
    }

    /** Same key format {@code SceneMixer.featureKey} reads at runtime; must match exactly or a saved
     * per-feature setting silently never takes effect. */
    private static String featureKey(OutputKind kind, HapticFeature feature) {
        return kind.wireName() + "|" + feature.featureIndex() + "|" + feature.description();
    }

    /** Rebuild the persisted config: the original with only touched device/calibration entries replaced. */
    public HapticConfig toConfig() {
        Map<String, DeviceSetting> newDevices = new LinkedHashMap<>(original.devices());
        Map<String, PositionCalibration> newCalibrations =
                new LinkedHashMap<>(original.positionCalibrations());

        for (DeviceRow row : rows.values()) {
            if (row.deviceTouched) {
                Map<String, FeatureSetting> featureSettings = new LinkedHashMap<>();
                for (FeatureRow feature : row.features) {
                    featureSettings.put(feature.key(),
                            new FeatureSetting(feature.enabled, feature.multiplier));
                }
                newDevices.put(row.identityKey,
                        new DeviceSetting(row.enabled, row.minLevel, row.maxLevel, featureSettings));
            }
            if (row.calibrationApplies && row.calibrationTouched) {
                PositionCalibrationRow c = row.calibration;
                newCalibrations.put(row.identityKey, new PositionCalibration(c.enabled, c.minimum,
                        c.maximum, c.neutral, c.invert, c.gameplayTravelFraction,
                        c.requireReturnToNeutral));
            }
        }

        return new HapticConfig(original.schemaVersion(), original.profile(), original.global(),
                original.buttplug(), original.events(), original.outputPolicy(), newDevices,
                newCalibrations, original.accumulation(), original.customIntensity(),
                original.bridge());
    }

    /** Persist the edits through the shared client. */
    public void apply(MinegasmClient client) {
        client.updateConfig(toConfig());
    }

    /** One device's editable settings. {@link #deviceTouched} gates whether {@link #enabled},
     * {@link #maxLevel}, and {@link #features} are written back on save; {@link #calibrationTouched}
     * separately gates {@link #calibration}, so enabling a device doesn't implicitly pin a calibration
     * the user never opened. */
    public static final class DeviceRow {
        public final String identityKey;
        public final String label;
        public boolean enabled;
        public double minLevel;
        public double maxLevel;
        public final List<FeatureRow> features;
        public final boolean calibrationApplies;
        public final PositionCalibrationRow calibration;
        public boolean deviceTouched;
        public boolean calibrationTouched;

        DeviceRow(String identityKey, String label, boolean enabled, double minLevel, double maxLevel,
                  List<FeatureRow> features, boolean calibrationApplies,
                  PositionCalibrationRow calibration) {
            this.identityKey = identityKey;
            this.label = label;
            this.enabled = enabled;
            this.minLevel = minLevel;
            this.maxLevel = maxLevel;
            this.features = features;
            this.calibrationApplies = calibrationApplies;
            this.calibration = calibration;
        }
    }

    /** One (feature, output kind) pair's enable flag and multiplier. A feature advertising more than
     * one output kind (e.g. a stroker feature with both Position and HwPositionWithDuration) gets one
     * row per kind, matching how {@code SceneMixer} keys {@code FeatureSetting} entries. */
    public static final class FeatureRow {
        public final int featureIndex;
        public final String description;
        public final OutputKind kind;
        public boolean enabled;
        public double multiplier;

        FeatureRow(int featureIndex, String description, OutputKind kind, boolean enabled,
                   double multiplier) {
            this.featureIndex = featureIndex;
            this.description = description;
            this.kind = kind;
            this.enabled = enabled;
            this.multiplier = multiplier;
        }

        String key() {
            return kind.wireName() + "|" + featureIndex + "|" + description;
        }
    }

    /** Editable mirror of {@link PositionCalibration}'s fields. */
    public static final class PositionCalibrationRow {
        public boolean enabled;
        public double minimum;
        public double maximum;
        public double neutral;
        public boolean invert;
        public double gameplayTravelFraction;
        public boolean requireReturnToNeutral;

        PositionCalibrationRow(PositionCalibration seed) {
            this.enabled = seed.enabled();
            this.minimum = seed.minimum();
            this.maximum = seed.maximum();
            this.neutral = seed.neutral();
            this.invert = seed.invert();
            this.gameplayTravelFraction = seed.gameplayTravelFraction();
            this.requireReturnToNeutral = seed.requireReturnToNeutral();
        }
    }
}
