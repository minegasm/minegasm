# Briefs & planning documents

This directory is the home for **briefs and planning documents** — the source specifications that
drive the implementation. It is the durable record of *what we set out to build and why*, kept
separate from the living design docs (`docs/ARCHITECTURE.md`, `docs/adr/`, …) which describe *how the
code works today*.

## Convention

- One folder per brief, prefixed with a zero-padded sequence number and a slug:
  `NNNN-short-slug/`. The initial brief is `0001-…`; the next planning document is `0002-…`, and so on.
- A simple, single-file plan can just be `NNNN-short-slug.md` instead of a folder.
- Do not edit a brief after it is accepted; if direction changes, add a new brief (or an ADR under
  `docs/adr/`) that supersedes it, and note the supersession here.
- Cross-cutting decisions that emerge from a brief are captured as ADRs in `docs/adr/`.

## Index

| # | Brief | Date | Status |
|---|---|---|---|
| 0001 | [Initial implementation brief](0001-initial-implementation-brief/MINEGASM_NEXT_IMPLEMENTATION_BRIEF.md) | 16 Jul 2026 | Accepted — implemented |

### 0001 — Initial implementation brief

The senior-developer handoff brief this project was built from: a client-side, multi-device Minecraft
haptics mod (NeoForge, Java 25, Stonecraft, Buttplug v4 via Intiface). The brief, its eight appendices
(parity matrix, recipe catalog, Buttplug v4 mapping, build plan, test matrix, risks, architecture
decisions, coding guidelines), examples, and diagram assets are preserved **verbatim, byte-for-byte**.

> **Naming note:** this brief predates the project's naming decision and uses the working name
> "Minegasm Next". The project is now named **`minegasm`** (package `net.minegasm`, AGPL-3.0); the
> original mod is referred to as *legacy Minegasm* (`com.therainbowville.minegasm`, source in the
> `minegasm-legacy` repo). See `docs/adr/ADR-001-rewrite-and-license.md`. The brief is kept unedited as
> a historical record.
