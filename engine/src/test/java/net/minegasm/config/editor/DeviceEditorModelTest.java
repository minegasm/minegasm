package net.minegasm.config.editor;

import net.minegasm.config.DeviceSetting;
import net.minegasm.config.FeatureSetting;
import net.minegasm.config.HapticConfig;
import net.minegasm.config.PositionCalibration;
import net.minegasm.core.BodyRegion;
import net.minegasm.core.OutputKind;
import net.minegasm.device.HapticDevice;
import net.minegasm.device.HapticFeature;
import net.minegasm.device.IntRange;
import net.minegasm.device.OutputCapability;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeviceEditorModelTest {

    private static HapticFeature vibrateFeature() {
        Map<OutputKind, OutputCapability> outputs = new LinkedHashMap<>();
        outputs.put(OutputKind.VIBRATE, OutputCapability.level(OutputKind.VIBRATE, 0, 20));
        return new HapticFeature(0, "Vibrator", outputs, Map.of());
    }

    /** A stroker feature advertising two output kinds, matching HapticFeature's own javadoc example. */
    private static HapticFeature strokerFeature() {
        Map<OutputKind, OutputCapability> outputs = new LinkedHashMap<>();
        outputs.put(OutputKind.POSITION, OutputCapability.level(OutputKind.POSITION, 0, 100));
        outputs.put(OutputKind.HW_POSITION_WITH_DURATION,
                OutputCapability.withDuration(OutputKind.HW_POSITION_WITH_DURATION,
                        new IntRange(0, 100), new IntRange(0, 5000)));
        return new HapticFeature(1, "Stroker", outputs, Map.of());
    }

    private static HapticDevice strokerDevice() {
        Map<Integer, HapticFeature> features = new LinkedHashMap<>();
        features.put(0, vibrateFeature());
        features.put(1, strokerFeature());
        return new HapticDevice(0, "The Handy", Optional.empty(), 100, features, 1L);
    }

    @Test
    void noEditsLeaveConfigUnchanged() {
        HapticConfig original = HapticConfig.defaults();
        DeviceEditorModel model = new DeviceEditorModel(original, List.of(strokerDevice()));

        HapticConfig result = model.toConfig();

        assertEquals(original.devices(), result.devices());
        assertEquals(original.positionCalibrations(), result.positionCalibrations());
    }

    @Test
    void touchedDeviceWritesFeatureSettingsWithSceneMixerKeyFormat() {
        HapticDevice device = strokerDevice();
        DeviceEditorModel model = new DeviceEditorModel(HapticConfig.defaults(), List.of(device));

        DeviceEditorModel.DeviceRow row = model.rows().get(0);
        assertEquals(3, row.features.size(), "vibrate + position + hwPositionWithDuration rows");
        row.deviceTouched = true;
        row.maxLevel = 0.5;
        for (DeviceEditorModel.FeatureRow feature : row.features) {
            if (feature.kind == OutputKind.VIBRATE) {
                feature.multiplier = 1.5;
            }
        }

        HapticConfig result = model.toConfig();

        DeviceSetting saved = result.devices().get(device.identityKey());
        assertEquals(0.5, saved.maxLevel(), 1e-9);
        FeatureSetting vibrate = saved.features().get("Vibrate|0|Vibrator");
        assertEquals(1.5, vibrate.multiplier(), 1e-9);
        // Both stroker output kinds get their own entry, keyed exactly like SceneMixer.featureKey.
        assertTrue(saved.features().containsKey("Position|1|Stroker"));
        assertTrue(saved.features().containsKey("HwPositionWithDuration|1|Stroker"));
    }

    @Test
    void editingRegionWritesItAndSeedsAsNotSet() {
        HapticDevice device = strokerDevice();
        DeviceEditorModel model = new DeviceEditorModel(HapticConfig.defaults(), List.of(device));

        DeviceEditorModel.DeviceRow row = model.rows().get(0);
        assertNull(row.region, "a fresh device seeds as not set, distinct from an explicit whole body");

        row.deviceTouched = true;
        row.region = BodyRegion.NIPPLE;
        DeviceSetting saved = model.toConfig().devices().get(device.identityKey());
        assertTrue(saved.regionAssigned());
        assertEquals(BodyRegion.NIPPLE, saved.bodyRegion());
    }

    @Test
    void regionControlCyclesThroughNotSetAndLabelsIt() {
        assertEquals("Not set", DeviceEditorModel.regionLabel(null));
        assertEquals(BodyRegion.WHOLE_BODY, DeviceEditorModel.nextRegion(null), "not set steps to the first region");
        assertEquals("Whole body", DeviceEditorModel.regionLabel(BodyRegion.WHOLE_BODY));
        BodyRegion last = BodyRegion.values()[BodyRegion.values().length - 1];
        assertNull(DeviceEditorModel.nextRegion(last), "cycling off the last region returns to not set");
    }

    @Test
    void disconnectedDeviceEntrySurvivesUntouched() {
        String otherIdentity = "Some Other Device|0:Vibrate,;";
        Map<String, DeviceSetting> devices = new LinkedHashMap<>();
        devices.put(otherIdentity, new DeviceSetting(false, 0.22, 0.4, Map.of()));
        HapticConfig defaults = HapticConfig.defaults();
        HapticConfig original = new HapticConfig(defaults.schemaVersion(), defaults.profile(),
                defaults.global(), defaults.buttplug(), defaults.events(), defaults.outputPolicy(),
                devices, defaults.positionCalibrations(), defaults.accumulation(),
                defaults.customIntensity(), defaults.bridges());

        // The device currently connected is NOT the one in `devices` above.
        DeviceEditorModel model = new DeviceEditorModel(original, List.of(strokerDevice()));
        model.rows().get(0).deviceTouched = true;

        HapticConfig result = model.toConfig();

        assertEquals(devices.get(otherIdentity), result.devices().get(otherIdentity));
    }

    @Test
    void calibrationOnlyWrittenWhenCalibrationTouched() {
        HapticDevice device = strokerDevice();
        DeviceEditorModel model = new DeviceEditorModel(HapticConfig.defaults(), List.of(device));

        DeviceEditorModel.DeviceRow row = model.rows().get(0);
        assertTrue(row.calibrationApplies);
        row.deviceTouched = true; // enable/maxLevel/feature edits only

        HapticConfig result = model.toConfig();
        assertFalse(result.positionCalibrations().containsKey(device.identityKey()));

        row.calibrationTouched = true;
        row.calibration.minimum = 0.3;
        HapticConfig withCalibration = model.toConfig();
        PositionCalibration saved = withCalibration.positionCalibrations().get(device.identityKey());
        assertEquals(0.3, saved.minimum(), 1e-9);
    }

    @Test
    void calibrationDoesNotApplyToVibrateOnlyDevice() {
        Map<Integer, HapticFeature> features = new LinkedHashMap<>();
        features.put(0, vibrateFeature());
        HapticDevice vibrator = new HapticDevice(0, "Simple Vibe", Optional.empty(), 100, features, 1L);

        DeviceEditorModel model = new DeviceEditorModel(HapticConfig.defaults(), List.of(vibrator));

        assertFalse(model.rows().get(0).calibrationApplies);
    }
}
