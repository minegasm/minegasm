package net.minegasm.config.editor;

import net.minegasm.config.AccumulationParams;
import net.minegasm.config.CustomIntensities;
import net.minegasm.config.DeviceSetting;
import net.minegasm.config.EventSetting;
import net.minegasm.config.HapticConfig;
import net.minegasm.config.OutputPolicy;
import net.minegasm.config.PositionCalibration;
import net.minegasm.core.GameEventKind;
import net.minegasm.core.OutputKind;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomizationModelTest {

    /** A config with non-default devices/positionCalibrations so the round-trip test can prove they
     * survive untouched, plus an off-default event/output entry to prove edits actually apply. */
    private static HapticConfig sample() {
        HapticConfig defaults = HapticConfig.defaults();

        Map<String, DeviceSetting> devices = new LinkedHashMap<>();
        devices.put("Lovense Edge|0:Vibrate,;", new DeviceSetting(true, 0.75, Map.of()));

        Map<String, PositionCalibration> calibrations = new LinkedHashMap<>();
        calibrations.put("Lovense Edge|0:Vibrate,;",
                new PositionCalibration(true, 0.1, 0.9, 0.5, true, 0.15, false));

        return new HapticConfig(defaults.schemaVersion(), defaults.profile(), defaults.global(),
                defaults.buttplug(), defaults.events(), defaults.outputPolicy(), devices, calibrations,
                defaults.accumulation(), defaults.customIntensity());
    }

    @Test
    void untouchedFieldsRoundTrip() {
        HapticConfig original = sample();
        CustomizationModel model = new CustomizationModel(original);

        HapticConfig result = model.toConfig();

        assertEquals(original.profile(), result.profile());
        assertEquals(original.global(), result.global());
        assertEquals(original.devices(), result.devices());
        assertEquals(original.positionCalibrations(), result.positionCalibrations());
    }

    @Test
    void editedEventSavesAndOthersUnchanged() {
        HapticConfig original = sample();
        CustomizationModel model = new CustomizationModel(original);

        model.events.get(GameEventKind.ATTACK).enabled = false;
        model.events.get(GameEventKind.ATTACK).multiplier = 2.5;

        HapticConfig result = model.toConfig();

        EventSetting attack = result.events().get(GameEventKind.ATTACK.configKey());
        assertFalse(attack.enabled());
        assertEquals(2.5, attack.multiplier(), 1e-9);

        // An event never touched by the test still matches the original.
        assertEquals(original.events().get(GameEventKind.HURT.configKey()),
                result.events().get(GameEventKind.HURT.configKey()));
    }

    @Test
    void editedOutputPolicySaves() {
        HapticConfig original = sample();
        CustomizationModel model = new CustomizationModel(original);

        model.outputPolicy.put(OutputKind.ROTATE, false);

        HapticConfig result = model.toConfig();

        assertFalse(result.outputPolicy().get(OutputKind.ROTATE.wireName()).enabled());
        assertTrue(result.outputPolicy().get(OutputKind.VIBRATE.wireName()).enabled());
    }

    @Test
    void editedReconnectSaves() {
        CustomizationModel model = new CustomizationModel(sample());

        model.reconnectEnabled = false;
        model.reconnectMaxDelaySeconds = 90;

        HapticConfig result = model.toConfig();

        assertFalse(result.buttplug().reconnect().enabled());
        assertEquals(90, result.buttplug().reconnect().maxDelaySeconds());
        // Buttplug's other fields must be untouched.
        assertEquals(sample().buttplug().serverUrl(), result.buttplug().serverUrl());
    }

    @Test
    void editedAccumulationAndCustomIntensitySave() {
        CustomizationModel model = new CustomizationModel(sample());

        model.accumulationCapacity = 200.0;
        model.cycleAccumulationCurve();
        model.customAttack = 0.33;

        HapticConfig result = model.toConfig();

        AccumulationParams accumulation = result.accumulation();
        assertEquals(200.0, accumulation.capacity(), 1e-9);
        assertEquals("linear", accumulation.outputCurve());

        CustomIntensities custom = result.customIntensity();
        assertEquals(0.33, custom.attack(), 1e-9);
    }
}
