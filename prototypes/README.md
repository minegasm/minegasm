# Prototypes (pre-brief ideation)

Two exploratory MVPs built during the ideation phase, **before** the implementation brief
(`docs/briefs/0001-…`) was written. Both were codenamed **Feelcraft** (Java package
`gg.meza.feelcraft`). They are preserved here as a historical record of how the design evolved.

They are **frozen** and **not part of this repository's build** — the shipping implementation is the
`minegasm` mod at the repo root (`src/`, package `net.minegasm`), which is a fresh rewrite informed by
these experiments rather than derived from their code. Each prototype is a self-contained Stonecraft/
NeoForge Gradle project kept as-is.

## The two MVPs

| # | Folder | What it was |
|---|---|---|
| 1 | [`01-feelcraft-haptics-mvp/`](01-feelcraft-haptics-mvp/) | The first MVP: a client-side Minecraft → Buttplug/Intiface haptics scaffold (tick sampling → intents → scenes → feature renderer → scheduler). |
| 2 | [`02-feelcraft-minegasm-replacement-mvp/`](02-feelcraft-minegasm-replacement-mvp/) | The second MVP: adds a legacy-Minegasm compatibility layer on top of #1 — mode presets (`config/MinegasmMode`), legacy event mapping (`config/LegacyEventType`), and the related docs. |

## Notes

- Code and configuration are preserved verbatim (byte-for-byte).
- Design docs are markdown under each prototype's `docs/` (`DESIGN.md`, `MINEGASM_REPLACEMENT.md`,
  `SAFETY.md`, `BUILD_NOTES.md`, …). The equivalent `.pdf` renders were dropped in favour of those.
- These prototypes fed into the brief and the current implementation; the lineage is
  Feelcraft MVP #1 → MVP #2 → the brief (`docs/briefs/0001-…`) → the `minegasm` rewrite.
