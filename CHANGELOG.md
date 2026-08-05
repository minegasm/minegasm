# Changelog

All notable changes to Minegasm are documented in this file. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versioning follows
[SemVer](https://semver.org/) with `0.x.x` reserved for legacy Minegasm (see
`docs/adr/ADR-001-rewrite-and-license.md`).

## [Unreleased]

### Added

- **Shareable scene packs.** Recipe packs can now be authored as data: a JSON pack file maps game
  events to scene templates in the existing scene/layer/primitive vocabulary. Packs load from
  `<config>/minegasm/scene-packs/` and are selectable by id alongside the built-in Classic and Balanced
  packs. Both loaders present the same picker: a Scene Packs screen reached from the dashboard or the
  settings recipe row, plus `/minegasm recipe <id>`. Every surface offers the full list and stores the
  raw id. Import is fail-closed and
  clamps every value through the engine's caps, so a shared pack can never exceed them; a per-layer
  strength response lets a pack follow event strength. See brief 0003 §2 and
  `docs/adr/ADR-017-string-recipe-pack-identity.md`.
- **Multi-backend output.** Scenes fan out through a backend coordinator to every enabled output
  backend rather than only Buttplug. The Buttplug worker is one backend behind a `HapticBackend` seam;
  a stop reaches every backend. A Buttplug-only user sees no change. See brief 0003 §3 and
  `docs/adr/ADR-018-scene-level-central-governance.md`.
- **Local bridge.** An optional backend that streams each scene as newline-delimited JSON over loopback
  TCP to an adapter you run, the extension point for non-Buttplug outputs (DIY hardware, other
  services). Off by default, outbound and loopback-only (remote behind an explicit opt-in), with a
  bounded, one-in-flight send queue and a first-class stop. It works on every loader, Classic included,
  because the transport is plain Java 8 TCP; a `bridge.transport` selector leaves room for other
  transports. Toggle it from the settings screen (both loaders) or `/minegasm bridge on|off`.
  Wire protocol and a dependency-free reference adapter under `docs/bridge/`. See brief 0002 §4.3 and
  brief 0003 §3.4.
- **Native Buttplug provider on classic.** The `native` client backend (an alternative to `buttplug4j`
  that avoids its Jetty/Jackson stack) now works on the classic loaders too, and both dashboards carry
  the adapter toggle. Classic's Java 8 target has no built-in WebSocket client, so `native` there is a
  bundled, relocated Java-WebSocket transport rather than modern's JDK `java.net.http.WebSocket`; the
  inbound frame cap is configured the same way. Default stays `buttplug4j`. See
  `docs/adr/ADR-019-classic-native-provider-via-websocket-library.md`.
- **Reset to defaults.** A button in the settings screen (both loaders) resets the whole config to
  defaults, keeping only the master enable state. It backs up the current file to a timestamped sibling
  first, so repeated resets keep every earlier backup, and it takes two clicks to confirm.

### Changed

- **Stronger default haptics, and a working intensity slider.** Output was weak enough that many devices
  barely moved on gameplay events, and the intensity slider did little. The Balanced recipe now shapes
  each event's strength *before* applying user gain (intensity × per-event multiplier) rather than after,
  so the slider scales the result instead of being clamped away. Events a mode de-emphasizes still sit
  at the floor by design.
- **Per-device start-threshold.** A vibration-class output below a device's minimum is lifted so it
  registers on a motor with a dead zone. It lives in the mixer, so it covers every pack (Classic and
  file packs too), applies only to strength kinds (never position/strokers), and stays under the device
  cap and `SafetyCaps`. Default 0.22, editable per device in the Device Editor. It runs after fatigue, so
  a fatigued ambient holds at the threshold rather than fading to silence; set the device minimum to 0 to
  let fatigue duck it away entirely. The per-kind `SafetyCaps` were also raised from the conservative scaffold values (Oscillate
  0.5→0.9, Rotate 0.35→0.75, Constrict 0.3→0.6; Vibrate stays 1.0), still ordered by risk and still the
  hard backstop applied after all scaling.

### Fixed

- **Auto-reconnect now actually reconnects, and auto-connect keeps trying.** The reconnect policy was
  in the config and settings screen but nothing acted on it, so a dropped connection was never retried
  and a startup auto-connect that failed (Intiface not up yet) never tried again. A client-tick
  supervisor now retries a wanted connection with bounded exponential backoff and jitter. It runs at the
  main menu too, so a connection can come up before you load a world, and a manual disconnect stays
  disconnected instead of being reconnected against you.
- **The default `buttplug4j` backend now notices a dropped socket.** Its client library exposes no
  disconnect callback and its internal close is silent, so after Intiface closed or the link dropped the
  mod still reported "connected" and output silently went nowhere. The provider now reconciles its state
  against the library each tick, so a drop surfaces (and the reconnect supervisor can act on it).
- **Scanning no longer gets stuck.** A scan only ended when the server reported it finished, which some
  servers never do (they scan continuously, or find nothing when your device is already paired), so the
  status could sit on "scanning" forever. A scan now stops on its own after about ten seconds, which
  settles the status back to connected. Applies to both auto-scan and the manual scan button.
- **Native backend: devices found mid-scan now show up on their own.** The native provider ignored the
  server's device-added and device-removed notifications, so a toy that connected during a scan stayed
  invisible until you hit Refresh. It now re-reads the device list when the set changes. (The default
  `buttplug4j` backend already did this.)
- Loader metadata now pins the Minecraft dependency to the exact version each jar targets. The previous
  unbounded `>=` range let a jar built for one Minecraft version load on a newer one and crash on an API
  that version had removed (for example the 1.19.2 jar's `Button` constructor on 1.20.1). Companion
  floors were relaxed so a matching jar still loads on slightly older setups: fabric-api to any version,
  the Fabric loader floor to `0.14.0`, and the NeoForge floor to its major line.

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
