package net.minegasm.buttplug;

import net.minegasm.device.HapticDevice;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Parsed inbound Buttplug messages, reduced to an immutable hierarchy on the transport thread before
 * being handed to the provider (brief §6.5). Unknown message types are represented rather than
 * dropped, so they can be logged once and ignored safely.
 */
public interface ServerMessage {

    /** Every correlated response carries the request id it answers (0 for unsolicited). */
    long id();

    final class ServerInfo implements ServerMessage {
        private final long id;
        private final String serverName;
        private final int majorVersion;
        private final int minorVersion;
        private final long maxPingTimeMs;

        public ServerInfo(long id, String serverName, int majorVersion, int minorVersion,
                          long maxPingTimeMs) {
            this.id = id;
            this.serverName = serverName;
            this.majorVersion = majorVersion;
            this.minorVersion = minorVersion;
            this.maxPingTimeMs = maxPingTimeMs;
        }

        @Override
        public long id() {
            return id;
        }

        public String serverName() {
            return serverName;
        }

        public int majorVersion() {
            return majorVersion;
        }

        public int minorVersion() {
            return minorVersion;
        }

        public long maxPingTimeMs() {
            return maxPingTimeMs;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof ServerInfo)) {
                return false;
            }
            ServerInfo other = (ServerInfo) o;
            return id == other.id && majorVersion == other.majorVersion
                    && minorVersion == other.minorVersion && maxPingTimeMs == other.maxPingTimeMs
                    && Objects.equals(serverName, other.serverName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, serverName, majorVersion, minorVersion, maxPingTimeMs);
        }

        @Override
        public String toString() {
            return "ServerInfo[id=" + id + ", serverName=" + serverName + ", majorVersion="
                    + majorVersion + ", minorVersion=" + minorVersion + ", maxPingTimeMs="
                    + maxPingTimeMs + "]";
        }
    }

    final class Ok implements ServerMessage {
        private final long id;

        public Ok(long id) {
            this.id = id;
        }

        @Override
        public long id() {
            return id;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Ok)) {
                return false;
            }
            return id == ((Ok) o).id;
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }

        @Override
        public String toString() {
            return "Ok[id=" + id + "]";
        }
    }

    final class Error implements ServerMessage {
        private final long id;
        private final String errorMessage;
        private final int errorCode;

        public Error(long id, String errorMessage, int errorCode) {
            this.id = id;
            this.errorMessage = errorMessage;
            this.errorCode = errorCode;
        }

        @Override
        public long id() {
            return id;
        }

        public String errorMessage() {
            return errorMessage;
        }

        public int errorCode() {
            return errorCode;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Error)) {
                return false;
            }
            Error other = (Error) o;
            return id == other.id && errorCode == other.errorCode
                    && Objects.equals(errorMessage, other.errorMessage);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, errorMessage, errorCode);
        }

        @Override
        public String toString() {
            return "Error[id=" + id + ", errorMessage=" + errorMessage + ", errorCode=" + errorCode
                    + "]";
        }
    }

    /**
     * A complete device snapshot. Devices are already normalized into {@link HapticDevice} with a
     * placeholder generation of 0; the {@link DeviceRegistry} stamps the real generation on accept.
     */
    final class DeviceList implements ServerMessage {
        private final long id;
        private final List<HapticDevice> devices;

        public DeviceList(long id, List<HapticDevice> devices) {
            this.id = id;
            this.devices = devices == null
                    ? Collections.emptyList()
                    : Collections.unmodifiableList(new ArrayList<>(devices));
        }

        @Override
        public long id() {
            return id;
        }

        public List<HapticDevice> devices() {
            return devices;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof DeviceList)) {
                return false;
            }
            DeviceList other = (DeviceList) o;
            return id == other.id && Objects.equals(devices, other.devices);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, devices);
        }

        @Override
        public String toString() {
            return "DeviceList[id=" + id + ", devices=" + devices + "]";
        }
    }

    final class ScanningFinished implements ServerMessage {
        private final long id;

        public ScanningFinished(long id) {
            this.id = id;
        }

        @Override
        public long id() {
            return id;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof ScanningFinished)) {
                return false;
            }
            return id == ((ScanningFinished) o).id;
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }

        @Override
        public String toString() {
            return "ScanningFinished[id=" + id + "]";
        }
    }

    final class Unknown implements ServerMessage {
        private final long id;
        private final String name;

        public Unknown(long id, String name) {
            this.id = id;
            this.name = name;
        }

        @Override
        public long id() {
            return id;
        }

        public String name() {
            return name;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Unknown)) {
                return false;
            }
            Unknown other = (Unknown) o;
            return id == other.id && Objects.equals(name, other.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, name);
        }

        @Override
        public String toString() {
            return "Unknown[id=" + id + ", name=" + name + "]";
        }
    }
}
