# Feelcraft Haptics MVP Design

## 1. Context

The MVP implements Minecraft haptic feedback as a client-side NeoForge mod using Stonecraft for a multi-version workspace and Buttplug/Intiface as the only device provider. The long-term architecture should support multiple device protocols, but the MVP intentionally limits external integration to Buttplug v4.

Stonecraft is used because it wires Architectury and Stonecutter for multi-loader and multi-version Minecraft workspaces. In this MVP only NeoForge targets are enabled, but the project layout does not prevent adding Fabric or other providers later.

## 2. Version target

The MVP targets the two current 2026 Minecraft lines:

- `26.2-neoforge`
- `26.1.2-neoforge`

`26.2` is treated as the newest line, and `26.1.2` as the latest patch line from the previous major 2026 release line. The version properties live in `versions/dependencies/`.

## 3. Core principle

Minecraft events should never directly produce device commands.

Instead:

```text
Minecraft sampled state / event
-> GameHapticEvent
-> HapticIntent
-> HapticScene
-> HapticLayer
-> HapticPrimitive
-> feature-compatible HapticCommand
-> Buttplug OutputCmd
```

This keeps the game logic independent from the device protocol.

## 4. Why Buttplug-only for MVP

Buttplug v4 is already a feature-based device protocol. Its `DeviceList` reports devices as indexes with `DeviceFeatures`; each feature can expose output verbs such as `Vibrate`, `Rotate`, `Oscillate`, `Constrict`, `Temperature`, `Led`, `Position`, and `HwPositionWithDuration`. This maps naturally to the internal `HapticDevice`, `HapticFeature`, and `HapticOutputCapability` records.

The MVP uses these Buttplug messages:

- `RequestServerInfo`
- `RequestDeviceList`
- `StartScanning`
- `Ping`
- `OutputCmd`
- `StopCmd`
- `Disconnect`

`StopCmd` is treated as mandatory and bypasses queues. Because most Buttplug outputs such as `Vibrate`, `Rotate`, and `Oscillate` do not carry duration fields in v4, the renderer also schedules a matching zero-value stop command after each pulse duration.

## 5. Tick model

Minecraft runs game logic in ticks, but haptic playback should use real monotonic time. The MVP therefore separates tick sampling from command dispatch.

```text
Minecraft client tick end
-> drain TickEventBuffer
-> aggregate events
-> resolve scenes
-> render commands
-> enqueue per-device scheduler

Haptic worker every ~10 ms
-> poll due commands
-> send Buttplug OutputCmd
```

Ticks are good for ordering and sampling game state. Real time is used for command duration, expiry, cooldown, and device message timing gaps.

## 6. Event buffer

A simple FIFO queue is not enough. Haptics must be fresh and prioritized.

The MVP uses:

- a bounded per-tick raw event buffer
- an aggregator that merges repeated damage and keeps only the latest mining tick
- a scene mixer that lets high-priority damage suppress lower-priority texture effects
- a per-device scheduler that respects Buttplug timing gaps and command expiry

## 7. MVP scenes

### Player hurt

Detected from health delta.

```text
GameHapticEvent.PlayerDamaged
-> HapticIntent.PlayerHurt
-> HapticScene player_hurt
-> Impulse layer
```

Rendering:

- `Vibrate`: short thump
- `Oscillate`: brief medium pulse
- `HwPositionWithDuration`: push and return to neutral

### Mining

Detected from attack key + block hit result.

```text
GameHapticEvent.MiningTick
-> HapticIntent.MiningTexture
-> HapticScene mining
-> Texture layer
```

Rendering:

- `Vibrate`: low texture pulse, latest wins
- `HwPositionWithDuration`/`Position`: tiny movement near neutral

Mining updates are capped by scheduler timing and expiry so the device does not lag behind.

### Block break

Approximated when the previously targeted mined block becomes air.

```text
GameHapticEvent.BlockBroken
-> HapticIntent.BlockBreak
-> short reward pop
```

### Low health

Detected from current health fraction <= 30%.

```text
GameHapticEvent.LowHealth
-> HapticIntent.LowHealthWarning
-> heartbeat pattern
```

## 8. Buttplug provider

The provider owns:

- WebSocket connection
- protocol handshake
- ping watchdog
- scanning
- device-list parsing
- output command serialization
- universal stop

The core engine only sees normalized devices and commands.

## 9. Internal device model

```java
record HapticDevice(int deviceIndex, String name, String displayName, int timingGapMs, List<HapticFeature> features) {}
record HapticFeature(int featureIndex, String description, List<HapticOutputCapability> outputs) {}
record HapticOutputCapability(OutputKind kind, int minValue, int maxValue, Integer minDurationMs, Integer maxDurationMs) {}
```

This intentionally mirrors Buttplug v4 while still giving you an abstraction that other providers can later map into.

## 10. Routing and rendering

A `HapticLayer` declares output kinds it can use:

```java
Set.of(OutputKind.VIBRATE, OutputKind.HW_POSITION_WITH_DURATION, OutputKind.OSCILLATE)
```

The renderer then chooses compatible features from the current Buttplug `DeviceList`.

Examples:

| Primitive | Buttplug output | Rendering |
|---|---|---|
| Impulse | Vibrate | short pulse |
| Impulse | HwPositionWithDuration | push + neutral return |
| Texture | Vibrate | subtle texture pulse |
| Texture | Position | small offset around neutral |
| Pattern | Vibrate | timed beats |

## 11. Safety and comfort

Defaults are conservative:

- no footsteps by default
- no ambient by default
- no constrict by default
- no temperature by default
- rotation capped low
- oscillation capped medium
- linear position commands are limited to small amplitudes

Every scene has expiry so old commands are dropped. `StopAll` is sent when leaving a world, logging out, disabling haptics, or when the sampler sees no player/world.

## 12. Deferred work

After MVP:

1. proper config screen
2. manual connect/scan/test buttons
3. reliable explosion event integration
4. per-device calibration profiles
5. battery/RSSI inputs
6. provider interface for SDL, HID, BLE, OpenXR, and mobile companion apps
7. richer material taxonomy
8. per-server or per-world profiles

## 13. References

- Stonecraft docs: https://stonecraft.meza.gg/docs
- Stonecraft quickstart: https://stonecraft.meza.gg/docs/Quickstart
- Buttplug v4 output spec: https://buttplug.io/docs/spec/output/
- Buttplug v4 device information: https://buttplug.io/docs/spec/device_information/
- Buttplug v4 stop messages: https://buttplug.io/docs/spec/stop/
- NeoForge versioning docs: https://docs.neoforged.net/docs/gettingstarted/versioning/
