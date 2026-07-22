# Minegasm

Client-side, multi-device haptic feedback for **Minecraft Java Edition** (NeoForge, Fabric, and Forge,
26.2 and 26.1.x, Java 25), driving [Buttplug](https://buttplug.io/) v4 devices through a local
[Intiface](https://intiface.com/) server. It is a full-rewrite replacement for the original mod —
RainbowVille's Minegasm (`com.therainbowville.minegasm`, source in the `minegasm-legacy` repo) —
covering its user-visible triggers, modes, and config migration on a new semantic haptic engine.

> Mod id: **`minegasm`**, package **`net.minegasm`**, license **AGPL-3.0**. Versions start at
> **1.0.0** — legacy Minegasm was 0.x.x, so the lines never collide
> (see `docs/adr/ADR-001-rewrite-and-license.md`).

## What it does

1. Observes gameplay on the client thread (works on multiplayer servers that don't have the mod).
2. Turns short-lived events and continuous player state into device-independent **intents**.
3. Resolves intents into semantic **scenes** via a recipe pack (Classic parity or Balanced modern).
4. Mixes scenes with priority, expiry, ducking, coalescing, and fatigue protection.
5. Renders scenes to every enabled compatible Buttplug **feature** and schedules feature-level
   `OutputCmd`s on a dedicated worker using **monotonic real time** — never tick counts.
6. Applies the configured pause/world-exit policy and always stops on disconnect, shutdown,
   watchdog, or panic.

Pause behavior has three modes: **Stop** stops hardware and discards active scenes; **Pause and
resume** stops hardware, freezes remaining scene/fatigue time, and safely re-renders on unpause;
**Continue** leaves active output governed by its normal recipe expiry. A paused scene is discarded
instead of resumed if the world unloads, the device registry changes, or another safety stop occurs.

Devices are reached via the **buttplug4j** client (`4.0.278`, v4 feature-based spec) by default, with a
dependency-free JDK-WebSocket provider as a fallback/test backend — selectable with the
`buttplug.client` config key (`buttplug4j` | `native`). See `docs/adr/ADR-006-buttplug-v4-external-provider.md`.

Fresh installs automatically connect to local Intiface and start scanning. Haptic feedback remains
disabled until the user explicitly enables it; a first-run toast explains this opt-in. Existing
configuration files are not migrated or overwritten by these defaults.

See `docs/ARCHITECTURE.md` for the layered design and `docs/adr/` for the decisions behind it.

## Project status

This repository is built to the initial brief, archived in
[`docs/briefs/0001-initial-implementation-brief/`](docs/briefs/) — the brief and its appendices,
examples, and assets preserved verbatim (see `docs/briefs/README.md` for the planning-doc convention).
**Known gap:** the nearby-explosion event is fully implemented (intents, recipes, settings, manual
`/minegasm trigger`) but is not yet raised automatically by gameplay; every other listed trigger,
including advancement (`docs/adr/ADR-014-advancement-acquisition-via-client-listener.md`), now fires
automatically. See `CHANGELOG.md` and `docs/STATUS.md` for details.
The two pre-brief ideation prototypes (codename *Feelcraft*) are kept under
[`prototypes/`](prototypes/) for historical reference. The **pure engine**
(loader- and Minecraft-independent) is implemented and covered by a JUnit suite; the **NeoForge and
Fabric observation/UI layers** are written against the 26.x API and compiled by the Gradle+Stonecraft
toolchain (`docs/adr/ADR-012-add-fabric-loader.md`). Forge builds on both Minecraft lines too, unblocked
by pinning Architectury Loom 1.17.491 (`docs/adr/ADR-011-add-forge-loader.md`). See `docs/STATUS.md` for
exactly what is verified vs. what needs the Minecraft toolchain and hardware.

| Layer | Package | Built & tested here? |
|---|---|---|
| Domain model, math, clock | `net.minegasm.core`, `util`, `time`, `device` | ✅ compiled + unit-tested |
| Config, migration, legacy import | `net.minegasm.config` | ✅ compiled + unit-tested |
| Recipes, presets, accumulation | `net.minegasm.recipe` | ✅ compiled + unit-tested |
| Mixer, scheduler, worker, fatigue | `net.minegasm.runtime`, `render`, `observe` | ✅ compiled + unit-tested |
| Buttplug v4 provider + fake server (native fallback) | `net.minegasm.buttplug` | ✅ compiled + unit-tested |
| buttplug4j provider (default backend) | `net.minegasm.buttplug.b4j` | ✅ compiled vs buttplug4j 4.0.278 (needs Intiface + hardware to run) |
| Client glue | `net.minegasm.client` | ✅ compiled |
| Minecraft observation, UI (shared) | `net.minegasm.neoforge` | ✅ compiled, manually exercised in-game (see `docs/STATUS.md`) |
| NeoForge entrypoint | `net.minegasm.neoforge.MinegasmMod` (shared `src`, `//? if neoforge`) | ✅ compiled, manually exercised in-game |
| Fabric entrypoint | `net.minegasm.fabric.MinegasmMod` (shared `src`, `//? if fabric`) | ✅ compiled, packaged, manually exercised in-game |
| Forge entrypoint | `net.minegasm.forge.MinegasmMod` (shared `src`, `//? if forge`) | ✅ compiled, packaged, manually exercised in-game |

## Building

### The mod (Gradle + Stonecraft + NeoForge/Fabric/Forge, Java 25)

```bash
./gradlew build                # builds the active Stonecutter variant
./gradlew chiseledBuild        # builds all six variants (26.2/26.1.2 x neoforge/fabric/forge)
```

Artifacts are one jar per variant, e.g. `minegasm-1.0.0+mc26.2-neoforge.jar` or
`minegasm-1.0.0+mc26.2-fabric.jar`. Requires a JDK 25 toolchain (auto-provisioned by Gradle) and
network access to the NeoForge/Fabric/Forge/Architectury Maven repos on first run. Loader versions are
pinned in `versions/dependencies/` (NeoForge builds are currently `-beta`; note this in release notes
per the brief). Forge additionally pins Architectury Loom `1.17.491` in `settings.gradle.kts` (see
`docs/adr/ADR-011-add-forge-loader.md`).

### The pure core only (JDK 25, no Gradle) — fast inner loop

The engine has no Minecraft or Gradle dependency, so it can be compiled and unit-tested with just a
JDK plus Gson and the JUnit console jar (auto-downloaded into `.localbuild/libs`):

```powershell
pwsh .localbuild/build.ps1 -Test
```

This excludes `net.minegasm.neoforge` and the loader entrypoints (`net.minegasm.neoforge.MinegasmMod`,
`net.minegasm.fabric.MinegasmMod`, `net.minegasm.forge.MinegasmMod`), which need the Minecraft
classpath. Gradle and CI report the current result and test totals.

**Testing the real device path** (Intiface, no Minecraft or hardware needed) and the full in-game
matrix are described in **`docs/TESTING.md`** — including a standalone `intifaceProbe` harness
(`./gradlew :26.2-neoforge:intifaceProbe --args="--backend buttplug4j"`). The qualified command runs
only that variant. An unqualified `./gradlew intifaceProbe` selects every Stonecutter variant and
runs the probes sequentially, because Intiface may reject simultaneous client connections.

## Using it in-game

1. Install and open **Intiface Central**; start its server (default `ws://127.0.0.1:12345`).
2. On Fabric, also install **[Fabric API](https://modrinth.com/mod/fabric-api/versions)** matching
   your Minecraft version (`0.155.2+26.2` or `0.155.2+26.1.2`) in the same `mods` folder — it is a
   required dependency, declared in `fabric.mod.json` but never bundled into the Minegasm jar, the
   same as with any other Fabric mod that uses it.
3. Launch Minecraft with the mod. On NeoForge, open the config screen from the mods list; on Fabric
   (no mods-list entry point without ModMenu, ADR-012), bind and press **Open Minegasm settings**
   (Controls → Minegasm) instead.
4. Connect and scan; connected devices appear in the list and all of them receive output (there is
   no per-device selection in the UI yet). Run the device-output test.
5. Enable haptics and pick a recipe pack + mode. Bind a **panic** key (Controls → Minegasm).
   `/minegasm enable` and `/minegasm disable` toggle master haptic output from chat (the same switch
   as the config screen; disabling also stops any active output). `/minegasm stop` is the client-side
   panic fallback; `/minegasm resume` clears the panic latch. Connection controls are available
   through `/minegasm status`, `connect`, `disconnect`, and `reconnect`. These client-side commands do
   not require server permissions.
   Use `/minegasm test [strength-percent] [duration-ms]` for a bounded direct pulse, or
   `/minegasm trigger <event>` to exercise an event through the normal recipe pipeline.
   Tests above the configured normal limit require an explicit `unsafe` suffix and remain bounded by
   a separately configured unsafe ceiling. Settings provides profiles for both tiers; absolute bounds
   are 100% and 10 minutes.
   `/mg` is a short alias for the entire command tree when no other client or server command already
   owns that root; Minegasm never replaces a conflicting `/mg` command.

Haptics default to **off** until you complete setup, and only **loopback** servers are allowed
unless you explicitly opt in to remote (see `docs/SAFETY.md`).

## Safety

Physical hardware, so the engine fails toward **stopped**: bounded queues, real-time expiry, a
watchdog, universal stop on every lifecycle transition, conservative output caps, and experimental
gating for non-vibration outputs. Details in `docs/SAFETY.md`.

## License & provenance

**AGPL-3.0** (see `LICENSE`), matching legacy Minegasm so the two are license-compatible. This is a
full rewrite on a new engine, not a fork of the original code (which lives in the `minegasm-legacy`
repo); public behaviour and config names were used as a compatibility reference. The license and
provenance rationale are recorded in `docs/adr/ADR-001-rewrite-and-license.md`.
