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
  physical range, outside the calibrated window. Only vibration-like outputs (Vibrate, Oscillate,
  Rotate, Constrict) get planned zeroes; position outputs hold where the envelope ended
  (`FeatureScheduler.needsZeroOnRelease`), and `StopCmd` covers emergencies.
- **Bounded queues + real-time expiry**: no stale command is ever sent; nothing grows unbounded.
- **No reassertion after stop**: local state is cleared atomically with the stop.

## Output caps and gating

- Hard per-kind caps in `render.SafetyCaps` (Vibrate ≤ 1.0, Oscillate ≤ 0.5, Rotate ≤ 0.35, motion
  travel ≤ 0.20) applied after all user scaling.
- Per-device and per-feature caps/multipliers, plus global intensity.
- **Experimental gating**: `Position`/`HwPositionWithDuration` require explicit opt-in *and*
  per-feature calibration before gameplay can move them (`SceneMixer.buildTarget`,
  `config.PositionCalibration`). `Spray` is permanently unsupported and can never be routed.
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
