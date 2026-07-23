# ADR-004: Semantic scenes rather than direct event-to-command mapping

**Status:** accepted.

**Decision.** Minecraft events never produce device commands directly. They become device-independent
`HapticIntent`s, which recipes turn into `HapticScene`s composed of `HapticLayer`s carrying
`HapticPrimitive`s (Impulse/Texture/Rumble/Sweep/BeatPattern/Hold). Output-specific renderers translate
meaning into Buttplug commands at the edge. "Intensity" is not the universal model: a short impact can
render as vibration on one feature and a calibrated motion segment on another.

**Consequences.** The engine is device-agnostic and unit-testable without hardware. New output types or
recipes slot in without touching acquisition or scheduling.
