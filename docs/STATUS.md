# Implementation status

Current implementation and verification status for Minegasm `1.0.0-beta.1`. Automated tests,
live Intiface tests, in-game tests, and physical-device tests are reported separately so that a
successful simulator or unit test is not presented as broader hardware validation.

## Automated verification

All nine active variants — `26.1.2`/`26.2`/`1.21.1` × `neoforge`/`fabric`/`forge` — compile and pass
the Gradle test suite. A full `chiseledBuild` produces one distributable jar per variant (see
`docs/adr/ADR-012-add-fabric-loader.md`, `docs/adr/ADR-013-centralize-loader-entrypoints.md`). Forge
was unblocked by pinning Architectury Loom 1.17.491 (`docs/adr/ADR-011-add-forge-loader.md`); this also
covers Forge on 1.21.1, which Architectury's own runtime warning otherwise flags as unsupported.

### 1.21.1 (added after the 26.x lines)

1.21.1 sits many Minecraft releases behind 26.1.2/26.2, so — unlike the small, adjacent-version
`McCompat`-style shims between 26.1.2 and 26.2 — its shared-source compat guards cover real API
generation changes: `ResourceLocation`/`Identifier`, `KeyMapping`'s string vs. object category, the
`Screen`/`ObjectSelectionList.Entry` render pipeline (`render(GuiGraphics,...)` vs.
`extractRenderState`/`extractContent(GuiGraphicsExtractor,...)`), NeoForge's client-stopping event
(absent pre-26.1.2, so 1.21.1 uses a JVM shutdown hook like Forge), Forge's event-bus-6 static-`BUS`
API (absent pre-26.1.2, so 1.21.1 registers listeners on `context.getModEventBus()` /
`MinecraftForge.EVENT_BUS` instead), and Fabric API's pre-rename modules (`ClientCommandManager` not
`ClientCommands`, `KeyBindingHelper` not `KeyMappingHelper`). All three loaders compile, pass the unit
suite, and package (including jar-in-jar for the bundled Buttplug client) from a clean build.

ModMenu mods-list integration works on every Fabric variant including 1.21.1: its only available ModMenu
build (11.0.4) ships intermediary-mapped, so its dependency goes through Loom's `modCompileOnly` (which
remaps the mod jar to the project's mappings) rather than the plain `compileOnly` the mojmap-native 26.x
builds use. ModMenu stays compile-only and optional on all variants.

**In-game verification so far:** `testPulse` (device output) has been manually confirmed working on all
three 1.21.1 loaders (NeoForge, Fabric, Forge). The rest of the in-game manual matrix recorded below for
26.1.2/26.2 (config screens, connection, scanning, commands, panic/resume, legacy import, etc.) has not
yet been repeated on 1.21.1 — this build environment has no Minecraft client to drive further passes.

The automated suite covers:

- normalization curves, range scaling (including signed ranges), and deterministic variation;
- primitive evaluation, envelopes, beat timing, expiry, recipes, presets, and mode gating;
- accumulation decay and bounded charge;
- hurt merging, XP coalescing, mining continuity, vitality edges/repeats, fishing refractory periods,
  and respawn handling;
- mixer routing, caps, exclusive ducking, fatigue attenuation, and bounded scene ingress;
- scheduling, deadband, timing gaps, held-endpoint stops, and device-registry generation invalidation;
- pause policies, including true freeze/resume, and independent world-exit behavior;
- connection failure recovery, reconnect backoff, index reuse, stale-generation rejection, and
  connection-state guards that prevent scans or output while disconnected;
- panic/stop behavior and bounded normal/unsafe test-output profiles;
- Buttplug v4 encoding and tolerant parsing of device lists, server information, errors, malformed
  ranges, and unknown output kinds;
- native-provider and buttplug4j integration against the real buttplug4j `4.0.278` API;
- configuration round trips, corrupt-file recovery, atomic saves, first-run defaults, and legacy
  Minegasm TOML import with non-overwriting pre-import backups;
- end-to-end intent-to-device behavior, including output to every compatible device feature.

The native codec's message shapes were also checked field-by-field against the official
`buttplugio/buttplug` Rust implementation and buttplug4j `4.0.278` message classes. This confirmed
the v4 index-keyed `Devices` and `DeviceFeatures` objects, feature indices and descriptions, and
externally tagged output descriptors.

## Live Intiface verification

Both the default buttplug4j provider and the native fallback provider have been exercised against a
running Intiface Central server using simulated devices. The probes confirmed connection, v4
negotiation, scanning, feature discovery, vibration output, and stop behavior through the same
provider/command path used by the mod.

This confirms interoperability with live Intiface, but simulated devices do not replace testing on
physical hardware. In particular, position/stroker and rotation output still need broader
physical-device validation.

## Minecraft and NeoForge verification

The real Gradle, Stonecutter, and NeoForge toolchain compiles both supported variants against their
pinned Minecraft mappings. The generated jars have been loaded and manually exercised in Minecraft
26.1.2 and 26.2.

Manual testing has covered the configuration screens, editable settings, automatic connection and
scanning, device and session-error lists, direct output tests, panic/emergency stop, client commands,
configurable command alias, pause and world-exit policies, reconnection, and normal gameplay output.

NeoForge dependencies are currently pinned beta builds. Release notes must not imply greater
stability than those dependencies provide.

## Minecraft and Fabric verification

The real Gradle, Stonecutter, and Fabric Loader/API toolchain compiles both supported variants
against their pinned Minecraft mappings, and `jar`/`build` produce a correctly packaged mod jar for
each (entrypoint class, bundled buttplug4j dependency via Fabric's jar-in-jar `jars` manifest entry,
license file). Both Fabric variants have now been manually exercised in Minecraft (26.1.2 and 26.2)
with no new issues observed. (The pre-existing "Test Device Output" quirk under Known issues applies
to Fabric too.)

The first real Fabric launch surfaced that Fabric API must be installed as a separate companion mod
(Fabric Loader's "missing dependency" screen otherwise) — expected, not a bug: Fabric API is declared
as a dependency in `fabric.mod.json` but never bundled into the Minegasm jar, unlike `buttplug4j`,
which is. See `README.md` for the exact required Fabric API versions per Minecraft line.

The config screen opens on Fabric via the `key.minegasm.config` keybinding, and — when the optional
ModMenu is installed — from a mods-list entry via a compile-only ModMenu integration
(`net.minegasm.fabric.ModMenuIntegration`). Both paths have been confirmed working in-game.

## Known issues

**Config screen "Test Device Output" button sometimes produces no pulse.** In some in-game sessions,
clicking the button (`MinegasmConfigScreen`) does nothing — no vibration — even though the button
renders as active (enabled, connected, a device with a Vibrate capability present) and the same
device demonstrably vibrates correctly from normal gameplay events (hurt, mining, etc.) in the same
session. Reproduced on all three loaders (NeoForge, Fabric, Forge); reproduces on a fresh session's
very first test attempt as well as after other activity. Occurs only occasionally, not every session.

**Workaround:** press **Emergency Stop** then **Resume after emergency** on the config screen once;
"Test Device Output" then works normally for the rest of the session. This has cleared the issue in
every observed case.

**Root cause not confirmed.** Investigated and ruled out: the screen pausing the game (still
reproduces with `pauseBehavior` set to `Continue`, which never engages the worker's pause gate at
all), and `FatigueGovernor` attenuation (the test pulse's `HapticRole.IMPACT` role is never
fatigue-attenuated, by design). Diagnosing further needs runtime evidence (e.g. temporary logging in
`MinegasmClient.testPulse`/`HapticWorker.cycle`/`FeatureScheduler.accept` during a live repro), not
more static code reading.

## CI and release automation

The Forgejo Actions workflow is implemented for Codeberg's `codeberg-medium-lazy` runner. It builds
and tests every active variant and publishes matching beta tags as Codeberg prereleases. **It has
been run successfully on Codeberg and published the `v1.0.0-beta.1` prerelease**; the ordinary push
build and the tagged prerelease path are both proven there. The current all-six-variant
(`neoforge`/`fabric`/`forge`) workflow has since also been run green on Codeberg.

The workflow's dependency/licensing check is loader-aware (NeoForge/Forge's `jarjar/metadata.json` vs
Fabric's `fabric.mod.json` `jars` array; `docs/adr/ADR-012-add-fabric-loader.md`) and covers the Forge
jar's `mods.toml` + jarjar format. Both the Forgejo (Codeberg) and GitHub Actions workflows now run
green across all six variants (`26.2`/`26.1.2` × `neoforge`/`fabric`/`forge`).

## Forge loader (buildable)

Forge is a registered variant on both Minecraft lines (`26.2-forge`, `26.1.2-forge`); its entrypoint
lives in shared `src` behind a `//? if forge` guard like the other loaders (ADR-013). It was blocked by
a `gg.meza.stonecraft`/Architectury Loom incompatibility (`convertAccessWideners is final`); Loom
1.17.491 fixes it and is pinned via a `resolutionStrategy.force` in `settings.gradle.kts`. See
`docs/adr/ADR-011-add-forge-loader.md`. As with every variant, `chiseledBuild` (compile + test + jar)
passes locally; both Forge variants have also been manually exercised in Minecraft with no new issues
observed. (The pre-existing "Test Device Output" quirk under Known issues reproduces on Forge too.)

## Remaining beta validation and follow-ups

- Diagnose the "Test Device Output does nothing" known issue above with runtime evidence (logging
  during a live repro), since static code review ruled out the two leading theories without finding
  the actual cause.
- Manually exercise both Fabric variants in-game (config screen via keybind, commands, connection,
  panic/output) the same way NeoForge already has been.
- Advancement acquisition is now automatic via the vanilla client advancement listener
  (`docs/adr/ADR-014-advancement-acquisition-via-client-listener.md`); it still needs in-game
  confirmation (earn a `task`/`goal`/`challenge` advancement and feel the pulse; confirm joining a
  world does not replay past advancements). Nearby-explosion acquisition remains pending — its
  intent, recipe, settings, and manual `/minegasm trigger explosion` path exist, but gameplay does
  not emit it automatically (no mixin-free client signal carrying explosion position and power).
  Planned for `1.0.0-beta.3` via a client-only mixin on the explosion receive path
  (`docs/adr/ADR-015-explosion-acquisition-deferred-to-beta3.md`).
- Complete and record the gameplay acceptance matrix for both supported Minecraft versions.
- Manually confirm legacy TOML import in Minecraft with representative legacy configuration files.
- Confirm multi-device behavior with physical devices if testing so far used Intiface simulators.
- Test position/stroker and rotation output on suitable physical hardware; keep these outputs
  experimental until their calibration and stop behavior are verified.
- Per-device routing controls and position calibration UI remain follow-ups.
- Diagnostics export remains unimplemented.
- Stable-release automation remains intentionally disabled until the beta workflow has been proven.

## Verification summary

| Area | Automated | Live Intiface | In-game / physical |
|---|---|---|---|
| Core engine and recipes | Yes | Not applicable | Manually exercised in game |
| Native provider | Yes | Yes, simulated devices | Exercised through the mod |
| buttplug4j provider | Yes | Yes, simulated devices | Exercised through the mod |
| NeoForge 26.1.2 | Build and tests pass | Through provider | Jar loads and has been tested |
| NeoForge 26.2 | Build and tests pass | Through provider | Jar loads and has been tested |
| Fabric 26.1.2 | Build and tests pass | Through provider | Not yet manually tested |
| Fabric 26.2 | Build and tests pass | Through provider | Not yet manually tested |
| NeoForge 1.21.1 | Build and tests pass | Through provider | `testPulse` confirmed in-game |
| Fabric 1.21.1 | Build and tests pass | Through provider | `testPulse` confirmed in-game |
| Forge 1.21.1 | Build and tests pass | Through provider | `testPulse` confirmed in-game |
| Vibration output | Yes | Yes, simulated devices | Manual physical coverage not recorded |
| Position and rotation output | Renderer tests | Simulator coverage only | Physical verification pending |
| Legacy configuration import | Yes | Not applicable | Manual in-game confirmation pending |
| Forgejo release workflow | Defined | Not applicable | Proven on Codeberg (`v1.0.0-beta.1` prerelease; all-six-variant workflow since run green) |

## Fast verification commands

```powershell
.\gradlew.bat :26.1.2-neoforge:test :26.2-neoforge:test :26.1.2-fabric:test :26.2-fabric:test
.\gradlew.bat clean chiseledBuild --rerun-tasks --warning-mode all
```

See `docs/TESTING.md` for the Intiface probes and full manual acceptance matrix.
