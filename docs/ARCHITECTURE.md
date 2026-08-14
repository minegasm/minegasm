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
Central governance     (net.minegasm.runtime, ADR-020)
  SceneGovernor: SceneStore (hold, coalesce latest-wins, expiry, bounded) + FatigueGovernor
                 (active timing, output-class competition, post-resolution regional fatigue)
                 → GovernedOutput {active scenes, ResolvedDestinationSnapshot}
              ▼
Driver + backends      (net.minegasm.runtime, net.minegasm.backend, net.minegasm.bridge)
  HapticWorker (neutral driver) advances resolve() once per ~15 ms cycle and fans one GovernedOutput to
  every backend via BackendCoordinator; it renders nothing itself:
    ├─ ButtplugBackend  (rendering) scenes → SceneMixer → FeatureScheduler → OutputCommand
    │       → ButtplugProvider → ButtplugCodec → ButtplugTransport → Intiface → devices
    ├─ (a future native integration implements the same seam and runs alongside)
    └─ BridgeBackend    (semantic) destination snapshot → BridgeDestinationForwarder (change-driven)
          → BridgeCodec v2 → OutboundQueue → BridgeTransport (TCP) → adapter

  BackendOutcomeTracker: accepted → delivered | failed | timed out | superseded
          → BackendCoordinator quarantine + OutputViewState
```

## Threading (brief §6)

- **Client thread** only samples state, builds immutable objects, and submits scenes to the
  `SceneGovernor` (a small monitor guards its bounded store). It never blocks on I/O.
- **Governance driver** (`HapticWorker`, single thread, ~15 ms cadence) advances the governor once per
  cycle and fans the governed set to every backend; each backend renders or forwards it on that thread.
  All durations/expiry/cooldowns use `System.nanoTime()` via the `Clock` abstraction, so behaviour is
  identical under tick-rate changes or stalls.
- **Provider thread(s)** parse protocol frames and complete delivery outcomes; they never call Minecraft.

## Key invariants

- **Registry generations**: every `DeviceList` increments a generation; a command captured against an
  old generation is dropped (`DeviceRegistrySnapshot.resolve`, `FeatureScheduler`). A reused device
  index in a new list is a new logical device.
- **Held endpoints need a stop**: vibration holds its level until changed, so the scheduler emits an
  explicit zero when a gesture ends (`FeatureScheduler.accept`).
- **Stop wins locally before I/O completes**: a stop clears the governor and advances backend generations,
  so stale writes cannot reassert output. Physical stop confirmation is a separate asynchronous outcome.
  A failed or timed-out confirmation remains quarantined and visible.
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

- `HapticBackend`: add an output backend by consuming `onGovernedOutput`. Rendering backends use active
  scenes for physical mapping; semantic backends use the resolved destination snapshot. The driver fans
  the same central result to every backend each cycle, so a native
  integration runs concurrently with Buttplug and the bridge with no privileged path (brief 0003 §3.2,
  ADR-020).
- `BridgeTransport`: swap the bridge's transport (TCP by default, a future WebSocket/OSC) without
  touching the backend or codec.
- `ButtplugTransport`: swap the JDK WebSocket for a client library without touching the engine.
- `RecipePack`: add built-in packs, or load shareable scene packs from disk (`pack/PackLoader`),
  without changing acquisition or scheduling.
- The Minecraft sampler: the only class that changes between Minecraft versions (Stonecutter guards).
