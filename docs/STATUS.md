# Implementation status

Current implementation and verification status for Minegasm `1.0.0-beta.1`. Automated tests,
live Intiface tests, in-game tests, and physical-device tests are reported separately so that a
successful simulator or unit test is not presented as broader hardware validation.

## Automated verification

Both `26.1.2-neoforge` and `26.2-neoforge` compile and pass the Gradle test suite. A full
`chiseledBuild` produces one distributable jar for each version.

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

## CI and release automation

The Forgejo Actions workflow is implemented for Codeberg's `codeberg-medium-lazy` runner. It builds
and tests both variants, checks packaged dependencies and licensing, generates SHA-256 checksums, and
publishes matching beta tags as Codeberg prereleases.

**The workflow has not yet been run successfully on Codeberg.** Hosted-runner access, repository
permissions, the ordinary push build, and the tagged prerelease path must all be verified before the
automation is considered proven.

## Remaining beta validation and follow-ups

- Run the Forgejo workflow on Codeberg, first for an ordinary push and then for the beta tag.
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
| Vibration output | Yes | Yes, simulated devices | Manual physical coverage not recorded |
| Position and rotation output | Renderer tests | Simulator coverage only | Physical verification pending |
| Legacy configuration import | Yes | Not applicable | Manual in-game confirmation pending |
| Forgejo release workflow | Defined | Not applicable | Codeberg run pending |

## Fast verification commands

```powershell
.\gradlew.bat :26.1.2-neoforge:test :26.2-neoforge:test
.\gradlew.bat clean chiseledBuild --rerun-tasks --warning-mode all
```

See `docs/TESTING.md` for the Intiface probes and full manual acceptance matrix.
