# ADR-018: Central governance is scene-level, not per-cycle signal-level

**Status:** superseded by ADR-020. This document remains the history of the original scene-level design.
The implementation later needed a central time-sampled logical destination snapshot for consistent
bridge timing, complete routing axes, and post-resolution safety accounting.

The original decision kept fatigue per-backend; a later cycle moved
it central once the second backend (the local bridge) landed, which is the trigger brief 0003 §3.3 named.
Fatigue is now governed centrally as a scene-level attenuation (see "Fatigue centralization" below); the
aggregate body budget is still deferred to Phase 6. Refines brief 0003 §3.3.

**Context.** Brief 0003 §3.3 decided that mixing, fatigue, and aggregate safety are governed centrally
because the body is one system. When the local bridge (the second backend) landed, the question became
concrete: at what granularity does the central stage produce output, and what do backends consume?

Reading the mixer surfaced an apparent fork. `SceneMixer.render` is a per-15ms-cycle computation that
evaluates instantaneous primitive levels and routes them to Buttplug feature endpoints. The bridge, by
contrast, is a semantic-event channel: it sends a scene once (primitives + TTL) and lets the adapter
render the envelope. A central stage that emits per-cycle evaluated levels serves Buttplug but would
turn the bridge into a ~60/sec sample stream, contradicting its committed wire model.

**Decision.** The fork is really a layering:

- **Central = scene-level.** Holding, coalescing (continuous scenes latest-wins), expiry, priority and
  ducking between scenes, fatigue attenuation (see below), and later an aggregate body budget applied as
  level attenuation, all *before* fan-out to backends. This lives in `SceneGovernor`. A neutral driver
  (`HapticWorker`) advances it once per cycle and fans the governed set to every backend; it renders
  nothing itself.
- **Per-cycle signal rendering stays per-backend.** Envelope evaluation, per-endpoint coupling,
  smoothing, calibration, this is `SceneMixer.render`, and it is genuinely device-specific. It lives in
  the Buttplug rendering backend, which is the first rendering backend, not a privileged one: a future
  native integration implements the same `onGovernedScenes` seam and renders concurrently.
- **The semantic bridge consumes governed scenes unchanged.** Its wire model (scene + primitives +
  TTL) is unaffected. It now receives them coalesced and change-driven from the governor rather than raw
  every tick (`GovernedSceneForwarder`).

**Rationale.** A per-cycle instantaneous body budget cannot include a semantic backend at all: we do
not know what an adapter emits at time T. So a signal-level central bus is a category error the moment
a semantic backend exists. And electrostim's real constraints (max intensity, minimum inter-shock
interval, cooldown) are event-level, not per-cycle. Scene-level attenuation is both achievable and
sufficient, with ADR-016's independent per-device caps and arming as defense in depth.

**Fatigue centralization.** The original decision left fatigue per-backend, reasoning that it is
per-cycle signal work and a signal-level central bus is a category error once a semantic backend exists.
That reasoning holds for a per-cycle *sample stream*, and we did not build one. Fatigue moved central as
a **scene-level attenuation**, the same shape this ADR already earmarked for the body budget: the
`SceneGovernor` decays and accounts the per-role budgets and bakes the resulting per-role scalar into the
scene's primitive amplitudes before fan-out. Because `PrimitiveEvaluator` is linear in amplitude, baking
the scalar is equivalent to scaling the rendered level, and the bridge still receives a scene (primitives
+ TTL), not a 60/sec stream. So this is scene-level governance, consistent with the semantic wire model,
not the bus the original text ruled out.

The accounting basis changed from *achieved device output* to the *intended scene level*, since a central
stage cannot see any one backend's rendered output. The old `recordFatigue` read the post-cap,
post-multiplier, thresholded level of each rendered target; the central `govern()` accrues from
`levelAt × factor` on the authored scene. For the budgeted roles (steady TEXTURE and AMBIENT holds) the
two are close, and the one edge that matters is that the authored level is uncapped, so a user with a low
device max or multiplier reaches attenuation somewhat sooner than before. That is safe-direction (fatigue
only ever *reduces* output).

The governor stays device-neutral: it accrues whatever load the driver tells it to. The driver gates that
on a rendering backend actually being able to drive the body (`accountLoad = enabled &&
coordinator.anyRenderingActive()`, where a rendering backend is active when enabled, not panicked, and
has a device), so an enabled mod with nothing attached never fatigues, which the old per-render path got
for free. Fatigue is a property of the body, and with nothing attached nothing is driving the body. (Only
rendering backends count; a bridge-only session does not accrue, matching the pre-lift behavior where the
bridge never fatigued at all. Accruing across a genuinely rendering second backend is a refinement for
when one exists.) The move was done now because the surface is small while the bridge is the only second
backend, and because the Phase-6 body budget needs exactly this central seam.

**Consequences.** The governance lift was smaller than a signal-level rewrite: `SceneGovernor` holds and
governs, a neutral driver fans the governed set to every backend, and `SceneMixer` shrank to a pure
per-cycle renderer owned by the Buttplug backend. Rendering (Buttplug) and forwarding (the bridge) both
became `onGovernedScenes` implementations, so native integrations are first-class and run concurrently
with each other and the bridge. It cannot retroactively break the semantic bridge, which is why the
bridge transport was built first. The aggregate body budget is still not designed here; that is Phase 6
(electrostim), behind its threat model, and it slots into the same central attenuation stage fatigue now
uses.

**References.** Brief 0003 §3.3 (central governance), ADR-016 (electrostim caps/arming), `SceneGovernor`
(central hold, coalesce, expiry, fatigue), `SceneStore`, `GovernedSceneForwarder` (change-driven bridge
fan-out), `SceneMixer` (per-cycle renderer), `BridgeBackend` (semantic wire model).
