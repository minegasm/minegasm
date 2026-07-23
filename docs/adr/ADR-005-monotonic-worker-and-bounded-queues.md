# ADR-005: Dedicated monotonic haptic worker and bounded queues

**Status:** accepted.

**Decision.** Minecraft ticks are observation boundaries, not the haptic clock. The client thread only
samples state and offers scenes to a bounded queue. A single haptic worker (~15 ms cadence) owns all
mixer/scheduler state and dispatches commands using `System.nanoTime()` (behind a `Clock` seam) for
every duration, cooldown, expiry, and pattern phase. State lives in four distinct structures, each with
documented overflow policies, rather than a single FIFO: a tick-local event buffer (128), continuous
state trackers, a cross-thread scene ingress queue (64), and per-feature scheduled state.

**Rationale.** A 150 ms effect computed as "3 ticks" becomes wrong under tick-rate change or stalls.
Real-time haptics must expire rather than replay stale events.

**Consequences.** No device I/O on the client thread; deterministic, sleep-free tests via `FakeClock`.
