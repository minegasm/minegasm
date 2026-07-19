# Appendix C - Buttplug v4 mapping and provider checklist

## Required protocol messages

### Client -> server

- `RequestServerInfo`
- `Ping` when `MaxPingTime` is nonzero
- `RequestDeviceList`
- `StartScanning`
- `StopScanning`
- `OutputCmd`
- `StopCmd`
- `Disconnect`
- Optional later: `InputCmd` for battery/RSSI

### Server -> client

- `ServerInfo`
- `Ok`
- `Error`
- `DeviceList`
- `ScanningFinished`
- Optional later: `InputReading`

## Normalization rules

1. Treat every DeviceList as the complete truth.
2. Increment registry generation on each accepted list.
3. Discard queued output if generation differs.
4. Validate output value/duration ranges as inclusive.
5. Store all advertised output types, including unknown strings.
6. Render only explicitly supported and enabled types.
7. A feature can expose multiple output contexts; choose the renderer by scene and policy.
8. One OutputCmd targets one device feature.
9. Track every request ID and timeout.
10. StopCmd has a separate immediate lane.

## Renderer score guidance

| Primitive | Vibrate | HwPositionWithDuration | Position | Oscillate | Rotate | Constrict |
|---|---:|---:|---:|---:|---:|---:|
| Impulse | 1.0 | 0.9 | 0.6 | 0.3 | 0.1 | 0.2 |
| Texture | 0.9 | 0.6 | 0.5 | 0.5 | 0.1 | 0.1 |
| Rumble | 1.0 | 0.5 | 0.3 | 0.7 | 0.2 | 0.1 |
| Sweep | 0.6 | 1.0 | 0.7 | 0.4 | 0.3 | 0.4 |
| BeatPattern | 1.0 | 0.7 | 0.4 | 0.3 | 0.1 | 0.3 |

Scores express fidelity only. Policy, user opt-in, calibration, safety, and device enablement can veto a renderer.

## Provider implementation checklist

- [ ] Configurable WebSocket URL
- [ ] JDK WebSocket or evaluated v4 library behind transport interface
- [ ] Protocol major/minor negotiation
- [ ] Ping scheduler with safety margin
- [ ] Maximum frame/message size
- [ ] Request ID correlation and timeouts
- [ ] Full DeviceList parse and validation
- [ ] Atomic immutable registry snapshot
- [ ] Registry generation invalidation
- [ ] Scan controls
- [ ] Feature-level OutputCmd
- [ ] Stop all/device/feature selections
- [ ] Graceful Disconnect
- [ ] Unexpected disconnect safe stop/clear
- [ ] Backoff reconnect without scene replay
- [ ] Redacted logs
- [ ] Fake server tests

## Endpoint state warning

Most output levels remain set until changed. The application must schedule zero/neutral transitions. Do not assume a duration for `Vibrate`, `Rotate`, `Oscillate`, or similar level commands. `HwPositionWithDuration` contains a duration for movement, but the endpoint may remain at the resulting position afterward.
