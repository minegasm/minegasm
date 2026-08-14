# ADR-020: Central resolved destinations and observable backend outcomes

**Status:** accepted. Supersedes the fan-out currency and bridge parts of ADR-018.

**Context.** Passing the same scene list to every backend did not make their output equivalent. The
Buttplug renderer sampled layer windows and primitive shapes. The bridge took nominal amplitudes, so it
could start delayed effects early, bridge over beat gaps, and run short layers until their parent scene
expired. The central conflict model also lacked output class, and fatigue was recorded before suppressed
output was removed.

Non-blocking providers exposed a second mismatch. A backend call could return after enqueueing work, then
the actual send or stop could fail later. The worker still recorded a healthy cycle and the UI had no way
to distinguish a requested stop from a confirmed one.

**Decision.**

1. `SceneGovernor.resolve` produces one `GovernedOutput` per cycle. It contains the active governed
   scenes needed for physical route refinement and a `ResolvedDestinationSnapshot` sampled at the same
   monotonic time.
2. A logical destination is the tuple of role, body region, and output class. Output classes are
   strength, motion, constriction, thermal, light, and unknown. Routes that span more than one class are
   split before competition, so unrelated actuator families cannot suppress each other.
3. Layers outside their own time window, and primitives currently evaluating to zero, do not compete or
   enter the snapshot. Priority and exclusivity resolve before fatigue. Fatigue load is tracked by role
   and body region and records only surviving intended output.
4. Rendering backends retain scenes for physical capability filters, calibration, and the partial overlap
   case where a region-specific exclusive meets a whole-body layer. Semantic backends consume the
   authoritative destination snapshot rather than evaluating scenes independently.
5. Bridge protocol version 1 carries the complete destination set, a monotonic generation, and a TTL.
   Missing destinations are zero. Adapters reject any version they do not understand.
6. Every non-blocking backend operation reports accepted, delivered, failed, timed out, or superseded,
   together with its operation and generation. An unresolved failure remains visible after a newer
   compensating operation and clears only on explicit recovery. Failed or timed-out backends are
   quarantined without claiming that healthy integrations stopped. A diagnostic test failure remains
   visible as its action result, but does not quarantine ordinary output.
7. The shared `OutputViewState` separates the global output gate, actual body-driving state, current
   backend outcomes, and unresolved per-backend failures. Commands and screens use its global status
   instead of rebuilding safety predicates.

**Consequences.** The bridge is now a sampled state protocol rather than a semantic scene protocol. At
the worker cadence this is still bounded and change-driven: unchanged active output is sent only to
refresh its TTL. The beta keeps protocol version 1 while changing its draft message shape, so no migration
shim is retained.

The destination snapshot is device-neutral. A Buttplug backend can still refine partial whole-body
overlap using actual device placement and concrete feature filters. An adapter that needs the same
precision must map body regions and output classes explicitly. The XToys adapter offers both the shipped
role aggregation mode and a destination action mode for that purpose.

Backend-local generation checks and short dispatch locks make stop ordering explicit. The bridge also
orders its generation check and queue insertion under one boundary, so an output cannot pass the check
and then append behind a stop. Blocking Buttplug4j calls stay off the governance thread; dropped queued
writes complete as superseded instead of becoming silent, permanently pending operations.

This ADR does not authorize electrostimulation output. Its distinct capability, physical confirmation,
hard native-unit limits, pulse and cooldown rules, threat model, and dedicated safety review remain under
ADR-016.
