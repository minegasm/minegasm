---
title: "Shareable Haptics and Multi-Backend Output"
subtitle: "Data-driven recipe packs and a realized backend seam"
author: "Minegasm project planning"
date: "30 July 2026"
status: "Accepted"
lang: en-US
---

# Document status

**Audience:** Minegasm maintainers and contributors working on the haptic engine, recipe/scene
authoring, device integrations, configuration UI, and testing.

**Status:** accepted implementation brief. It builds on the initial architecture (0001) and the
backend expansion brief (0002).

**Relationship to earlier briefs:**

- **0001** stands. The observation → intent → scene → mixer → device pipeline does not change.
- **0002** stands for its backend direction, and this brief adopts its Phase 0 backend-neutral seam
  as a hard prerequisite. This brief does not restate 0002's provider roadmap; it references it.

**Primary recommendation:** treat the device-independent `HapticScene` as the pivot for two
workstreams that share one foundation. Make recipes and scenes data so users can configure and share
them, and realize the multi-backend seam so one scene fans out to more than Buttplug, with the load on
the body governed as one system.

# 1. Goals and non-goals

## 1.1 Goals

- Let users edit, save, and share recipe/scene content as files, without recompiling the mod.
- Ship a device-independent scene serialization format that round-trips the existing scene model.
- Realize 0002's backend-neutral seam so one scene can drive multiple enabled backends.
- Support additional backends (local bridge, bHaptics, XToys) on that seam.
- Govern the combined load on the body holistically across all enabled backends, not per device.
- Keep a Buttplug-only user fully unaffected by any of this.

## 1.2 Non-goals

- Replacing Buttplug or the semantic pipeline.
- A public network wire protocol for scene packs. The format is a local file, not a service API.
- Loading executable third-party code from a shared pack. Packs are declarative data only.
- Letting a shared pack enable a backend, raise a safety cap, or reach a restricted output modality.
- Guaranteeing every device in a backend's catalog is release-tested.

# 2. Workstream A: configurable and shareable haptics

## 2.1 Where we are

Recipes are Java today. `RecipePack` implementations (`ClassicRecipePack`, `BalancedRecipePack`) turn
a `RecipeContext` into an `Optional<HapticScene>` in code. Scenes exist only at runtime. A user cannot
change how an event feels, or share a feel they like, without a source build.

The good news is that the thing worth sharing already exists as clean data. `HapticScene` →
`HapticLayer` → `HapticPrimitive` is a device-independent tree of immutable value types: a scene has a
kind, priority, timing, and layers; a layer has a role, route, coupling, priority, offset, and one
primitive; primitives are seven concrete shapes (`Impulse`, `Texture`, `Rumble`, `Sweep`,
`BeatPattern`, `Hold`, `Oscillation`). None of it references a device. This is the payload of a
shareable pattern, analogous to what a bHaptics `.tact` file carries, and we already own the model.

## 2.2 Serialization

Reuse the existing config serialization approach rather than inventing one. `ConfigStore` already
runs Gson with `ConfigValueTypeAdapterFactory`, which builds immutable value types through their
all-args constructors so validation and defaults run and final fields are set correctly (this also
sidesteps the Gson 2.8.9 final-field bug seen on Minecraft 1.19.2). Scene packs use the same
machinery, plus:

- A **polymorphic adapter for `HapticPrimitive`**: a tagged union keyed on a `type` field
  (`impulse`, `texture`, …) so the seven shapes round-trip. This is the one genuinely new adapter.
- The same **atomic write, corrupt-file backup, and schema-version migration** pattern `ConfigStore`
  already implements. Packs carry a schema version from day one.

All new types must stay Java 8 source compatible, because the engine also compiles for Classic. No
records, no sealed types, no pattern switches.

## 2.3 Pack format

A **scene pack** is a single JSON file with:

- a schema version, a stable pack id, and display metadata (name, author, description);
- a set of **triggers** mapping a `GameEventKind` (and optional context predicates, e.g. ore vs
  stone, hardness band) to a **scene template**;
- scene templates expressed in the scene/layer/primitive vocabulary above.

A pack is the unit of sharing: one file, exported and imported whole.

## 2.4 Two tiers, ship the simple one first

`RecipeContext` shows today's recipes are context-reactive: `amplitude = modeBase × shape(intent) ×
userGain`. That shaping is the difference between a static pattern and a living recipe, and it decides
the effort:

- **Tier 1, static authored patterns.** A trigger fires a fixed scene template. This is true `.tact`
  parity, it covers the community-content use case (share a feel, apply it), and it is small. Ship
  this first.
- **Tier 2, parameterized templates.** A template binds a small, closed set of context values
  (intent magnitude, user gain, a hardness band) into levels and durations, reproducing what the code
  packs do today. This needs a tiny, non-Turing binding layer, deliberately not a scripting language.
  Defer until Tier 1 is proven.

## 2.5 Loading and identity

- Introduce a **`FileRecipePack`** implementing `RecipePack`, resolving intents against a loaded pack.
- Add a **pack registry/loader** that discovers packs in a config folder, validates them, and exposes
  them for selection.
- `RecipePackId` is a closed enum (`CLASSIC`, `BALANCED`) today. File packs need string identity, so
  either widen pack selection to a string id with the two built-ins reserved, or keep the enum for
  built-ins and add a parallel id space for file packs. This is a real change to touch carefully
  because `RuntimeConfig` and ADR-009 depend on the enum; capture it as an ADR.

## 2.6 Import safety

A shared pack is untrusted input. On load:

- validate against the schema and reject unknown or malformed structure fail-closed;
- clamp every level and duration through `SafetyCaps` so no pack can exceed engine ceilings, regardless
  of what the file asks for;
- a pack can never enable a backend, raise a cap, or reach a restricted output modality. Those live
  only in local config the user controls.

## 2.7 Authoring UI

Reuse the config editor scaffolding (`CustomizationModel`, `DeviceEditorModel`, the existing editor
screens). A pack manager lists installed packs, enables one, and imports/exports files. A full visual
scene editor is a later enhancement; file import plus the existing customization controls cover the
first release.

# 3. Workstream B: multi-backend output

This workstream is 0002's Phase 0, made concrete against the current code. It is a prerequisite for
Workstream A being interesting on more than one device.

## 3.1 The seam is at the scene

The last device-independent artifact in the running engine is the `HapticScene` handed to
`SceneIngressQueue`. Everything downstream (`SceneMixer.render` → `EndpointTarget` → `FeatureScheduler`
→ `OutputCommand` → `HapticProvider.send`) is Buttplug-shaped. So the fan-out point is the scene, and
the existing worker is, in effect, already the Buttplug backend.

## 3.2 Phase 0 slice

- **`HapticBackend`** interface: lifecycle, `submit(HapticScene)`, universal and scoped stop, health,
  optional (not mandatory) discovery.
- **`ButtplugBackend`** wrapping the existing `HapticWorker` unchanged. This wrapper is the regression
  guard: a Buttplug-only user must see no behavioral change.
- **`BackendCoordinator`** owning the enabled backends, fanning `submit` to all, and broadcasting
  `stopAll` concurrently with each backend guarded so one failure cannot block or delay another.
- **`FakeBackend`** for tests. Its test is the exit condition: one scene reaches two backends
  concurrently, `stopAll` reaches both without blocking, and one backend throwing does not stop the
  other.
- Rewire `HapticRuntime` to submit scenes to the coordinator, and `LifecycleController` to stop
  through it. Keep the watchdog per-backend on the Buttplug worker for now.

## 3.3 Mixing, fatigue, and safety are central

Decision: scene mixing, fatigue, and aggregate safety governance are **central**, not per-backend.
The body is one system. A user wearing several devices at once should have the combined load on that
body governed holistically, not have each backend independently spend its own budget unaware of the
others.

Reconciling this with the code takes a two-layer split, because different devices genuinely do not
share one ceiling:

- **Central, device-neutral.** Scene priority, expiry, and ducking (`SceneMixer.add/update` is already
  device-neutral), the `FatigueGovernor` (per role, per body region), and an aggregate body-level
  safety budget. These operate in the scene/layer vocabulary and see every enabled backend's intended
  output.
- **Per-backend, device-specific.** Translating resolved layers to a backend's own features and
  applying per-modality physical caps: a vibrator's ceiling, a vest actuator's ceiling. `SafetyCaps`
  per output kind and per-feature scaling stay here, under the central budget.

So the fan-out currency is the mixed, governed set of resolved layers, still in device-neutral scene
vocabulary, not the raw independent scene and not the full rendered-effect taxonomy from 0002 §3.2.
Each backend renders that and clamps within its own physical envelope, and the central budget is the
outer bound none of them can exceed in aggregate.

Sequencing note: `SceneMixer` and `FatigueGovernor` live inside `HapticWorker` today. Phase 0 can wrap
the worker whole for a single backend, but the central mixer, governor, and body budget must be lifted
out of the worker up to the coordinator when the second backend lands, so they are shared rather than
duplicated. Plan for that lift in Phase 0 rather than paying it back later. (Done once the local bridge
landed: scene holding, coalescing, expiry, and fatigue moved into `SceneGovernor`; fatigue is realized as
the scene-level attenuation this section calls for, and the worker now renders per-cycle only. See
ADR-018.)

## 3.4 Backend roadmap

Ordering, safest-foundation-first: local bridge (loopback WebSocket/OSC, the community extension
point) → bHaptics (spatial, gated on the Java-SDK feasibility spike 0002 already flagged and this
brief still treats as unverified) → XToys (webhook bridge). The neutral rendered-effect taxonomy from
0002 §3.2 stays deferred: the scene is the currency until a spatial backend actually needs a
normalized effect, and fanning the full scene is strictly better for a spatial backend than a
pre-flattened one.

# 4. Phasing

- **Phase 0. Backend seam.** Workstream B §3.2. Prerequisite for everything else.
- **Phase 1. Scene serialization + Tier 1 file packs + import safety.** No new hardware. Delivers
  configurable and shareable static patterns.
- **Phase 2. Pack manager UI + Tier 2 parameterized templates.**
- **Phase 3. Local bridge backend.** The extension point.
- **Phase 4. bHaptics feasibility spike and backend.**
- **Phase 5. XToys webhook backend.**

# 5. Decisions to capture as ADRs

- Scene-level fan-out as the backend seam (supersedes 0002's post-mix diagram as the starting point).
- Central body-level mixing, fatigue, and aggregate safety, with per-backend and per-modality physical
  caps underneath (decided in §3.3; the ADR records the two-layer split and why the body is governed
  as one system).
- File-based recipe packs and extensible pack identity.

# 6. Risks and open questions

| Risk or question | Required resolution |
|---|---|
| Tier 2 template binding could grow into an unsafe scripting surface | Keep it a closed, declarative binding; no user code execution; clamp all outputs |
| A malicious or broken shared pack | Schema validation, fail-closed parsing, mandatory cap clamping, no capability escalation from a file |
| Extending `RecipePackId` touches `RuntimeConfig` and ADR-009 | ADR before implementation; preserve built-in ids and Buttplug-only config exactly |
| No confirmed desktop Java bHaptics SDK | Feasibility spike before committing; still unverified as of this brief |
| Classic runs Java 8 with Gson 2.8.9 | New value types stay Java 8 compatible; reuse the all-args-constructor factory; no records/sealed/pattern-switch |
| Multiple backends could overwhelm the user | Per-backend and per-event routing plus conservative defaults, inherited from 0002 |

# 7. Reference sources

- Brief 0002, haptic backend expansion (backend seam, provider roadmap, inherited safety model):
  [`0002-haptic-backend-expansion.md`](0002-haptic-backend-expansion.md)
- Brief 0001 architecture, ADR-004 (semantic scene model), ADR-008 (output type policy), ADR-009
  (Classic pack and migration), under [`../adr/`](../adr/).
