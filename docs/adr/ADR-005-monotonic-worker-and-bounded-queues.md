# ADR-005: Dedicated monotonic haptic worker and bounded queues

**Status:** accepted.

**Decision.** Minecraft ticks are observation boundaries, not the haptic clock. The client thread only
samples state and offers scenes to a bounded queue. A single haptic worker (~15 ms cadence) owns all
mixer/scheduler state and dispatches commands using `System.nanoTime()` (behind a `Clock` seam) for
every duration, cooldown, expiry, and pattern phase. Four distinct structures with documented overflow
policies: tick-local event buffer (128), continuous state trackers, cross-thread scene ingress (64),
and per-feature scheduled state — never a single FIFO.

**Rationale.** A 150 ms effect computed as "3 ticks" becomes wrong under tick-rate change or stalls.
Real-time haptics must expire rather than replay stale events.

**Consequences.** No device I/O on the client thread; deterministic, sleep-free tests via `FakeClock`.
