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
- **P1-6 safety lifecycle failures are still silently discarded.** Open. Render faults quarantine correctly,
  but lifecycle calls (stop, emergency stop, pause, resume, start, close, latch fan-out) still discard their
  result, so a failed stop is not distinguished from a delivered one in the global status. This is the next
  item: typed lifecycle outcomes feeding a shared backend-health snapshot that the hub, status command, and
  logs all read, with asynchronous provider stops tracked to completion.

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

- **P1-6 lifecycle-failure outcomes and shared integration health** (next).
- **Provider-level buttplug4j integration test** behind an injectable client seam (P1-5 residual).
- **Write-off-mutex writer with generation tokens** in the XToys adapter (P1-4 hardening).
- **Hardware feel pass**, which needs Intiface, a toy, and XToys on a real machine. Not runnable here. (The
  cross-backend conformance test is a unit test and is included: it feeds one governed set to both the bridge
  forwarder and the Buttplug mixer and asserts a suppressed layer reaches neither.)

## Phase 2 reminder (region gets teeth)

Phase 1 resolves competition per role with the body region defaulting to whole-body. Phase 2 makes the
region real: a region field on device config, region-aware matching in the mixer so competition is scoped
to overlapping regions rather than the whole role, and the device-config UI to set it. That needs a
hardware feel pass, so it is deliberately after the current safety-correctness work.
