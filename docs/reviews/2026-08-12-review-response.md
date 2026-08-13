<!--
SPDX-AI-Disclosure: ai-generated
SPDX-AI-Model: claude-opus-4-8
SPDX-AI-Provider: Anthropic
SPDX-AI-Scope: Written by Claude Opus 4.8 while implementing the fixes for the 2026-08-12 comprehensive code review under human direction. Records what was fixed, mitigated, or left as a residual, with the reasoning for each.
SPDX-AI-Date: 2026-08-12
-->

# Review response

Date: 2026-08-12

This tracks what was done about the findings in
[2026-08-12 comprehensive code review](2026-08-12-comprehensive-code-review.md). Each entry is either
fixed (with a regression test), fixed in a measured form with the larger rework noted, or deferred with a
reason. The e-stim modality is planned but not a priority right now, so its finding is mitigated rather
than fully built out.

## Fixed

- **P1-2 buttplug4j stop.** The provider dispatches a stop on its own executor, so an emergency stop can't
  queue behind a blocking connect or scan on the lifecycle thread.
- **P1-3 panic latch on a new bridge.** A bridge built at construction or added during reconciliation
  inherits the current output latch before it joins the fan-out, so one added while output is latched off
  starts disabled.
- **P1-4 isolated test outliving a stop.** The Buttplug backend clears its injected test scene on every
  stop-like transition (stop, pause, discard, master-off, close), so a long test can't resume when output
  comes back.
- **P1-5 swallowed backend faults.** A backend that throws during scene fan-out is stopped and recorded
  instead of silently swallowed, so it can't keep holding stale output, and the fault shows up in status.
- **P2-1 unimplemented delivery modes.** Scene packs reject the delivery modes the mixer doesn't honor
  (BEST_PER_DEVICE, BEST_GLOBAL, EXCLUSIVE) at load instead of silently fanning to every device.
- **P2-2 connection generations.** The TCP bridge publishes its connecting socket before the blocking
  connect so close can abort an attempt in flight, and tears the writer executor down if it loses the
  race; covered by a test. The modern Buttplug WebSocket was rewritten so each connect attempt gets its
  own listener that acts only while it is the current attempt, so a slow handshake can't publish a dead
  socket after close and a superseded socket's callback can't clear a newer connection; covered by a test
  that drives the listener with a fake WebSocket (the handshake wiring itself still wants an in-game smoke
  test against Intiface). The buttplug4j async connect is generation-stamped too, so a connect completing
  after a disconnect can't rebuild live state.
- **P2-8 pack screen.** The scene-pack screen scrolls in a viewport with up/down buttons (and the wheel on
  modern), reusing the hub's RowScroller, so installed packs no longer overrun the pinned Done button.
  Applied on modern and every classic loader. The larger "manager" features (import, open-folder, per-file
  validation, metadata) are not built.
- **P2-3 dropped bridge frames counted as delivered.** The forwarder records a frame as sent only when the
  sink accepts it, retries a drop once the link returns, resyncs on reconnect, and keys change detection on
  structure as well as peak amplitude.
- **P2-4 config swapped before the fallible save.** A disable takes effect and stops hardware before the
  save and stays applied even if the write throws; other changes persist first and publish only once on
  disk.
- **P2-5 unbounded input.** Pack files are size-capped before reading; trigger, layer, beat, output, and
  string cardinalities are capped during parse; the bridge inbound line is length-capped; and the modern
  WebSocket rejects an oversized message whole instead of delivering a truncated prefix.
- **P2-6 future config schema downgraded.** A file from a newer schema is left byte-for-byte intact, backed
  up without clobbering an earlier backup, and the build runs on safe defaults instead of rewriting it.
- **P2-7 pack identity.** Built-in pack ids (classic, balanced, modern) are reserved so a file pack can't
  shadow them, and a selected pack that isn't found is reported instead of quietly becoming Balanced.

## Fixed, measured

- **P1-1 out-of-band stop.** Two parts. First, the watchdog uses an out-of-band emergency stop: it stops
  every backend's hardware without taking the cycle monitor, doing only thread-safe work on that path (the
  synchronized outbound queue, a provider stop dispatched off-thread), so it can't deadlock behind a
  backend hung inside a cycle. It does not latch master output off, since a stall is usually transient and
  the old watchdog auto-recovered; a real panic still latches. Second, the buttplug4j provider now runs its
  blocking device writes off the worker thread on a bounded queue, so the concrete inline call that could
  wedge a cycle no longer does. A send epoch drops writes still queued when a stop-all lands, but a write
  already inside a blocking library call can still finish after the stop; ordering that fully needs an
  injectable timing seam and is not verified.
  With that in place, no backend runs blocking I/O inline on the worker cycle any more: the native
  provider's send is an async WebSocket write, the bridge's is a bounded non-blocking queue, and mixing and
  scheduling are pure CPU. The review's "full per-backend actor" rearchitecture is therefore not needed for
  safety and is not done, since it would rework the tested stop-under-one-monitor contract for no remaining
  wedge. The buttplug4j change is compile-verified (that provider has no injectable seam for a unit test),
  so it wants an in-game smoke test.
- **P1-6 central governance.** Two parts done: two exclusive layers colliding on one endpoint now duck by
  priority first, so a quieter high-priority effect wins over a louder low-priority one; and an active,
  connected bridge now counts toward fatigue, so a bridge-only session fatigues instead of never doing so.
  Moving all priority and ducking resolution into one backend-neutral stage keyed on logical destinations
  is not done: it needs a logical-destination model that does not exist yet, and centralizing ducking means
  removing it from the Buttplug mixer to avoid double-ducking, which can't be validated by feel without
  hardware. That central rewrite is the residual.
- **P1-7 bridge identity.** Fully addressed. A duplicate name can no longer create a hidden, unremovable
  backend (runtime construction skips a duplicate id and the client drops and reports one), the editors
  refuse a name already used by another bridge, and each bridge now has an immutable id separate from the
  display name. The runtime keys backends on the id, so a rename keeps the connection; the id is generated
  once and persisted (no schema bump, still beta), and the UI keeps passing the display name while the
  client resolves it to the id.

## Fixed (connection-chain and UX)

- **Connection-chain states.** The bridge protocol gained optional, versioned adapter-to-mod messages
  (hello, status) carrying a downstream state, so Minegasm shows the whole chain it can see (waiting,
  adapter connected, ready, or adapter up but downstream offline) in the hub rows and /mg status. The
  XToys adapter reports it; an adapter that stays silent works exactly as before.
- **Stopped-output banner.** Every hub screen shows a red OUTPUT STOPPED banner while output is latched
  off, on modern and every classic loader. Output is now tracked as an explicit state (running, user
  stopped, watchdog stopped), the watchdog latches and auto-recovers, and the banner keys on the latch, so
  it reflects a watchdog stop as well as a user panic.

## Mitigated / needs sign-off

- **P0-1 e-stim via XToys.** The adapter docs no longer present a raw-scalar e-stim output as supported;
  they spell out that it stays unsupported and at the user's own risk until the opt-in safety modality
  (ADR-016) lands. The pathway is left in place because e-stim is planned. The engine models no e-stim
  modality at all, so the current state is fail-closed by absence: there is no armed shock path an ordinary
  scene could reach. The binding controls ADR-016 requires (distinct capability, separate caps, timed
  arming, hard native limits, finite pulses, cooldown, body budget, physical confirmation, threat model)
  are deliberately not built here: that is a safety-critical system that cannot be hardware-validated in
  this pass and needs the project's own threat model and review, so it is surfaced for an explicit,
  informed go-ahead rather than shipped unvalidated.

## Residual

- **P1-6 central ducking.** The contained bug (exclusive-vs-exclusive priority) and bridge fatigue are
  done. Moving all priority and ducking resolution into one backend-neutral stage keyed on logical
  destinations is the residual: it needs a logical body-region/destination model that does not exist yet,
  and centralizing ducking means removing it from the Buttplug mixer (whose per-endpoint behavior is
  tested), which cannot be validated by feel without hardware.
- **Structured test results and presenter extraction.** Returning a structured test result (accepted and
  skipped targets, acknowledgements) touches every test caller across the screens; presenter extraction is
  architecture hygiene rather than a review fix. Both are lower-value UX and are not done.
