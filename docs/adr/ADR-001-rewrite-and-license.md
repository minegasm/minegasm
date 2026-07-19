# ADR-001: Full rewrite and AGPL-3.0 license

**Status:** accepted.

**Context.** The original mod — RainbowVille's Minegasm (Java package `com.therainbowville.minegasm`,
source in the `minegasm-legacy` repo) — is licensed AGPL-3.0. We want a maintained replacement covering
its user-visible behaviour on current Minecraft/NeoForge.

**Decision.** License this project under **AGPL-3.0** (see `LICENSE`), matching legacy Minegasm so the
two are license-compatible. Build it as a **full rewrite** on a new semantic haptic engine rather than
a fork: behaviour, configuration names, and parity values are used as a compatibility reference and
recorded independently in `docs/PARITY.md` and as data in `recipe/Presets` / `ClassicRecipePack`.

A strict clean-room process is **not** a requirement — the AGPL license already permits reusing legacy
Minegasm code — but a fresh, independent implementation is preferred for a cleaner architecture and
clearer provenance.

**Name and versioning.** The project ships as **`minegasm`** (package `net.minegasm`). Versioning
starts at **1.0.0**: legacy Minegasm released as 0.x.x, so a fresh 1.x line keeps the two unambiguous
for users, mod hosts, and dependency ranges. Semantic versioning from there; Minecraft/loader
compatibility lives in artifact metadata (`minegasm-<version>+mc<mc>-neoforge.jar`), not in the
project version (brief §15.3).

**Consequences.** The engine is independent and testable without the old code. Deliberately adopting a
snippet or patch from the `minegasm-legacy` repo is permitted by the license if ever useful; it should
be noted in provenance when done.
