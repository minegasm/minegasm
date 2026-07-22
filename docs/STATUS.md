# Implementation status

Current implementation and verification status for Minegasm `1.0.0-beta.1`. Automated tests,
live Intiface tests, in-game tests, and physical-device tests are reported separately so that a
successful simulator or unit test is not presented as broader hardware validation.

## Automated verification

All six active variants — `26.1.2`/`26.2` × `neoforge`/`fabric`/`forge` — compile and pass the Gradle
test suite. A full `chiseledBuild` produces one distributable jar per variant (see
`docs/adr/ADR-012-add-fabric-loader.md`, `docs/adr/ADR-013-centralize-loader-entrypoints.md`). Forge
was unblocked by pinning Architectury Loom 1.17.491 (`docs/adr/ADR-011-add-forge-loader.md`).

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
license file). **Neither Fabric variant has been fully manually exercised in Minecraft yet** — unlike
NeoForge above, verification here is still mostly compile- and package-level.

The first real Fabric launch surfaced that Fabric API must be installed as a separate companion mod
(Fabric Loader's "missing dependency" screen otherwise) — expected, not a bug: Fabric API is declared
as a dependency in `fabric.mod.json` but never bundled into the Minegasm jar, unlike `buttplug4j`,
which is. See `README.md` for the exact required Fabric API versions per Minecraft line.

The config screen has no mods-list entry point on Fabric (no ModMenu integration); it opens via the
`key.minegasm.config` keybinding instead, which still needs manual confirmation in-game.

## Known issues

**Config screen "Test Device Output" button sometimes produces no pulse.** In some in-game sessions,
clicking the button (`MinegasmConfigScreen`) does nothing — no vibration — even though the button
renders as active (enabled, connected, a device with a Vibrate capability present) and the same
device demonstrably vibrates correctly from normal gameplay events (hurt, mining, etc.) in the same
session. Reproduced on both NeoForge and Fabric; reproduces on a fresh session's very first test
attempt as well as after other activity.

**Workaround:** press **Emergency Stop** then **Resume after emergency** on the config screen once;
"Test Device Output" then works normally for the rest of the session.

**Root cause not confirmed.** Investigated and ruled out: the screen pausing the game (still
reproduces with `pauseBehavior` set to `Continue`, which never engages the worker's pause gate at
all), and `FatigueGovernor` attenuation (the test pulse's `HapticRole.IMPACT` role is never
fatigue-attenuated, by design). Diagnosing further needs runtime evidence (e.g. temporary logging in
`MinegasmClient.testPulse`/`HapticWorker.cycle`/`FeatureScheduler.accept` during a live repro), not
more static code reading.

## CI and release automation

The Forgejo Actions workflow is implemented for Codeberg's `codeberg-medium-lazy` runner. It builds
and tests every active variant and publishes matching beta tags as Codeberg prereleases. **It has
been run successfully on Codeberg and published the `v1.0.0-beta.1` prerelease** — both the ordinary
push build and the tagged prerelease path are proven for the two-variant (NeoForge-only) workflow.

The workflow's dependency/licensing check is loader-aware (NeoForge/Forge's `jarjar/metadata.json` vs
Fabric's `fabric.mod.json` `jars` array; `docs/adr/ADR-012-add-fabric-loader.md`) and already covers
the Forge jar's `mods.toml` + jarjar format. A real Codeberg run across all six variants is still
pending — verified locally only so far.

## Forge loader (buildable)

Forge is a registered variant on both Minecraft lines (`26.2-forge`, `26.1.2-forge`); its entrypoint
lives in shared `src` behind a `//? if forge` guard like the other loaders (ADR-013). It was blocked by
a `gg.meza.stonecraft`/Architectury Loom incompatibility (`convertAccessWideners is final`); Loom
1.17.491 fixes it and is pinned via a `resolutionStrategy.force` in `settings.gradle.kts`. See
`docs/adr/ADR-011-add-forge-loader.md`. As with every variant, `chiseledBuild` (compile + test + jar)
passes locally; in-game verification on real hardware is still pending (below).

## Remaining beta validation and follow-ups

- Verify the updated (Fabric-aware) Forgejo workflow with a real Codeberg run, once the Fabric loader
  changes are committed and pushed — the `v1.0.0-beta.1` run predates them.
- Diagnose the "Test Device Output does nothing" known issue above with runtime evidence (logging
  during a live repro), since static code review ruled out the two leading theories without finding
  the actual cause.
- Manually exercise both Fabric variants in-game (config screen via keybind, commands, connection,
  panic/output) the same way NeoForge already has been.
- Decide whether to add optional ModMenu integration for a mods-list config screen entry on Fabric.
- Add real client-side acquisition hooks for advancement and nearby explosion events. Their intents,
  recipes, settings, and manual `/minegasm trigger` paths exist, but gameplay does not currently emit
  them automatically.
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
| Vibration output | Yes | Yes, simulated devices | Manual physical coverage not recorded |
| Position and rotation output | Renderer tests | Simulator coverage only | Physical verification pending |
| Legacy configuration import | Yes | Not applicable | Manual in-game confirmation pending |
| Forgejo release workflow | Defined | Not applicable | Proven on Codeberg for `v1.0.0-beta.1` (NeoForge-only); Fabric-aware update not yet run there |

## Fast verification commands

```powershell
.\gradlew.bat :26.1.2-neoforge:test :26.2-neoforge:test :26.1.2-fabric:test :26.2-fabric:test
.\gradlew.bat clean chiseledBuild --rerun-tasks --warning-mode all
```

See `docs/TESTING.md` for the Intiface probes and full manual acceptance matrix.
