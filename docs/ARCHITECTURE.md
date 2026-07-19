# Architecture

Four layers with strict dependency direction. Only the observation layer may touch Minecraft; only
the provider layer may touch the WebSocket/JSON. The domain and runtime are pure and unit-tested.

```
Minecraft client                         (net.minegasm.neoforge)
  └─ MinecraftSampler ─┐  raw events + ClientStateSnapshot
                       ▼
Observation            (net.minegasm.observe)
  TickEventBuffer, StateTracker → StateTransitions, HapticAggregator → HapticIntent
                       ▼
Recipe / domain        (net.minegasm.recipe, net.minegasm.core)
  RecipeEngine + Presets + {Classic,Balanced}RecipePack → HapticScene
                       ▼  (bounded SceneIngressQueue, cross-thread)
Runtime                (net.minegasm.runtime, net.minegasm.render)
  SceneMixer → EndpointTarget → FeatureScheduler → OutputCommand   (HapticWorker, monotonic clock)
                       ▼
Provider               (net.minegasm.buttplug)
  ButtplugProvider → ButtplugCodec → ButtplugTransport → Intiface → devices
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
- **Bounded everything**: tick buffer (128), scene ingress (64), per-feature pending state — all with
  documented overflow policies.

## Where behaviour lives (data, not code branches)

- Priorities: `core/Priorities`. Per-event priority + expiry: `recipe/RecipeTiming`.
- Mode intensities: `recipe/Presets` (legacy parity table).
- Recipes: `recipe/ClassicRecipePack` (flat plateau parity) and `recipe/BalancedRecipePack` (shaped).
- Output caps: `render/SafetyCaps`. Fatigue budgets: `runtime/FatigueGovernor`.

## Extension seams

- `ButtplugTransport` — swap the JDK WebSocket for a client library without touching the engine.
- `RecipePack` — add packs without changing acquisition or scheduling.
- `MinecraftSampler` — the only class that changes between Minecraft versions (Stonecutter guards).
