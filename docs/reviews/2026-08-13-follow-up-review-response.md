<!--
SPDX-AI-Disclosure: ai-generated
SPDX-AI-Model: claude-opus-4-8
SPDX-AI-Provider: Anthropic
SPDX-AI-Scope: Written by Claude Opus 4.8 while implementing the fixes for the 2026-08-13 follow-up code review under human direction. Records what was fixed and tested, and the one item left as a labelled beta limitation, with reasoning.
SPDX-AI-Date: 2026-08-13
-->

# Follow-up review response

Date: 2026-08-13

Tracks what was done about
[2026-08-13 follow-up comprehensive code review](2026-08-13-follow-up-code-review.md). Every entry has a
regression test unless noted. Two doc overclaims the follow-up caught (the banner claim and the
no-write-after-stop claim) were corrected in `2026-08-12-review-response.md` before this work.

## P1 findings

- **P1-1 watchdog reachability and latch.** The client tick now checks the watchdog before anything that
  takes the worker monitor, and calls `onResume` only on a paused-to-active transition, so a backend hung
  inside a cycle can no longer block the tick before the watchdog runs. Output is an explicit state
  (running, user stopped, watchdog stopped); the watchdog latches output off and auto-recovers once healthy
  cycles resume, a user panic stays latched, and the banner keys on the latch so it shows a watchdog stop.
  Tests: an integration test hangs the worker monitor and proves the tick reaches the watchdog without
  blocking, plus latch-and-recover and panic-not-cleared-by-recovery tests.
- **P1-2 stop overtaken by an in-progress write.** A buttplug4j write re-checks the stop generation after
  its blocking call and, if a stop raced it, reasserts the stop so a zero is the last command. The
  sequencing is a dependency-free helper with unit tests. A full provider-level integration test still
  needs an injectable buttplug4j client seam, which this provider does not yet have.
- **P1-3 bridge removal races the forwarder.** The bridge never resets its forwarder from a non-worker
  thread now (the reset is deferred to the worker cycle), and a stop bumps a generation the cycle captures,
  so a stop can't be overtaken by an effect the worker was mid-forward on and the forwarder maps are not
  raced. A dead transport is also closed before it is replaced.
- **P1-4 fault not quarantined, worker still healthy.** A faulted backend is stopped and taken out of the
  fan-out (quarantined) so it can't re-enter and re-drive; a separately-failed stop is recorded; the worker
  no longer advances its healthy heartbeat on a faulting cycle; the exception type and a capped message are
  recorded; and the hub rows and `/mg status` show a FAULT (quarantined) state. Quarantine lifts on
  reconnect. Test: quarantine, no re-entry, and lift.
- **P1-5 XToys reconnect drops state.** A reconnect loop keeps the link alive independently of output
  changes; on reconnect it resends the full current state including zeroes; cached values are marked
  unknown on a drop; and an owed zero from an expiry or panic is delivered on the next connection. Go tests
  with a fake WebSocket cover reconnect resend, owed-zero delivery, and per-role combining.
- **P1-6 central priority.** The bridge now resolves priority and exclusivity per role (a higher-priority
  exclusive layer suppresses lower-priority ones on the same role), which the raw per-role-maximum path
  ignored. This is per role because a bridge output is per role, and it leaves the hardware-validated
  Buttplug per-feature path unchanged. Full backend-neutral resolution keyed on a logical body region is a
  larger model that also needs hardware-in-the-loop validation; until then, the cross-role parity gap with
  native Buttplug is labelled as a beta limitation in the XToys docs.

## P2 findings

- **P2-1 incomplete fingerprint.** The bridge change-detection signature now includes every non-level shape
  parameter (beat timing, oscillation period, sweep easing, texture grain), so a shape change at the same
  peak reaches the adapter. Test added.
- **P2-2 SUPPLEMENTAL accepted.** Scene packs reject SUPPLEMENTAL too, since only ALL_COMPATIBLE has a
  runtime effect. Test added.
- **P2-3 writer executor leak.** The TCP transport shuts down its writer executor on a failed read/write,
  and the backend closes a dead transport before replacing it, so a reconnect no longer leaks a writer
  thread.
- **P2-4 shared XToys state and unbounded growth.** Scenes are namespaced per Minegasm connection, so two
  clients don't overwrite each other and one disconnecting clears only its own scenes; the live-scene map
  and listener set are bounded; and downstream status writes no longer hold the mutex.
- **P2-5 coercible numbers and NaN.** Numeric pack fields require an actual JSON number and reject a quoted
  value, NaN, and infinities. Tests added.

## Still open

- **Full backend-neutral priority resolution (part of P1-6).** A logical body-region/output-class model
  that resolves exclusivity and priority once for every backend, with the Buttplug mixer reduced to
  rendering, is not built. It spans recipes, packs, and rendering and cannot be validated by feel without
  hardware, so it is labelled as a beta limitation rather than shipped unvalidated.
- **Hardware/timing smoke pass.** The buttplug4j write path and the modern WebSocket handshake are
  compile-and-logic verified but not exercised against real hardware or a live Intiface/XToys session. Stop,
  disconnect, reconnect, world unload, pause, and backend switching still want a manual smoke pass.
- **E-stim and schema migration** remain policy-deferred beta items, as the follow-up review notes.
