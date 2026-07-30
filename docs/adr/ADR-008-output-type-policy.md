# ADR-008: Vibration production support, position experimental, Spray never

**Status:** accepted; amended 2026-07-30 (see Update).

**Update (2026-07-30).** Output support widened and the policy simplified; this supersedes the
"experimental" and "disabled by default" parts of the original decision below.

`Oscillate` and `Rotate` are enabled by default and driven by the same layers as `Vibrate` (continuous,
vibrate-equivalent outputs): the primary route allows all three and `SceneMixer.chooseKind` prefers
`Vibrate` where a device has it. `Position` and `HwPositionWithDuration` (strokers) are enabled too and
now move out of the box within a conservative **safe default** (centered neutral, narrow travel window,
capped by `SafetyCaps` at 0.20 travel). An explicit per-device calibration is now an optional refinement
rather than a prerequisite; calibration authoring has no UI yet and is still a follow-up.

`OutputPolicy` was slimmed to a single `enabled` flag. The old `experimental` and
`unsupported`/`permanentlyUnsupported` flags were never enforced (Spray is unroutable through
`OutputKind.UNKNOWN`, and motion is bounded by `SafetyCaps`), so they were removed along with the dead
`HapticRoute.requiresExperimentalOptIn`. The default policy now lists only the kinds a route can target
(Vibrate, Oscillate, Rotate, Position, HwPositionWithDuration); anything else is never chosen.

The paragraphs below describe the original decision.

**Decision.** `Vibrate` is production-ready and enabled after global/device enablement.
`Position` and `HwPositionWithDuration` are experimental: opt-in plus per-feature calibration required
before gameplay can move them. `Oscillate`, `Rotate`, `Constrict` are represented but disabled by
default (a speed/pressure value is not equivalent to vibration intensity). `Temperature`/`Led` are
discover/display only. `Spray` is **permanently unsupported** and can never be routed. Unknown future
output types are stored, logged once, and shown as unsupported, but never executed.

**Implementation.** `render/SafetyCaps` (hard per-kind ceilings), `config/OutputPolicy`
(`unsupported` for Spray), `SceneMixer.chooseKind`/`buildTarget` (gating + calibration).

**Consequences.** The reliable vibration path is never delayed by experimental outputs; a held vibration
always gets a planned zero (endpoints hold their value until changed).
