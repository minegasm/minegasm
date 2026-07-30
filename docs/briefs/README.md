# Briefs & planning documents

This directory holds **briefs and planning documents**, the source specifications that drive the
implementation. It's the durable record of *what we set out to build and why*, kept separate from the
living design docs (`docs/ARCHITECTURE.md`, `docs/adr/`, …) that describe *how the code works today*.

## Executive summary

Minegasm is a full rewrite of the original Minegasm mod into a client-side, multi-device haptic
feedback engine for Minecraft Java Edition, driving Buttplug v4 devices through a local Intiface
server. The project's shape comes from the initial implementation brief (0001): a semantic intent →
scene → mixer → device pipeline, running on NeoForge, Fabric, and Forge across several Minecraft
versions, reproducing the legacy mod's triggers and modes on a new engine rather than forking its
code. Brief 0003 (accepted) extends that direction: recipes and scenes become shareable, data-driven
packs, and the engine gains a backend-neutral seam so one scene fans out to multiple backends with the
load on the body governed centrally. Brief 0002 remains proposed as the broader provider roadmap
(bHaptics, local bridge, XToys, audio, and more) that 0003 builds on and references rather than
replaces. One decision that grew out of the seam, supporting opt-in electrostim, is accepted in
ADR-016. As new briefs are accepted, update this section to reflect the current standing direction
across all of them; the per-brief write-ups under the index below cover what each one specifically
added or changed.

## Convention

- One folder per brief, prefixed with a zero-padded sequence number and a slug:
  `NNNN-short-slug/`. The initial brief is `0001-…`; the next planning document is `0002-…`, and so on.
- A simple, single-file plan can just be `NNNN-short-slug.md` instead of a folder.
- Once accepted, a brief (including its appendices, examples, and assets) is kept unchanged, unless
  there's a compelling reason to edit it (e.g. removing leaked secrets). If direction changes, add a
  new brief (or an ADR under `docs/adr/`) that supersedes it, and note the supersession here.
- Cross-cutting decisions that emerge from a brief are captured as ADRs in `docs/adr/`.
- Each accepted brief gets a short summary (a few sentences: what it covers, what's notable) under the
  index table, in its own `### NNNN: Title` section, in addition to its one-line index row. When a new
  brief is accepted, also revisit the executive summary above and update it if the standing direction
  has changed.

## Index

| # | Brief | Date | Status |
|---|---|---|---|
| 0001 | [Initial implementation brief](0001-initial-implementation-brief/MINEGASM_NEXT_IMPLEMENTATION_BRIEF.md) | 16 Jul 2026 | Accepted, implemented |
| 0002 | [Haptic backend expansion](0002-haptic-backend-expansion.md) | 28 Jul 2026 | Proposed |
| 0003 | [Shareable haptics and multi-backend output](0003-shareable-haptics-and-multi-backend.md) | 30 Jul 2026 | Accepted |

### 0001: Initial implementation brief

The senior-developer handoff brief this project was built from: a client-side, multi-device Minecraft
haptics mod targeting NeoForge, Java 25, Stonecraft, and Buttplug v4 via Intiface. Its appendices cover
the parity matrix, recipe catalog, Buttplug v4 mapping, build plan, test matrix, risks, architecture
decisions, and coding guidelines, alongside examples and diagram assets.

> **Naming note:** this brief predates the project's naming decision and uses the working name
> "Minegasm Next". The project is now named **`minegasm`** (package `net.minegasm`, AGPL-3.0); the
> original mod is referred to as *legacy Minegasm* (`com.therainbowville.minegasm`, source in the
> `minegasm-legacy` repo). See `docs/adr/ADR-001-rewrite-and-license.md`.

### 0003: Shareable haptics and multi-backend output

Accepted. Makes recipes and scenes into shareable data: the existing device-independent
scene/layer/primitive model gets a JSON pack format (reusing the config Gson infrastructure), a
file-based `RecipePack`, a loader with import safety, and authoring UI, shipped static-first and then
parameterized. It also realizes 0002's backend-neutral seam concretely: the fan-out point is the
`HapticScene`, the existing Buttplug worker becomes one backend behind a `HapticBackend` interface, and
a coordinator fans scenes to every enabled backend. Mixing, fatigue, and aggregate safety are governed
centrally because the body is one system, with per-backend and per-modality caps underneath. It builds
on and references 0002 for the provider roadmap rather than replacing it; the related opt-in
electrostim decision is accepted separately in ADR-016.
