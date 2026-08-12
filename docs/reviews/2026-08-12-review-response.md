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

- **P1-1 out-of-band stop.** The watchdog now uses an out-of-band emergency stop: it latches master output
  off and stops every backend without taking the cycle monitor, doing only thread-safe work on that path
  (a volatile latch, the synchronized outbound queue, a provider stop dispatched off-thread). A watchdog
  can no longer deadlock behind a backend hung inside a cycle. The larger rework the review describes,
  isolating each backend behind a bounded queue so the cycle never runs backend I/O inline at all, is not
  done; a provider call that hangs still wedges its own worker thread, but output is latched off and
  devices are stopped out of band meanwhile.
- **P1-6 central governance.** The contained bug is fixed: two exclusive layers colliding on one endpoint
  now duck by priority first, so a quieter high-priority effect wins over a louder low-priority one. Moving
  all priority and ducking resolution into one backend-neutral stage, and counting an active bridge toward
  fatigue, are not done and remain as described in the review.
- **P1-7 bridge identity.** The safety core is fixed: a duplicate name can no longer create a hidden,
  unremovable backend. Runtime construction skips a duplicate id and the client drops and reports a
  duplicate name rather than orphaning a backend the maps can't address. The full split into an immutable
  internal id plus an editable display name, with inline uniqueness rejection across the editors and a
  config migration, is deferred.
- **P2-2 connection generations.** The TCP bridge publishes its connecting socket before the blocking
  connect so close can abort an attempt in flight, and tears the writer executor down if it loses the race.
  The modern WebSocket gives each connect attempt its own listener, so a slow handshake can't publish a
  dead socket after close and a superseded socket's callback can't clear a newer connection. The buttplug4j
  async connect is not separately generation-stamped.

## Mitigated

- **P0-1 e-stim via XToys.** The adapter docs no longer present a raw-scalar e-stim output as supported;
  they spell out that it stays unsupported and at the user's own risk until the opt-in safety modality
  (ADR-016) lands. The pathway is left in place because e-stim is planned. The binding controls
  (distinct capability, separate caps, timed arming, hard native limits, finite pulses, cooldown, body
  budget, physical confirmation, threat model) are not built.

## Deferred

- Most of the UX recommendations (persistent stopped-state banner, full connection-chain states, the
  bridge-identity UX, structured test results, unified editing semantics, presenter extraction) are larger
  UX work and are not addressed here beyond the status and identity fixes above.
- The scene-pack manager screen (P2-8) remains a startup-only, unscrollable selector.
