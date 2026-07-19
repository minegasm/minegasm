# Feelcraft Haptics MVP

Client-side Minecraft haptic feedback MVP using Stonecraft, NeoForge, and the Buttplug v4 protocol.

This package now includes a **Minegasm replacement/compatibility layer**. Minegasm is used only as product inspiration: Feelcraft keeps the public behavior surface - Intiface connection, mode presets, and event intensities - but implements it with a new semantic haptic scene architecture.

## Scope

- Loader: NeoForge only.
- Minecraft targets: `26.2` and `26.1.2`.
- Device protocol: Buttplug v4 over WebSocket, intended for Intiface Central on `ws://127.0.0.1:12345/buttplug`.
- Stonecraft workspace structure for multi-version builds.

## Minegasm-compatible features included

- Buttplug / Intiface server URL config.
- Global haptics enable/disable.
- Mode names: `NORMAL`, `MASOCHIST`, `HEDONIST`, `CUSTOM`.
- Event intensities:
  - attack
  - hurt
  - mine
  - XP gain/change
  - harvest
  - vitality
- Client-side multiplayer support by sampling local player state.
- v0.1 behavior can be emulated through `CUSTOM` mode with only `hurtIntensity = 1.0`.

## Feelcraft extensions beyond Minegasm

- Feature-based Buttplug mapping instead of simple vibration-only assumptions.
- Supports `Vibrate`, `Rotate`, `Oscillate`, `Position`, and `HwPositionWithDuration` outputs in the MVP.
- Per-tick aggregation, priority, coalescing, expiry, and timing-gap scheduling.
- Mandatory `StopAll` on pause, logout, no-world state, and disconnect.
- Conservative caps for motion-like outputs.

## How to use

1. Install and open Intiface Central.
2. Start the Buttplug server. Use `ws://127.0.0.1:12345/buttplug` unless you changed the port.
3. Open the project in IntelliJ IDEA.
4. Use a current Gradle distribution.
5. Build the active target:

```bash
gradle build
```

6. Change active Stonecutter target if needed:

```bash
gradle chiseledBuild
```

Stonecraft and Stonecutter task names can change across plugin versions. If the wrapper task is not available, use Stonecutter's native fan-out tasks as described in the Stonecraft docs.

## Project layout

```text
src/main/java/gg/meza/feelcraft/
  neoforge/     NeoForge event adapter and Minecraft state sampler
  core/         raw Minecraft haptic events and material classification
  haptics/      semantic haptic intent/scene/layer/primitive model
  engine/       tick buffer, aggregator, scene resolver, mixer, scheduler, renderer
  device/       internal normalized device/feature/command model
  buttplug/     Buttplug v4 WebSocket protocol provider
  config/       MVP config defaults and Minegasm-compatible modes
  runtime/      bootstrap and worker loop
versions/
  dependencies/ dependency versions per Minecraft target
  26.2-neoforge/ NeoForge metadata/resources
  26.1.2-neoforge/ NeoForge metadata/resources
docs/
  DESIGN.md
  MINEGASM_REPLACEMENT.md
  BUILD_NOTES.md
  SAFETY.md
```

## Important MVP caveat

This is a source implementation scaffold generated without running a full Gradle dependency resolution in the sandbox. The architecture, package layout, and Buttplug v4 message shapes are concrete, but you may need to adjust Stonecraft, NeoForge, or Minecraft mappings if their APIs changed after this pack was generated.

## Safety defaults

The MVP uses conservative defaults:

- global intensity: `0.75`
- vibration cap: `0.80`
- oscillation cap: `0.50`
- rotation cap: `0.35`
- linear-position amplitude cap: `0.22`
- constrict/temperature: disabled
- footsteps/ambient: disabled

Use `StopAll` on pause, disconnect, logout, no-world state, and config reload.
