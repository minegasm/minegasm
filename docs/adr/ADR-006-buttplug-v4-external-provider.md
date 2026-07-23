# ADR-006: Buttplug v4 external-server provider (buttplug4j primary, native fallback)

**Status:** accepted. Library spike complete.

**Decision.** Devices are driven only through Buttplug v4 to a separately running Intiface server. No
Bluetooth/HID/vendor SDK and no embedded device server. The engine depends on a `HapticProvider`
interface; two implementations exist:

- **`Buttplug4jProvider`** (default) wraps [buttplug4j](https://github.com/blackspherefollower/buttplug4j)
  `io.github.blackspherefollower:buttplug4j.connectors.jetty.websocket.client:4.0.278`, whose 4.x line
  implements the v4 feature-based spec (`OutputCmd`, `DeviceFeature`). Output is sent via the feature's
  `run*Float` methods, so buttplug4j owns hardware range scaling. Pulls in Jetty + Jackson.
- **`ButtplugProvider`** (fallback) is a dependency-free raw v4 client over the JDK `WebSocket` + Gson
  (`ButtplugCodec`). It is also the in-process fake-server test backend.

The backend is chosen by config (`buttplug.client` = `buttplug4j` | `native`); `ProviderFactory` builds
the selected one in the NeoForge bootstrap.

**Spike result.** buttplug4j stable was v3 message-based; the current `4.0.278` release is v4
feature-based and was confirmed by compiling `Buttplug4jProvider`/`B4jDeviceMapper` against the real
artifact (device model via `getOutput()`/`ButtplugOutput`, per-feature `run*Float`, `stopAllDevices` /
`sendStopDeviceCmd`, and the `IScanningEvent`/`IDeviceAddedEvent` handlers).

**Consequences.** buttplug4j owns the socket, negotiation, ping, and JSON, reducing our protocol
surface; we keep our own generation-stamped `DeviceRegistry` so the scheduler's staleness gate still
applies. The native provider remains valuable as a zero-dependency fallback and a deterministic test
backend; its `ButtplugCodec` message shapes were validated field-by-field against both the official
buttplug Rust source (`buttplugio/buttplug`) and buttplug4j 4.0.278. That validation caught that
`Devices` and `DeviceFeatures` are index-keyed objects (not arrays) while `Output` is an array of externally-tagged
`{Kind:{Value:[min,max],Duration:[..]}}` descriptors. A live Intiface run remains the final
confirmation.
