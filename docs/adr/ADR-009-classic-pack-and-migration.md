# ADR-009: Classic recipe pack and legacy config migration

**Status:** accepted.

**Decision.** Keep two recipe packs. **Classic** reproduces legacy Minegasm event enablement, relative
intensities, durations, and the flat-plateau + feedback-boost envelope (data in `recipe/Presets` and
`recipe/ClassicRecipePack`; values in `docs/PARITY.md`). **Balanced** is the modern default for new
users (shaped envelopes, mining texture, priority/ducking, variation, fatigue). The two are kept
separate so compatibility never constrains the modern engine. Modes are data-driven presets, never
hard-coded into event classes.

Legacy config import (`LegacyMinegasmImporter`) parses the old TOML read-only, shows a preview, maps
values into CUSTOM-mode intensities + the Classic pack, writes the new config atomically, and never
silently enables experimental outputs.

**Consequences.** Migrating users get equivalent behaviour without editing raw config; the exact
"harvest" semantics should be re-pinned against a chosen commit in the `minegasm-legacy` repo at kickoff.
