# How to test

Four layers, cheapest first. You can get real confidence in the device stack (Level 2) **without
Minecraft and without hardware**.

## Level 0: unit tests (JDK only, no Gradle, seconds)

The whole engine + protocol codec is Minecraft-independent and runs against an in-process fake
Buttplug server:

```powershell
pwsh .localbuild/build.ps1 -Test
```

Covers: normalization/curves, recipes/presets (legacy parity), aggregation (hurt merge, XP coalesce,
mining, vitality, fishing), mixer/scheduler (routing, caps, ducking, timing gap, **registry-generation
invalidation**, held-endpoint stop), provider connection-failure recovery, the Buttplug v4 codec (shapes validated against the buttplug Rust
source + buttplug4j 4.0.278), config/migration/import, and an end-to-end pipeline (hurt → every device,
pause stop/continue/freeze-resume behavior). Also compiles the buttplug4j provider against the real library.
The current result and test totals are reported by Gradle and CI.

## Level 1: build the mod (Gradle + NeoForge, Java 25)

```bash
./gradlew build            # active variant
./gradlew chiseledBuild    # all variants
```

Produces one jar per variant. This is the only way to compile `net.minegasm.neoforge` and confirm the
NeoForge event/screen APIs against the pinned build. Requires network on first run. (The MC/NeoForge
versions in `versions/dependencies/` follow the brief's 26.x line; substitute the actual versions you
target.)

### Install jars into your test instances

To iterate in-game across loaders/versions without copying jars by hand:

```bash
./gradlew installJars       # runs chiseledBuild, then copies each jar to its instance
```

One-time setup: copy `mods-install.env.example` to `mods-install.env` (gitignored) and set each
`<variant>=<mods folder>` line to the `mods` folder of the matching Minecraft instance. Keys are the
Stonecutter variant names (`26.2-neoforge`, `26.2-fabric`, `26.2-forge`, and the `26.1.2-*` trio);
values are raw paths (Windows backslashes need no escaping). Leave a line blank to skip that variant.

The task builds every variant, then for each variant with a target it removes any existing
`minegasm-*.jar` from that folder (so a version bump never leaves two Minegasm jars) and copies in the
new one. Other mods in the folder are untouched. Use `-x chiseledBuild` to copy already-built jars
without rebuilding.

## Level 2: Intiface, no Minecraft (the important one)

This validates connect → negotiate v4 → scan → device features → output → stop through the exact
provider/command path the engine uses.

1. Install **Intiface Central** and start its server (default `ws://127.0.0.1:12345`).
2. Add a **simulated device** in Intiface (its built-in device simulator, no hardware needed), or
   connect a real device.
3. Run the probe (`net.minegasm.tools.IntifaceProbe`):

   **buttplug4j backend (default in the mod), via Gradle**, which resolves the buttplug4j/Jetty deps:
   ```bash
   ./gradlew :26.2-neoforge:intifaceProbe --args="--backend buttplug4j --url ws://127.0.0.1:12345"
   ```

   **native backend, zero extra deps**, runnable straight from the JDK harness output:
   ```bash
   java -cp ".localbuild/out/classes;.localbuild/libs/gson-2.11.0.jar" \
        net.minegasm.tools.IntifaceProbe --url ws://127.0.0.1:12345
   ```
   (run `pwsh .localbuild/build.ps1` once first to produce `.localbuild/out/classes`)

   Flags: `--intensity 0.25 --durationMs 400 --scanMs 4000 --noPulse`.

**What to look for:** it prints the negotiated version (`v4.0`), each device's features with their
output kinds and ranges, then sends a gentle 25% pulse to every `Vibrate` feature and stops. Run it
against **both** backends; that cross-checks the native codec's wire shapes against a real server. If
`--backend native` lists devices identically to buttplug4j, the native codec is confirmed against live
Intiface.

## Level 3: in-game preflight checklist (brief §14)

Before a release, put the built jar in the matching client's `mods/` folder for each variant you're
shipping, then work through this list. A green unit test or Intiface-simulator run doesn't cover any
of it; it needs a real in-game pass with a device connected.

Run the full list on the current main release lines (see `README.md`'s Building section) across every
loader they support. For the older lines, a smoke test covers it unless something loader- or
version-specific changed: items 1-4, one representative event from item 10, and item 14.

1. Config screen opens (mods list on NeoForge/Forge; `key.minegasm.config` keybinding, or ModMenu, on
   Fabric).
2. Connect → scan: connected devices appear in the list, and output reaches all of them, not just the
   first.
3. **Test Device Output** produces a pulse.
4. Enable haptics; pick a recipe pack and mode.
5. `/minegasm stop` stops output immediately; `/minegasm resume` re-enables it. Neither needs operator
   permission.
6. `/minegasm status`, `connect`, `disconnect`, and `reconnect` report their result in chat, without
   operator permission.
7. `/minegasm enable`/`disable` toggles output (disable also stops active output).
8. `/minegasm test`: a normal-limit pulse, an `unsafe`-suffixed pulse at the unsafe limit, and
   rejection of anything past either cap.
9. `/minegasm trigger attack` fires the recipe pipeline. `/mg` behaves the same as `/minegasm` when
   the alias is available.
10. Each event fires once, promptly, at a sane intensity: hurt, attack, mining texture, block break
    (ore vs. plain), place, harvest, fishing bite, XP (+ level-up), advancement, vitality.
11. Advancement: earning one in-game fires it automatically (task/goal/challenge); joining a world
    doesn't replay past advancements.
12. Explosion: reachable only via `/minegasm trigger explosion` for now (no automatic acquisition
    yet); it ducks/interrupts active mining.
13. Mine continuously for 30 s: stays subtle, no backlog, and stops promptly when you stop mining.
14. Bind a **panic** key. Panic during heavy event flow stops everything immediately.
15. Pause modes: **Stop** stops output immediately and doesn't reassert; **Pause and resume** stops
    hardware while paused and resumes the scene at its remaining time; **Continue** never triggers a
    pause stop, and finite scenes still expire normally.
16. Pause, then Save and Quit: the world-exit policy still applies, independently of the pause mode.
17. Kill Intiface mid-output: the device stops buzzing immediately and the UI shows disconnected.
18. Reconnect with a reused device index: no stale command fires before you re-enable.
19. **Two devices** both react to an event, not just the first.

Record device names privately; publish only generic results.

### Issues found

A running log, not a per-variant scoreboard: append anything the checklist turns up, noting which
variant(s) it reproduces on. Remove or strike an entry once it's fixed, rather than resetting this
between releases.

- **Test Device Output, all variants**: intermittent, sometimes produces no pulse even when the
  button is active and the same device vibrates from gameplay in the same session. Pressing
  **Emergency Stop** then **Resume after emergency** once clears it for the rest of the session
  (workaround succeeds in every observed case). See Known issues in `docs/STATUS.md`.

## Tips

- Everything fails toward **stopped** (bounded queues, expiry, watchdog, universal stop); see
  `docs/SAFETY.md`.
- Start every hardware session at low `global.intensity`; the probe defaults to 25%.
- For protocol debugging without hardware, the fake server in `src/test/.../FakeButtplugServer.java`
  reproduces Intiface's v4 message shapes and fault cases.
