# Appendix G - Proposed architecture decision summaries

## ADR-001: clean-room replacement

**Decision:** Reimplement behavior without copying upstream source unless the project deliberately adopts compatible licensing and documents provenance.

## ADR-002: Stonecraft, NeoForge, two release lines

**Decision:** Use Stonecraft for variants `26.2-neoforge` and `26.1.2-neoforge`; Java 25; no second loader in MVP.

## ADR-003: client-observable event acquisition

**Decision:** State observation is canonical for multiplayer compatibility; loader events are optional optimizations.

## ADR-004: semantic scene model

**Decision:** Minecraft events create intents/scenes; they never create device commands directly.

## ADR-005: dedicated haptic worker

**Decision:** Client tick gathers/aggregates only. Real-time scheduling and WebSocket output run off-thread with monotonic time and bounded queues.

## ADR-006: Buttplug v4 provider only

**Decision:** Connect to external Intiface; no direct hardware protocols in MVP; protocol hidden behind provider/transport interfaces.

## ADR-007: full DeviceList and registry generation

**Decision:** Replace registry on every list and invalidate commands on generation changes/device-index reuse.

## ADR-008: output-type policy

**Decision:** Vibrate stable; Position/HwPosition experimental after calibration; other types disabled unless separately designed; Spray unsupported.

## ADR-009: compatibility recipe pack

**Decision:** Upstream quirks/defaults live in Minegasm Classic data/policies, separate from Balanced recipes.

## ADR-010: privacy and local-first operation

**Decision:** no telemetry, loopback default, redacted diagnostics, no server-side data exposure.
