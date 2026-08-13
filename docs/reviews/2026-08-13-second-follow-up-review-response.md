<!--
SPDX-AI-Disclosure: ai-generated
SPDX-AI-Model: claude-opus-4-8
SPDX-AI-Provider: Anthropic
SPDX-AI-Scope: Written by Claude Opus 4.8 while implementing the fixes for the 2026-08-13 second follow-up code review under human direction. Records what was fixed and tested, what is a labelled beta limitation, and what is still open, with reasoning.
SPDX-AI-Date: 2026-08-14
-->

# Second follow-up review response

Tracks what was done about
[the 2026-08-13 second follow-up code review](2026-08-13-second-follow-up-code-review.md). Every entry has
a regression test unless noted.

## P1 findings

- **P1-1 watchdog can be blocked before it runs.** An independent daemon timer polls the watchdog every
  500ms regardless of the client thread, and the client tick checks the watchdog before anything that takes
  the worker monitor. A pause or world-unload that blocks the client thread can no longer keep the watchdog
  from firing. Output state is an independent set of stop causes guarded by its own lock, not the cycle
  monitor, so a watchdog stop and a user panic can't overwrite each other: the watchdog latches off and
  auto-recovers on healthy cycles, a user panic stays latched until the user resumes, and neither clears the
  other. Tests cover both bad transitions.
- **P1-2 output state has no safe transition authority.** Same independent-cause model as P1-1: `StopCause`
  plus an immutable `OutputStatus`, folded from the worker (user stop, watchdog) and the client (disabled,
  backend fault). Removing one cause never clears another.
- **P1-3 bridge exclusivity does not retract an already-held scene.** Resolved as final-state architecture,
  not a patch. Priority and exclusivity now resolve once, centrally, in `SceneGovernor.govern`, so every
  backend consumes one already-resolved set instead of each running its own pass and diverging. The bridge
  then sends the whole current level per role as an authoritative `output` snapshot whenever it changes.
  Because each frame is the full state, a scene ending or being suppressed retracts at the adapter as soon
  as its role's level drops; the old per-scene stream could only add, so a lower-priority scene lingered
  until its TTL. The XToys and reference adapters mirror the snapshot rather than combining events. Within a
  role a higher-priority exclusive suppresses strictly lower-priority layers; cross-role per-feature ducking
  stays the Buttplug mixer's job, since only it has a device model, and the per-role bridge has no such
  collision. Logical body regions join the resolution key in Phase 2. Tests: central resolution in the
  governor, the authoritative snapshot (change, retract, heartbeat, drop-and-retry) on the Java side, and a
  vanished-role retraction test plus cross-client combination on the Go side.
- **P1-4 XToys reconnect can lose an owed zero or replay stale output.** The adapter keeps one authoritative
  entry per Minegasm client, so a resync resends the live current state (zeros included), never a remembered
  snapshot that could be stale. The blocking WebSocket dial runs without the state mutex held, so a slow or
  timing-out reconnect can't delay a stop; the resync then runs under the lock, so a stop is serialized
  against it (it either clears the state the resync reads, or runs after and sends the zeros itself) and an
  owed zero is not lost across a reconnect. The link is supervised while any client is connected, so an
  owed zero is redelivered by the next authoritative resend even if a single write failed ambiguously.
  Go tests cover reconnect resend, owed-zero-after-drop, and per-role cross-client combination.
  One narrow correctness edge stays open: an ambiguous failed nonzero write followed by the last client
  disconnecting leaves the owed zero unrecorded with no client to keep the link supervised, so a nonzero
  could sit at XToys with no mod attached. Client-connected supervision covers every case except that
  teardown window. Closing it is the single-owner writer with explicit generation tokens (writes off the
  mutex, delivery committed only if the generation is still current); the authoritative full-state resend
  already removes the broader stale-replay class.
- **P1-5 Buttplug writes not scoped to a session.** Each queued write re-checks the send epoch, the
  connection generation, and the registry generation immediately before dispatch, and the generations
  advance on connect, disconnect, and stop, so a backlog built before a drop is invalidated rather than run
  against the new device list. The dependency-free compensation helper has unit tests. A provider-level test
  against an injectable buttplug4j client seam is still the gap noted below.
- **P1-6 safety lifecycle failures are still silently discarded.** Addressed for the safety-critical case.
  A `stop` or `emergencyStop` that throws is no longer discarded: the backend is recorded as faulted and
  quarantined, and both calls return the count of backends whose stop was not confirmed. Because the
  quarantine already folds into `OutputStatus` (a `BACKEND_FAULT` cause) and into `buttplugFaulted` /
  `bridgeFaulted`, a failed stop surfaces on the hub and in `/mg status` as a fault rather than a clean
  stopped state, through the fault surface that already existed, without new per-loader logic. Test: a
  throwing stop and a throwing emergency stop are each recorded and quarantined while the other backends
  still stop. Still open: an asynchronous provider stop that fails only after its completion stage settles
  is not caught synchronously here; a provider health callback into the same fault surface is the remaining
  part, along with the shared view-state/action-result layer the review recommends for the UI (a P2/UX pass,
  not the safety core).

## P2 findings

- **P2-1 XToys bounds.** The accept loop enforces a hard client limit; each client is one authoritative
  output entry, so bounding connections bounds retained state, which retires the per-scene key/TTL/quota
  bounds the earlier per-scene model needed. The reconnect loop dials while any client is connected, so the
  chain shows ready before the first effect.
- **P2-2 backend fault visibility and recovery.** The Buttplug hub row shows FAULT (quarantined) and the
  refresh keys observe it, so a quarantined backend is never labelled ready. Buttplug quarantine lifts only
  on a successful connect, not on the request. Bridge faults surface as quarantined in the rows.
- **P2-3 integer pack fields accept fractional and overflowing numbers.** Integer fields parse through an
  exact decimal, reject a non-zero fractional scale, and reject values outside the Java integer range;
  floats reject non-finite values; only the implemented delivery mode is accepted.

## Still open

- **Asynchronous provider stop completion** (P1-6 residual): a provider health callback so a stop that
  fails only after its async completion stage settles surfaces the same way a synchronous one now does.
- **Shared view-state and action-result UI layer** across both loader families (the review's UX pass), so
  every screen reads one status model instead of repeating boolean logic.
- **Provider-level buttplug4j integration test** behind an injectable client seam (P1-5 residual).
- **Write-off-mutex writer with generation tokens** in the XToys adapter (P1-4 hardening).
- **Hardware feel pass**, which needs Intiface, a toy, and XToys on a real machine. Not runnable here. (The
  cross-backend conformance test is a unit test and is included: it feeds one governed set to both the bridge
  forwarder and the Buttplug mixer and asserts a suppressed layer reaches neither.)

## Phase 2 (region gets teeth) — built

Body region is now the second axis of the destination, built and tested without hardware. A `BodyRegion`
type carries an `overlaps` relation (routing and competition scope) and a `contains` relation (the
governor's coarse suppression). A layer carries its target region and a device its worn region (per
device). The governor suppresses a lower-priority same-role layer only when an exclusive wholly contains
its region, and the renderer routes an effect to a device only when their regions overlap, so a
region-scoped exclusive owns its region's devices while a whole-body effect keeps playing elsewhere. The
bridge is region-blind (an adapter has no device model), so region-scoped exclusivity is a renderer-path
refinement, not a bridge property. Everything defaults to whole-body, so untouched setups are unchanged.

The Device Editor has a region selector on every loader, with "Not set" kept distinct from an explicit
whole-body choice (both resolve to whole-body). Scene packs can author a layer region. The gate test
proves two same-role effects in non-overlapping regions neither suppress each other nor cross-route.

What is left is not architecture: choosing which built-in events deserve a specific region (an authoring
call, best made against real toys), and the hardware feel pass to validate the taxonomy and placement.
