# ADR-018: Central governance is scene-level, not per-cycle signal-level

**Status:** accepted (design decision; the body budget itself is deferred to Phase 6). Refines brief
0003 §3.3.

**Context.** Brief 0003 §3.3 decided that mixing, fatigue, and aggregate safety are governed centrally
because the body is one system. When the local bridge (the second backend) landed, the question became
concrete: at what granularity does the central stage produce output, and what do backends consume?

Reading the mixer surfaced an apparent fork. `SceneMixer.render` is a per-15ms-cycle computation that
evaluates instantaneous primitive levels and routes them to Buttplug feature endpoints. The bridge, by
contrast, is a semantic-event channel: it sends a scene once (primitives + TTL) and lets the adapter
render the envelope. A central stage that emits per-cycle evaluated levels serves Buttplug but would
turn the bridge into a ~60/sec sample stream, contradicting its committed wire model.

**Decision.** The fork is really a layering:

- **Central = scene-level.** Coalescing (continuous scenes latest-wins, already done in
  `SceneMixer.add`), priority/ducking between scenes, and later an aggregate body budget applied as
  level attenuation, all *before* fan-out to backends.
- **Per-cycle signal rendering stays per-backend.** Envelope evaluation, per-endpoint coupling,
  smoothing, calibration, this is `SceneMixer.render`/the worker, and it is genuinely device-specific.
  It does not move.
- **The semantic bridge consumes governed scenes unchanged.** Its wire model (scene + primitives +
  TTL) is unaffected.

**Rationale.** A per-cycle instantaneous body budget cannot include a semantic backend at all: we do
not know what an adapter emits at time T. So a signal-level central bus is a category error the moment
a semantic backend exists. And electrostim's real constraints (max intensity, minimum inter-shock
interval, cooldown) are event-level, not per-cycle. Scene-level attenuation is both achievable and
sufficient, with ADR-016's independent per-device caps and arming as defense in depth.

**Consequences.** The eventual governance lift is smaller than a signal-level rewrite: insert one
scene-level stage before fan-out, no worker-cycle restructure, no bridge change. It cannot retroactively
break the semantic bridge, which is why the bridge transport can be built first. The body budget is not
designed here; that is Phase 6 (electrostim), behind its threat model.

**References.** Brief 0003 §3.3 (central governance), ADR-016 (electrostim caps/arming), `SceneMixer`,
`BridgeBackend` (semantic wire model and its governance-scope note).
