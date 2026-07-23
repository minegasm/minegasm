# Changelog

All notable changes to Minegasm are documented in this file. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versioning follows
[SemVer](https://semver.org/) with `0.x.x` reserved for legacy Minegasm (see
`docs/adr/ADR-001-rewrite-and-license.md`).

## [Unreleased]

## [1.0.0-beta.2] - 2026-07-23

Adds Fabric and Forge as loaders alongside NeoForge, and extends support back to older Minecraft
lines (`1.21.1`, `1.20.1`, `1.19.2`), on top of the `26.2`/`26.1.2` NeoForge base from beta.1.

### Added

- **Fabric loader** support on both Minecraft lines (`26.2`, `26.1.2`), with `buttplug4j` bundled via
  Fabric's jar-in-jar `jars` manifest entry. Fabric API is a required companion mod and the config
  screen opens via the `key.minegasm.config` keybinding (no ModMenu integration yet). See
  `docs/adr/ADR-012-add-fabric-loader.md`.
- **Forge loader** support on both Minecraft lines (`26.2`, `26.1.2`), unblocked by pinning
  Architectury Loom `1.17.491`. See `docs/adr/ADR-011-add-forge-loader.md`.
- **Older Minecraft lines**: `1.21.1` (NeoForge, Fabric, Forge), `1.20.1` (Fabric, Forge), and `1.19.2`
  (Fabric, Forge) build alongside the 26.x lines via Stonecutter version guards over the real
  API-generation changes (advancement, toast, list-widget, key-mapping, and Forge/Fabric event APIs).
  1.20.1 and 1.19.2 run on Java 17, which required rewriting the loader-agnostic core's Java 21 switch
  type patterns to `instanceof` chains. This is behavior-preserving, verified by the same test suite
  passing on both Java 17 and Java 25. No separate NeoForge build is shipped for `1.20.1` (the tooling
  can't resolve its legacy `net.neoforged:forge` coordinates); instead NeoForge 1.20.1 loads the
  **Forge** jar directly. It is a near-verbatim Forge fork registering the `forge` mod, and the 1.20.1
  Forge build is compiled (floor `47.1.5`, classic no-arg constructor, classic config-screen registration) to load
  across the whole 1.20.1 Forge/NeoForge line. `1.19.2` predates NeoForge entirely (its first release
  was 1.20.1), so it is Fabric and Forge only, and it sits before the 1.20 UI rework: its screens and
  list widgets render through a `PoseStack` with the static `GuiComponent` draw helpers (not the 1.20+
  `GuiGraphics`), `Button` is constructed directly (`Button.builder` arrived in 1.19.4), the client
  command feedback takes a bare `Component` (not the 1.20+ `Supplier<Component>`), and `Entity.onGround`
  is still `isOnGround`. 1.19.2 also ships Gson 2.8.9, which predates Gson's record support, so the
  config record graph is (de)serialized through a `RecordTypeAdapterFactory` (registered on the config
  `Gson` in the loader-agnostic core, correct on every Gson version). The one gap: 1.19.2's
  `MultiPlayerGameMode` exposes no destroy-stage accessor, so the fine-grained mining-progress ramp is
  unavailable there (block-break events still fire). See `docs/STATUS.md`.
- **Quilt** runs the existing **Fabric** jar as-is. Quilt Loader loads it via `fabric.mod.json`, and
  the mod uses no loader-specific API beyond Fabric API, so no separate Quilt build is shipped
  (install the Fabric jar with the normal Fabric API mod). Likewise **NeoForge 1.20.1** runs the
  existing **Forge** jar (see the older-Minecraft-lines note above).
- `/minegasm enable` and `/minegasm disable` client commands to toggle master haptic output from
  chat, the same switch as the config screen's enable toggle; disabling also stops active output.
  Available under the `/mg` alias too, and requires no server permissions.
- **ModMenu** integration on Fabric: when ModMenu is installed, the config screen gets an entry in the
  mods list (in addition to the `key.minegasm.config` keybinding). ModMenu is an optional, compile-only
  dependency, never bundled or required at runtime, so the mod is unchanged without it.
- Automatic acquisition of the **advancement** event: earning an advancement in-game now raises the
  haptic event that was previously reachable only via `/minegasm trigger advancement`. Implemented
  with the vanilla client advancement listener so it works in singleplayer and on unmodified
  multiplayer servers, without mixins or reflection; the `task`/`goal`/`challenge` frame drives the
  recipe as before (`docs/adr/ADR-014-advancement-acquisition-via-client-listener.md`).

### Changed

- Loader entrypoints (`net.minegasm.<loader>.MinegasmMod`) centralized into shared source behind
  Stonecutter loader guards, one file per loader instead of one copy per Minecraft line; the two
  vanilla APIs that differ between 26.1.2 and 26.2 go through a `McCompat` shim. Both the Forgejo
  (Codeberg) and GitHub Actions workflows now build and test every registered variant. See
  `docs/adr/ADR-013-centralize-loader-entrypoints.md`.

### Known limitations

- **Nearby-explosion** acquisition is still pending; the event remains reachable only via
  `/minegasm trigger explosion`. It is an optional enhancement beyond strict legacy parity, and no
  client-side signal carrying explosion position and power is available without a mixin. Automatic
  acquisition is planned for `1.0.0-beta.3` via a **client-only** mixin on the explosion receive path
  (`docs/adr/ADR-015-explosion-acquisition-deferred-to-beta3.md`). Every other listed trigger fires
  automatically.
- In-game verification for this release was a relaxed smoke test across a sample of variants rather
  than the full preflight checklist on every line, per the beta relaxation in `docs/RELEASING.md`; no
  new issues surfaced.

## [1.0.0-beta.1] - 2026-07-21

Initial public beta. Full rewrite of RainbowVille's Minegasm on a new client-side, semantic haptic
engine, targeting NeoForge 26.2 and 26.1.2 on Java 25.

### Added

- Semantic intent → scene → mixer → device-feature rendering pipeline, driven by monotonic real
  time rather than tick counts.
- Two recipe packs: **Classic** (legacy Minegasm parity, see `docs/PARITY.md`) and **Balanced**
  (modern envelopes, mining texture, ducking), selectable per profile.
- Buttplug v4 device support via the `buttplug4j` client (default) or a dependency-free native
  WebSocket provider, selectable with `buttplug.client`.
- Pause/world-exit policies: **Stop**, **Pause and resume** (true freeze/resume of remaining
  scene and fatigue time), and **Continue**.
- Panic/emergency stop via a bindable key and `/minegasm stop`; `/minegasm resume` clears the
  panic latch. Additional client-side commands: `status`, `connect`, `disconnect`, `reconnect`,
  bounded `test [strength-percent] [duration-ms]` (with a separately configured `unsafe` tier),
  and `trigger <event>`. All are client-side and require no server permissions. `/mg` is
  available as a short alias tree when no conflicting command already owns that root.
- Legacy Minegasm TOML config import with a non-destructive preview and pre-import backup.
- First-run opt-in flow: haptics stay disabled, and only loopback Buttplug servers are allowed,
  until the user explicitly enables them (see `docs/SAFETY.md`).
- Config screen covering connection, device/feature selection, direct output test, recipe pack
  and mode selection, and pause/world-exit policy.
- Forgejo Actions workflow (`codeberg-medium-lazy`) that builds and tests both variants on every
  push and pull request, and publishes a Codeberg prerelease with both jars and a `SHA256SUMS`
  file for tags matching the built version and containing `-beta.`.
- GitHub Actions workflow on the GitHub mirror that builds and tests both variants on every push,
  pull request, and manual run; build/test verification only, no release publishing.

### Known limitations

- Advancement and nearby-explosion events are implemented end-to-end (intents, recipes,
  settings, and manual `/minegasm trigger`) but are **not yet raised automatically by gameplay**;
  real client-side acquisition hooks for these two events are still pending. Every other listed
  trigger fires automatically.
- Position/stroker and rotation output are exercised only against Buttplug simulators so far;
  treat them as experimental until validated on physical hardware.
- Per-device routing controls, position calibration UI, and diagnostics export are not yet
  implemented.
- The pinned NeoForge dependencies for both supported Minecraft versions are themselves beta
  builds; this beta's stability is bounded by theirs.
- The Forgejo release workflow has not yet completed a run on Codeberg's hosted runners; the
  ordinary push build and the tagged prerelease path are both unverified until then.

See `docs/STATUS.md` for the full verification breakdown (automated, live Intiface, and
in-game/physical) and `docs/TESTING.md` for how to reproduce it.

[Unreleased]: https://codeberg.org/minegasm/minegasm/compare/v1.0.0-beta.2...HEAD
[1.0.0-beta.2]: https://codeberg.org/minegasm/minegasm/compare/v1.0.0-beta.1...v1.0.0-beta.2
[1.0.0-beta.1]: https://codeberg.org/minegasm/minegasm/releases/tag/v1.0.0-beta.1
