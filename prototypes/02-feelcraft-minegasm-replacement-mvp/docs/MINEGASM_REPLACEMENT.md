# Feelcraft as a Minegasm Replacement - MVP Notes

## Goal

This MVP treats Minegasm as product inspiration and implements a compatibility layer for the public Minegasm user-facing behavior while keeping the new Feelcraft architecture:

```text
Minecraft client events
-> tick buffer
-> semantic haptic intents
-> haptic scenes
-> Buttplug feature renderer
-> per-device scheduler
-> Buttplug OutputCmd / StopCmd
```

The implementation is not a code copy of Minegasm. It is a new architecture with a Minegasm-compatible event and preset surface. This matters because the Minegasm repository is AGPL-3.0 licensed; avoid copying source unless you are prepared for AGPL obligations in your own distribution.

## What Minegasm exposes publicly

From the public Minegasm docs, the important replacement surface is:

- Connect to Intiface Central / Buttplug server.
- Configure `serverUrl`, historically `ws://localhost:12345/buttplug` or `ws://127.0.0.1:12345/buttplug`.
- Enable or disable vibration globally.
- Choose mode: `NORMAL`, `MASOCHIST`, `HEDONIST`, or `CUSTOM`.
- Configure event intensities for:
  - attack
  - hurt
  - mine
  - XP gain/change
  - harvest
  - vitality
- Work in multiplayer from the local client perspective.

## Compatibility mode behavior

Feelcraft keeps the same mode names and default percentages:

| Mode | Attack | Hurt | Mine | XP | Harvest | Vitality |
|---|---:|---:|---:|---:|---:|---:|
| `NORMAL` | 60% | Off | 80% | 100% | Off | Off |
| `MASOCHIST` | Off | 100% | Off | Off | Off | 10% |
| `HEDONIST` | 60% | 10% | 80% | 100% | 20% | 10% |
| `CUSTOM` | custom | custom | custom | custom | custom | custom |

Feelcraft then maps those mode intensities into semantic haptic scenes rather than blindly sending one vibration command.

## Event mapping

| Minegasm event | Feelcraft source | Feelcraft intent | Default feel |
|---|---|---|---|
| Attack | rising edge of attack key while targeting entity | `PlayerAttack` | short snap/thump |
| Hurt | client health delta | `PlayerHurt` | stronger impact pulse |
| Mine | attack key held on block | `MiningTexture` | texture pulse, coalesced |
| XP | total XP increases | `XpReward` | sparkle pattern |
| Harvest | crop/plant-like block broken | `HarvestReward` | soft reward pop |
| Vitality | high health+food or very low health, throttled | `VitalityPulse` | gentle pulse |

Feelcraft also keeps two richer events that go beyond Minegasm:

| Feelcraft event | Reason |
|---|---|
| `BlockBreak` | satisfying completion pop when a targeted block turns to air |
| `LowHealthWarning` | clearer danger heartbeat independent from the Minegasm vitality setting |

## Why this can replace Minegasm instead of only imitate it

Minegasm's public behavior is essentially event-to-vibration presets. Feelcraft keeps that surface but adds:

- feature-based Buttplug device mapping
- support for vibration, rotation, oscillation, position, and hardware-position-with-duration outputs
- tick aggregation instead of direct event spam
- command expiry and priority
- per-device timing gap handling
- universal `StopAll` on pause/logout/no-world/disconnect
- safer defaults for motion-like outputs
- a path to non-Buttplug providers later

## Multiplayer behavior

The MVP remains client-only. It samples the local player's client-visible state, so it can work on singleplayer and multiplayer servers without requiring a server-side mod. This is the simplest way to cover Minegasm's multiplayer feature from a user perspective.

Limitations:

- It cannot reliably know server-side-only causes unless reflected in client state.
- Harvest detection is approximate and name/tag based.
- Block break is approximated by watching the targeted block turn to air.
- Attack is detected on key press and entity target, not server-confirmed damage dealt.

These are acceptable for MVP because the goal is pleasant haptic feedback, not authoritative combat analytics.

## Buttplug output strategy

| Buttplug output | MVP use |
|---|---|
| `Vibrate` | primary replacement for Minegasm behavior |
| `Rotate` | optional low cap; used like a gentle pulse if available |
| `Oscillate` | optional medium cap; useful for rhythmic devices |
| `Position` | small texture movements around neutral |
| `HwPositionWithDuration` | impulse push/return and mining micro-motion |
| `Constrict` | disabled by default |
| `Temperature` | disabled by default |
| `Led` | debug/status only |

## Safety and comfort defaults

- Global intensity: 75%.
- Vibration cap: 80%.
- Rotation cap: 35%.
- Oscillation cap: 50%.
- Linear amplitude cap: 22% around neutral.
- Constrict and temperature outputs are disabled by default.
- Footsteps and ambience are disabled by default.
- Stop all devices on pause, logout, no-world state, and runtime stop.

## Migration guide from `minegasm-client.toml`

Minegasm setting:

```toml
[buttplug]
serverUrl = "ws://localhost:12345/buttplug"

[minegasm]
vibrate = true
mode = "NORMAL"

[minegasm.intensity]
attackIntensity = 0.6
hurtIntensity = 1.0
mineIntensity = 0.8
xpChangeIntensity = 1.0
harvestIntensity = 0.2
vitalityIntensity = 0.1
```

Feelcraft equivalent:

```json
{
  "enabled": true,
  "buttplugUri": "ws://127.0.0.1:12345/buttplug",
  "minegasmCompatibility": true,
  "mode": "NORMAL",
  "customIntensity": {
    "attackIntensity": 0.60,
    "hurtIntensity": 1.00,
    "mineIntensity": 0.80,
    "xpChangeIntensity": 1.00,
    "harvestIntensity": 0.20,
    "vitalityIntensity": 0.10
  }
}
```

For exact Minegasm v0.1 behavior, use `CUSTOM` with:

```json
{
  "mode": "CUSTOM",
  "customIntensity": {
    "attackIntensity": 0.0,
    "hurtIntensity": 1.0,
    "mineIntensity": 0.0,
    "xpChangeIntensity": 0.0,
    "harvestIntensity": 0.0,
    "vitalityIntensity": 0.0
  }
}
```

## Files added/changed for compatibility

```text
src/main/java/gg/meza/feelcraft/config/MinegasmMode.java
src/main/java/gg/meza/feelcraft/config/LegacyEventType.java
src/main/java/gg/meza/feelcraft/config/HapticConfig.java
src/main/java/gg/meza/feelcraft/core/GameHapticEvent.java
src/main/java/gg/meza/feelcraft/haptics/HapticIntent.java
src/main/java/gg/meza/feelcraft/engine/HapticAggregator.java
src/main/java/gg/meza/feelcraft/engine/SceneResolver.java
src/main/java/gg/meza/feelcraft/neoforge/MinecraftSampler.java
src/main/resources/feelcraft-defaults.json
docs/MINEGASM_REPLACEMENT.md
```

## MVP gaps to close before public release

1. Replace hardcoded defaults with a real NeoForge client config file.
2. Add Mod Menu/config UI or NeoForge config screen if desired.
3. Add explicit connect/disconnect/test buttons.
4. Add user-visible device list and per-device enablement.
5. Validate mapped Buttplug message shapes against the Java client library or your own WebSocket JSON implementation.
6. Run real tests with Intiface Central and at least one device simulator.
7. Add automated test scenes for every Minegasm-compatible event.
