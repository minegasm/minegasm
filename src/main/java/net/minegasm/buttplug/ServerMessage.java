package net.minegasm.buttplug;

import net.minegasm.device.HapticDevice;

import java.util.List;

/**
 * Parsed inbound Buttplug messages, reduced to an immutable sealed hierarchy on the transport thread
 * before being handed to the provider (brief §6.5). Unknown message types are represented rather than
 * dropped, so they can be logged once and ignored safely.
 */
public sealed interface ServerMessage {

    /** Every correlated response carries the request id it answers (0 for unsolicited). */
    long id();

    record ServerInfo(long id, String serverName, int majorVersion, int minorVersion,
                      long maxPingTimeMs) implements ServerMessage {}

    record Ok(long id) implements ServerMessage {}

    record Error(long id, String errorMessage, int errorCode) implements ServerMessage {}

    /**
     * A complete device snapshot. Devices are already normalized into {@link HapticDevice} with a
     * placeholder generation of 0; the {@link DeviceRegistry} stamps the real generation on accept.
     */
    record DeviceList(long id, List<HapticDevice> devices) implements ServerMessage {}

    record ScanningFinished(long id) implements ServerMessage {}

    record Unknown(long id, String name) implements ServerMessage {}
}
