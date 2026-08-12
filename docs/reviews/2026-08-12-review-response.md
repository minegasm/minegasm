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
  wedge a cycle no longer does, with a send epoch guaranteeing no write reaches a device after a stop-all.
  The full rearchitecture into a per-backend actor for every backend is not done; the buttplug4j change is
  compile-verified (that provider has no injectable seam for a unit test), so it wants an in-game smoke
  test.
- **P1-6 central governance.** Two parts done: two exclusive layers colliding on one endpoint now duck by
  priority first, so a quieter high-priority effect wins over a louder low-priority one; and an active,
  connected bridge now counts toward fatigue, so a bridge-only session fatigues instead of never doing so.
  Moving all priority and ducking resolution into one backend-neutral stage keyed on logical destinations
  is not done: it needs a logical-destination model that does not exist yet, and centralizing ducking means
  removing it from the Buttplug mixer to avoid double-ducking, which can't be validated by feel without
  hardware. That central rewrite is the residual.
- **P1-7 bridge identity.** The safety core plus inline rejection: a duplicate name can no longer create a
  hidden, unremovable backend (runtime construction skips a duplicate id and the client drops and reports a
  duplicate name), and the editors now refuse to save a name already used by another bridge on modern and
  every classic loader. The remaining split into an immutable internal id plus an editable display name,
  so a rename keeps the same connection instead of reconnecting, is the residual: it is a wide change to
  config serialization and every bridge screen plus a schema migration, for a rename-blip nicety now that
  duplicates are rejected at the source.

## Mitigated

- **P0-1 e-stim via XToys.** The adapter docs no longer present a raw-scalar e-stim output as supported;
  they spell out that it stays unsupported and at the user's own risk until the opt-in safety modality
  (ADR-016) lands. The pathway is left in place because e-stim is planned. The binding controls
  (distinct capability, separate caps, timed arming, hard native limits, finite pulses, cooldown, body
  budget, physical confirmation, threat model) are not built.

## Deferred

- The remaining UX recommendations (persistent stopped-state banner, structured test results, unified
  editing semantics, presenter extraction) are bounded UX work not yet done beyond the status, identity,
  and scrolling fixes above.
- Full connection-chain states in the UI (Minegasm to adapter to downstream device) need a bridge protocol
  extension: hello/version, capability declaration, downstream-ready/armed, acknowledgements. That changes
  a shipped wire format (PROTOCOL.md, the Go adapter, and the mod), so it is left for an explicit protocol
  revision rather than changed silently.
