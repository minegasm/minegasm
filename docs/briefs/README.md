# Briefs & planning documents

This directory holds **briefs and planning documents**, the source specifications that drive the
implementation. It's the durable record of *what we set out to build and why*, kept separate from the
living design docs (`docs/ARCHITECTURE.md`, `docs/adr/`, …) that describe *how the code works today*.

## Executive summary

Minegasm is a full rewrite of the original Minegasm mod into a client-side, multi-device haptic
feedback engine for Minecraft Java Edition, driving Buttplug v4 devices through a local Intiface
server. The project's shape comes from a single planning document so far, the initial implementation
brief (0001): a semantic intent → scene → mixer → device pipeline, running on NeoForge, Fabric, and
Forge across several Minecraft versions, reproducing the legacy mod's triggers and modes on a new
engine rather than forking its code. As new briefs are accepted, update this section to reflect the
current standing direction across all of them; the per-brief write-ups under the index below cover
what each one specifically added or changed.

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

### 0001: Initial implementation brief

The senior-developer handoff brief this project was built from: a client-side, multi-device Minecraft
haptics mod targeting NeoForge, Java 25, Stonecraft, and Buttplug v4 via Intiface. Its appendices cover
the parity matrix, recipe catalog, Buttplug v4 mapping, build plan, test matrix, risks, architecture
decisions, and coding guidelines, alongside examples and diagram assets.

> **Naming note:** this brief predates the project's naming decision and uses the working name
> "Minegasm Next". The project is now named **`minegasm`** (package `net.minegasm`, AGPL-3.0); the
> original mod is referred to as *legacy Minegasm* (`com.therainbowville.minegasm`, source in the
> `minegasm-legacy` repo). See `docs/adr/ADR-001-rewrite-and-license.md`.
