# Architecture

Layers with strict dependency direction. Only the observation layer may touch Minecraft; each output
backend owns its own transport (the Buttplug WebSocket, or the bridge's TCP). The domain, runtime, and
backends are pure and unit-tested.

```
Minecraft client                         (net.minegasm.<loader>)
  └─ sampler ─┐  raw events + ClientStateSnapshot
              ▼
Observation            (net.minegasm.observe)
  TickEventBuffer, StateTracker → StateTransitions, HapticAggregator → HapticIntent
              ▼
Recipe / domain        (net.minegasm.recipe, net.minegasm.core, net.minegasm.pack)
  RecipeEngine + Presets + {Classic,Balanced}RecipePack or a FileRecipePack (shareable scene packs)
      → HapticScene
              ▼
Central governance     (net.minegasm.runtime, ADR-018)
  SceneGovernor: SceneStore (hold, coalesce latest-wins, expiry, bounded) + FatigueGovernor
                 (decay, account, bake per-role attenuation) → governed HapticScene set
              ▼
Backends               (net.minegasm.backend, net.minegasm.bridge)
  BackendCoordinator fans lifecycle (start/stop/pause/close) to every backend; scenes reach each
  backend from the governor, not the coordinator:
    ├─ ButtplugBackend  (lifecycle) → HapticWorker pulls the governed set each ~15 ms cycle
    │     SceneMixer → EndpointTarget → FeatureScheduler → OutputCommand   (net.minegasm.runtime/render)
    │       → ButtplugProvider → ButtplugCodec → ButtplugTransport → Intiface → devices
    └─ BridgeBackend    (net.minegasm.bridge)
          GovernedSceneForwarder (change-driven) → BridgeCodec → OutboundQueue → BridgeTransport (TCP) → adapter
```

## Threading (brief §6)

- **Client thread** only samples state, builds immutable objects, and submits scenes to the
  `SceneGovernor` (a small monitor guards its bounded store). It never blocks on I/O.
- **Haptic worker** (single thread, ~15 ms cadence) pulls the governed scene set from the governor and
  owns the scheduler and dispatch; it also fans the governed set to the bridge change-driven. All
  durations/expiry/cooldowns use `System.nanoTime()` via the `Clock` abstraction, so behaviour is
  identical under tick-rate changes or stalls.
- **Provider thread(s)** parse protocol frames into immutable messages; they never call Minecraft.

## Key invariants

- **Registry generations**: every `DeviceList` increments a generation; a command captured against an
  old generation is dropped (`DeviceRegistrySnapshot.resolve`, `FeatureScheduler`). A reused device
  index in a new list is a new logical device.
- **Held endpoints need a stop**: vibration holds its level until changed, so the scheduler emits an
  explicit zero when a gesture ends (`FeatureScheduler.accept`).
- **Stop wins**: `HapticWorker.requestStop` clears the governor (scenes and fatigue) and the bridge
  forwarder state *and* sends `StopCmd`, all under the worker monitor, so a delayed cycle cannot reassert
  output on either backend.
- **Bounded everything**: tick buffer (128), the governor's discrete scene store (64), and per-feature
  pending state all have documented overflow policies.

## Where behaviour lives (data, not code branches)

- Priorities: `core/Priorities`. Per-event priority + expiry: `recipe/RecipeTiming`.
- Mode intensities: `recipe/Presets` (legacy parity table).
- Recipes: `recipe/ClassicRecipePack` (flat plateau parity), `recipe/BalancedRecipePack` (shaped), and
  `recipe/FileRecipePack` (a loaded `pack/ScenePack`, selected by id, ADR-017).
- Output caps: `render/SafetyCaps`. Fatigue budgets: `runtime/FatigueGovernor`, owned and applied
  centrally by `runtime/SceneGovernor`.

## Extension seams

- `HapticBackend`: add an output backend (the Buttplug worker, the bridge, a future one) behind the
  `BackendCoordinator`, which fans lifecycle to it; scenes reach it from the central `SceneGovernor`,
  already coalesced and fatigue-governed (brief 0003 §3.2, ADR-018).
- `BridgeTransport`: swap the bridge's transport (TCP by default, a future WebSocket/OSC) without
  touching the backend or codec.
- `ButtplugTransport`: swap the JDK WebSocket for a client library without touching the engine.
- `RecipePack`: add built-in packs, or load shareable scene packs from disk (`pack/PackLoader`),
  without changing acquisition or scheduling.
- The Minecraft sampler: the only class that changes between Minecraft versions (Stonecutter guards).
