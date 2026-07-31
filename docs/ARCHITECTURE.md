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
Backends               (net.minegasm.backend)
  BackendCoordinator fans each HapticScene to every enabled HapticBackend:
    ├─ ButtplugBackend  (wraps the worker: bounded SceneIngressQueue, monotonic HapticWorker)
    │     SceneMixer → EndpointTarget → FeatureScheduler → OutputCommand   (net.minegasm.runtime/render)
    │       → ButtplugProvider → ButtplugCodec → ButtplugTransport → Intiface → devices
    └─ BridgeBackend    (net.minegasm.bridge)
          BridgeCodec → OutboundQueue → BridgeTransport (TCP) → local adapter
```

## Threading (brief §6)

- **Client thread** only samples state, builds immutable objects, and `offer`s scenes to a bounded
  queue. It never blocks on I/O.
- **Haptic worker** (single thread, ~15 ms cadence) owns all mixer/scheduler state and dispatches
  commands. All durations/expiry/cooldowns use `System.nanoTime()` via the `Clock` abstraction, so
  behaviour is identical under tick-rate changes or stalls.
- **Provider thread(s)** parse protocol frames into immutable messages; they never call Minecraft.

## Key invariants

- **Registry generations**: every `DeviceList` increments a generation; a command captured against an
  old generation is dropped (`DeviceRegistrySnapshot.resolve`, `FeatureScheduler`). A reused device
  index in a new list is a new logical device.
- **Held endpoints need a stop**: vibration holds its level until changed, so the scheduler emits an
  explicit zero when a gesture ends (`FeatureScheduler.accept`).
- **Stop wins**: `HapticWorker.requestStop` clears local state *and* sends `StopCmd`, so a delayed
  cycle cannot reassert output.
- **Bounded everything**: tick buffer (128), scene ingress (64), and per-feature pending state all have
  documented overflow policies.

## Where behaviour lives (data, not code branches)

- Priorities: `core/Priorities`. Per-event priority + expiry: `recipe/RecipeTiming`.
- Mode intensities: `recipe/Presets` (legacy parity table).
- Recipes: `recipe/ClassicRecipePack` (flat plateau parity), `recipe/BalancedRecipePack` (shaped), and
  `recipe/FileRecipePack` (a loaded `pack/ScenePack`, selected by id, ADR-017).
- Output caps: `render/SafetyCaps`. Fatigue budgets: `runtime/FatigueGovernor`.

## Extension seams

- `HapticBackend`: add an output backend (the Buttplug worker, the bridge, a future one) behind the
  `BackendCoordinator`; scenes fan out to every enabled backend (brief 0003 §3.2, ADR-018).
- `BridgeTransport`: swap the bridge's transport (TCP by default, a future WebSocket/OSC) without
  touching the backend or codec.
- `ButtplugTransport`: swap the JDK WebSocket for a client library without touching the engine.
- `RecipePack`: add built-in packs, or load shareable scene packs from disk (`pack/PackLoader`),
  without changing acquisition or scheduling.
- The Minecraft sampler: the only class that changes between Minecraft versions (Stonecutter guards).
