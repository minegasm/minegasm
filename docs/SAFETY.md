# Safety, privacy, and security

This mod controls physical hardware, so every design choice biases toward **stopped output** on any
uncertainty (brief §12).

## Fail-stopped controls

- **Master enable defaults off** until setup completes (`HapticConfig.Global.enabled = false`).
- Fresh installs default local Intiface auto-connect and auto-scan on, but discovery cannot emit
  gameplay output while the master enable remains off. A first-run toast explains the opt-in.
- **Universal stop** (`HapticWorker.requestStop` → `StopCmd`, which bypasses the timing gap) fires on:
  configured pause/world-unload transitions, disconnect, shutdown, config reset, panic, transport error, registry
  invalidation, watchdog, and calibration cancel (`runtime.StopReason`, `LifecycleController`).
- **Pause policy** is explicit: `STOP` clears active state; `PAUSE` sends `StopCmd`, freezes scene and
  fatigue deadlines, and resumes only against the same device-registry generation; `CONTINUE` does
  not issue a pause-triggered command. World unload or any safety stop discards preserved pause state.
- **Panic key** (unbound by default; assign in Controls): highest priority, disables output and
  stops immediately, in-world or in menus.
- **Watchdog** (`runtime.Watchdog`): polled from the client tick, an observer independent of the
  worker thread it watches, and forces a stop if the worker's last healthy cycle is stale (>2 s).
- **Position endpoints are never zeroed on release**: a raw 0 would slam a stroker to the end of its
  physical range, outside its travel window. Only vibration-like outputs (Vibrate, Oscillate,
  Rotate, Constrict) get planned zeroes; position outputs hold where the envelope ended
  (`FeatureScheduler.needsZeroOnRelease`), and `StopCmd` covers emergencies.
- **Bounded queues + real-time expiry**: no stale command is ever sent; nothing grows unbounded.
- **No reassertion after stop**: local state is cleared atomically with the stop.

## Output caps and gating

- Hard per-kind caps in `render.SafetyCaps` (Vibrate ≤ 1.0, Oscillate ≤ 0.5, Rotate ≤ 0.35) applied
  after all user scaling. Motion is not capped here; its bound is the travel window below.
- Per-device and per-feature caps/multipliers, plus global intensity.
- **Bounded motion by default**: `Position`/`HwPositionWithDuration` (strokers) move within a
  conservative safe default (centered neutral, narrow window) even with no calibration; an explicit
  `config.PositionCalibration` can reshape it. Physical travel is bounded in `SceneMixer.buildTarget` by
  the calibration's `gameplayTravelFraction` (≤ 0.20 of the span) and the `[minimum, maximum]` clamp, so
  no config or shared profile can slam the device to its ends. `Spray` is unsupported and can never be
  routed (it maps to `OutputKind.UNKNOWN`).
- **Rhythmic stroking** (Balanced pack): gameplay activity drives a decaying charge that strokes the
  device back and forth, fading when idle. The stroke period is floored (`SceneMixer.MIN_STROKE_PERIOD_MS`),
  which caps how fast the device can be driven; depth stays within the travel window above.
- **Fatigue protection** (default on): rolling budgets reduce low-priority texture/ambient output
  before ever dulling warnings (`runtime.FatigueGovernor`).

## Network

- **Loopback by default** (`ws://127.0.0.1:12345`). `MinegasmClient.connect` refuses non-loopback
  URLs unless the user explicitly enables remote servers.
- Inbound frame size is capped (`WebSocketTransport`), malformed frames/ranges are rejected, unknown
  output types are represented but never executed, and there is no inbound listening server.

## Privacy

- No telemetry, accounts, or analytics.
- Server URL host is logged without credentials/query; device names are not required in logs.
- Config may contain intimate device info; it is stored locally and never uploaded.
