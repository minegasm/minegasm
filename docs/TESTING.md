# How to test

Four layers, cheapest first. You can get real confidence in the device stack (Level 2) **without
Minecraft and without hardware**.

## Level 0 — Unit tests (JDK only, no Gradle, seconds)

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

## Level 1 — Build the mod (Gradle + NeoForge, Java 25)

```bash
./gradlew build            # active variant
./gradlew chiseledBuild    # all variants
```

Produces one jar per variant. This is the only way to compile `net.minegasm.neoforge` and confirm the
NeoForge event/screen APIs against the pinned build. Requires network on first run. (The MC/NeoForge
versions in `versions/dependencies/` follow the brief's 26.x line; substitute the actual versions you
target.)

## Level 2 — Intiface, no Minecraft (the important one)

This validates connect → negotiate v4 → scan → device features → output → stop through the exact
provider/command path the engine uses.

1. Install **Intiface Central** and start its server (default `ws://127.0.0.1:12345`).
2. Add a **simulated device** in Intiface (its built-in device simulator) — no hardware needed. Or
   connect a real device.
3. Run the probe (`net.minegasm.tools.IntifaceProbe`):

   **buttplug4j backend (default in the mod), via Gradle** — resolves the buttplug4j/Jetty deps:
   ```bash
   ./gradlew :26.2-neoforge:intifaceProbe --args="--backend buttplug4j --url ws://127.0.0.1:12345"
   ```

   **native backend, zero extra deps** — runnable straight from the JDK harness output:
   ```bash
   java -cp ".localbuild/out/classes;.localbuild/libs/gson-2.11.0.jar" \
        net.minegasm.tools.IntifaceProbe --url ws://127.0.0.1:12345
   ```
   (run `pwsh .localbuild/build.ps1` once first to produce `.localbuild/out/classes`)

   Flags: `--intensity 0.25 --durationMs 400 --scanMs 4000 --noPulse`.

**What to look for:** it prints the negotiated version (`v4.0`), each device's features with their
output kinds and ranges, then sends a gentle 25% pulse to every `Vibrate` feature and stops. Run it
against **both** backends — that cross-checks the native codec's wire shapes against a real server. If
`--backend native` lists devices identically to buttplug4j, the native codec is confirmed against live
Intiface.

## Level 3 — In-game (full manual matrix, brief §14)

1. Put the built jar in a NeoForge client's `mods/` (the MC/NeoForge version you built for).
2. Start Intiface; in-game open the config screen (mods list) → connect → scan → select device →
   **Test** → enable haptics → pick a recipe pack + mode. Bind a **panic** key. Also verify that
   `/minegasm stop` immediately stops output and `/minegasm resume` allows output again; these are
   client-side commands and must not require operator permission.
3. Verify `/minegasm status`, `/minegasm connect`, `/minegasm disconnect`, and
   `/minegasm reconnect` report their final result in chat without operator permission.
4. Verify `/minegasm test`, a command at the configured normal limit, a command at the configured
   unsafe limit with the `unsafe` suffix, and `/minegasm trigger attack`. Values over the normal
   limit without `unsafe` and values over the configured unsafe limit must be rejected; emergency
   stop must interrupt output immediately.
   Verify `/mg status` behaves identically when the alias is available.
5. Walk the acceptance checklist:
   - hurt, attack, mining texture, block break (ore vs plain), place, harvest, fishing bite, XP
     (+ level-up), advancement, vitality — each fires once, promptly, at sane intensity;
   - **two devices** both react to hurt (not just the first);
   - mine for 30 s: stays subtle, no backlog; an explosion interrupts;
   - **Stop** pause mode → output stops immediately and does not reassert;
   - **Pause and resume** → hardware stops while paused, then resumes the scene at its remaining time;
   - **Continue** → no pause-triggered stop; finite scenes still expire normally;
   - pause then Save and Quit → the independent world-exit policy is still applied;
   - kill Intiface mid-output → engine clears, UI shows disconnected;
   - reconnect with a reused device index → no stale command before re-enable;
   - **panic** during heavy event flow → everything stops.

Record device names privately; publish only generic results.

## Tips

- Everything fails toward **stopped** (bounded queues, expiry, watchdog, universal stop) — see
  `docs/SAFETY.md`.
- Start every hardware session at low `global.intensity`; the probe defaults to 25%.
- For protocol debugging without hardware, the fake server in `src/test/.../FakeButtplugServer.java`
  reproduces Intiface's v4 message shapes and fault cases.
