# ADR-008: Vibration production support, position experimental, Spray never

**Status:** accepted.

**Decision.** `Vibrate` is production-ready and enabled after global/device enablement.
`Position` and `HwPositionWithDuration` are experimental: opt-in plus per-feature calibration required
before gameplay can move them. `Oscillate`, `Rotate`, `Constrict` are represented but disabled by
default (a speed/pressure value is not equivalent to vibration intensity). `Temperature`/`Led` are
discover/display only. `Spray` is **permanently unsupported** and can never be routed. Unknown future
output types are stored, logged once, and shown as unsupported, but never executed.

**Implementation.** `render/SafetyCaps` (hard per-kind ceilings), `config/OutputPolicy`
(`permanentlyUnsupported` for Spray), `SceneMixer.chooseKind`/`buildTarget` (gating + calibration).

**Consequences.** The reliable vibration path is never delayed by experimental outputs; a held vibration
always gets a planned zero (endpoints hold their value until changed).
