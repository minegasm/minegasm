package net.minegasm.buttplug;

import net.minegasm.core.OutputKind;
import net.minegasm.device.HapticDevice;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ButtplugCodecTest {

    @Test
    void requestServerInfoNegotiatesV4() {
        String frame = ButtplugCodec.requestServerInfo(1, "Minegasm");
        assertTrue(frame.contains("\"RequestServerInfo\""));
        assertTrue(frame.contains("\"ProtocolVersionMajor\":4"));
        assertTrue(frame.startsWith("[") && frame.endsWith("]"), "wire format is an array");
    }

    @Test
    void outputCmdMatchesExampleShape() {
        var cmd = new OutputCommand(0, 1, OutputKind.VIBRATE, 12, null, 1L);
        String frame = ButtplugCodec.outputCmd(10, cmd);
        assertTrue(frame.contains("\"OutputCmd\""));
        assertTrue(frame.contains("\"DeviceIndex\":0"));
        assertTrue(frame.contains("\"FeatureIndex\":1"));
        assertTrue(frame.contains("\"Vibrate\":{\"Value\":12}"));
    }

    @Test
    void hwPositionCarriesDuration() {
        var cmd = new OutputCommand(1, 0, OutputKind.HW_POSITION_WITH_DURATION, 62, 120, 1L);
        String frame = ButtplugCodec.outputCmd(11, cmd);
        assertTrue(frame.contains("\"HwPositionWithDuration\":{\"Value\":62,\"Duration\":120}"));
    }

    @Test
    void stopAllIsIdOnly() {
        assertTrue(ButtplugCodec.stopAll(20).contains("\"StopCmd\":{\"Id\":20}"));
    }

    @Test
    void scopedStopsCarrySelectionIndexes() {
        String device = ButtplugCodec.stopDevice(21, 3);
        assertTrue(device.contains("\"DeviceIndex\":3"));
        assertFalse(device.contains("FeatureIndex"));
        String feature = ButtplugCodec.stopFeature(22, 3, 1);
        assertTrue(feature.contains("\"DeviceIndex\":3"));
        assertTrue(feature.contains("\"FeatureIndex\":1"));
    }

    @Test
    void parsesServerInfoAndError() {
        List<ServerMessage> a = ButtplugCodec.parse(
                "[{\"ServerInfo\":{\"Id\":1,\"ServerName\":\"x\",\"ProtocolVersionMajor\":4,\"MaxPingTime\":1000}}]");
        assertTrue(a.get(0) instanceof ServerMessage.ServerInfo);
        assertEquals(1000, ((ServerMessage.ServerInfo) a.get(0)).maxPingTimeMs());

        List<ServerMessage> b = ButtplugCodec.parse(
                "[{\"Error\":{\"Id\":5,\"ErrorMessage\":\"boom\",\"ErrorCode\":3}}]");
        assertEquals("boom", ((ServerMessage.Error) b.get(0)).errorMessage());
    }

    @Test
    void parsesDeviceListWithFeatureRanges() {
        // Canonical v4 shape (validated against buttplug Rust + buttplug4j 4.0.278): Devices and
        // DeviceFeatures are index-keyed objects; Output is an array of {Kind:{Value:[min,max]}}.
        String frame = "[{\"DeviceList\":{\"Id\":2,\"Devices\":{\"0\":{"
                + "\"DeviceIndex\":0,\"DeviceName\":\"Toy\",\"DeviceMessageTimingGap\":100,"
                + "\"DeviceFeatures\":{\"0\":{\"FeatureIndex\":0,\"FeatureDescription\":\"m\","
                + "\"Output\":[{\"Vibrate\":{\"Value\":[0,20]}}]}}"
                + "}}}}]";
        ServerMessage.DeviceList list = (ServerMessage.DeviceList) ButtplugCodec.parse(frame).get(0);
        assertEquals(1, list.devices().size());
        HapticDevice d = list.devices().get(0);
        assertEquals("Toy", d.deviceName());
        assertEquals(100, d.messageTimingGapMs());
        assertTrue(d.feature(0).orElseThrow().supports(OutputKind.VIBRATE));
        assertEquals(20, d.feature(0).orElseThrow().output(OutputKind.VIBRATE).orElseThrow()
                .valueRange().max());
    }

    @Test
    void featureIndexAndDurationRangeParsed() {
        // A stroker feature keyed at index 2 with HwPositionWithDuration carrying Value + Duration.
        String frame = "[{\"DeviceList\":{\"Id\":2,\"Devices\":{\"0\":{\"DeviceIndex\":0,"
                + "\"DeviceFeatures\":{\"2\":{\"FeatureIndex\":2,\"FeatureDescription\":\"stroke\","
                + "\"Output\":[{\"HwPositionWithDuration\":{\"Value\":[0,100],\"Duration\":[50,2000]}}]}}}}}}]";
        ServerMessage.DeviceList list = (ServerMessage.DeviceList) ButtplugCodec.parse(frame).get(0);
        HapticDevice d = list.devices().get(0);
        assertTrue(d.feature(2).isPresent(), "feature index comes from the FeatureIndex field / map key");
        var cap = d.feature(2).orElseThrow().output(OutputKind.HW_POSITION_WITH_DURATION).orElseThrow();
        assertEquals(100, cap.valueRange().max());
        assertEquals(2000, cap.durationMs().orElseThrow().max());
    }

    @Test
    void toleratesLegacyArrayShape() {
        // Robustness: an array-shaped Devices/DeviceFeatures (older/hand-written) still parses.
        String frame = "[{\"DeviceList\":{\"Id\":2,\"Devices\":[{\"DeviceIndex\":0,"
                + "\"DeviceFeatures\":[{\"FeatureIndex\":0,\"Output\":[{\"Vibrate\":{\"Value\":[0,20]}}]}]}]}}]";
        ServerMessage.DeviceList list = (ServerMessage.DeviceList) ButtplugCodec.parse(frame).get(0);
        assertTrue(list.devices().get(0).feature(0).orElseThrow().supports(OutputKind.VIBRATE));
    }

    @Test
    void unknownOutputKindPreservedNotDropped() {
        String frame = "[{\"DeviceList\":{\"Id\":2,\"Devices\":{\"0\":{\"DeviceIndex\":0,"
                + "\"DeviceFeatures\":{\"0\":{\"FeatureIndex\":0,\"Output\":[{\"Warp\":{\"Value\":[0,5]}}]}}}}}}]";
        ServerMessage.DeviceList list = (ServerMessage.DeviceList) ButtplugCodec.parse(frame).get(0);
        assertTrue(list.devices().get(0).feature(0).orElseThrow().supports(OutputKind.UNKNOWN));
    }

    @Test
    void malformedRangeSkippedNotThrown() {
        String frame = "[{\"DeviceList\":{\"Id\":2,\"Devices\":{\"0\":{\"DeviceIndex\":0,"
                + "\"DeviceFeatures\":{\"0\":{\"FeatureIndex\":0,\"Output\":[{\"Vibrate\":{\"Value\":[\"bad\"]}}]}}}}}}]";
        ServerMessage.DeviceList list = (ServerMessage.DeviceList) ButtplugCodec.parse(frame).get(0);
        assertFalse(list.devices().get(0).feature(0).orElseThrow().supports(OutputKind.VIBRATE));
    }

    @Test
    void malformedFrameYieldsNoMessages() {
        assertTrue(ButtplugCodec.parse("not json").isEmpty());
        assertTrue(ButtplugCodec.parse("{}").isEmpty());
    }
}
