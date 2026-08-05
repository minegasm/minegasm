<p align="center">
  <img src="modern/src/main/resources/icon.png" width="144" alt="Minegasm logo">
</p>

# Minegasm

**Haptic feedback for Minecraft Java Edition, through devices supported by
[Intiface](https://intiface.com/).**

[Downloads](https://codeberg.org/minegasm/minegasm/releases) |
[Safety](docs/SAFETY.md) |
[Troubleshooting](docs/TROUBLESHOOTING.md) |
[Developer guide](docs/DEVELOPER_GUIDE.md)

Minegasm watches what happens in your game and turns it into haptic feedback. It reacts when you take
damage, mine, fish, gain experience, and more. It is entirely client-side, so you can use it on a
multiplayer server without installing anything on the server.

The 1.0 line is still in beta. Output starts disabled, and you have to turn it on after connecting and
testing your devices. Start gently and bind the panic key before playing.

## What it does

- Reacts to both one-off events and ongoing activity, rather than playing the same pulse for everything.
- Includes five modes: **Action**, **Reaction**, **Immersion**, **Momentum**, and **Custom**. Each suits
  a different style of play.
- Drives every connected device, including vibration, oscillation, rotation, and position features.
- Comes with **Balanced** and **Classic** recipe packs and can load community-made JSON scene packs.
- Talks to Intiface using its own lightweight client by default. A buttplug4j client is also included
  in case you need it.
- Can send scenes to a local adapter for DIY hardware or other services.
- Imports configuration from the original `0.x` Minegasm line.
- Keeps everything local: there are no accounts, analytics, or telemetry.

## Supported versions

Pick the jar whose filename matches your Minecraft version and loader.

| Minecraft | Loaders | Java target |
|---|---|---:|
| 26.2, 26.1.2 | NeoForge, Fabric, Forge | 25 |
| 1.21.1 | NeoForge, Fabric, Forge | 21 |
| 1.20.1 | Fabric, Forge, NeoForge* | 17 |
| 1.19.2 | Fabric, Forge | 17 |
| 1.16.5 | Fabric, Forge | 8 |
| 1.12.2, 1.8.9, 1.7.10 | Forge | 8 |

\* There is no separate NeoForge build for 1.20.1. The NeoForge-labelled download is the compatible
Forge jar under a friendlier filename.

Fabric needs a matching version of [Fabric API](https://modrinth.com/mod/fabric-api/versions). Quilt
can run the Fabric jar with Fabric API. [Mod Menu](https://modrinth.com/mod/modmenu) is optional; it
simply adds Minegasm to Fabric's mod list.

The 26.x versions get the most thorough release testing. Older versions still build and are tested,
but receive a lighter in-game pass. The exact coverage is recorded in [the testing guide](docs/TESTING.md)
and [status page](docs/STATUS.md).

## Installation

1. Install [Intiface Central](https://intiface.com/) and start its server.
2. Download the right Minegasm jar from [Codeberg](https://codeberg.org/minegasm/minegasm/releases)
   and put it in your instance's `mods` folder.
3. On Fabric or Quilt, put the matching Fabric API jar there as well.
4. Start Minecraft and open Minegasm's settings:
   - On **NeoForge or Forge**, open it from the mod list.
   - On **Fabric or Quilt**, bind **Open Minegasm settings** under Controls > Minegasm. You can also
     reach it through Mod Menu if that is installed.
5. Connect, scan for devices, and try **Test Device Output** at a low intensity.
6. Bind the **panic** key, choose a mode and recipe pack, and enable haptics when you are ready.

Minegasm looks for Intiface at `ws://127.0.0.1:12345` and starts scanning automatically on a fresh
install. That does not enable gameplay output; the final opt-in is always yours. For now, all connected
devices receive output because the UI does not yet have per-device enable switches.

## Commands

These are client-side commands, so they do not need server permissions. `/mg` is a shortcut for
`/minegasm` unless something else already uses that name.

| Command | What it does |
|---|---|
| `/minegasm status` | Shows the connection, devices, mode, and output state. |
| `/minegasm connect\|disconnect\|reconnect` | Controls the Intiface connection. |
| `/minegasm enable\|disable` | Turns gameplay output on or off. Disabling also stops current output. |
| `/minegasm stop` | Stops immediately and engages the panic latch. |
| `/minegasm resume` | Clears the panic latch. |
| `/minegasm mode [name]` | Shows or changes the current mode. |
| `/minegasm recipe [id]` | Shows or changes the current recipe pack. |
| `/minegasm test [strength-percent] [duration-ms] [unsafe]` | Sends a test pulse. |
| `/minegasm trigger <event>` | Triggers an event through the normal recipe system. |
| `/minegasm adapter [native\|buttplug4j]` | Shows or changes the Intiface client. |
| `/minegasm bridge [on\|off]` | Shows or changes the local bridge setting. |

Tab completion will show the available modes, recipes, and events. A test above your normal safety
limit needs the word `unsafe` at the end; it is still capped by a separate maximum.

## Scene packs and the local bridge

Most people can use one of the built-in recipe packs and ignore this section.

If you want to change how events feel, Minegasm can load JSON scene packs from
`<config>/minegasm/scene-packs/`. Select one in settings or with `/minegasm recipe <id>`. Pack values
are checked and clamped when they are loaded, so a pack cannot bypass the engine's safety limits.

The local bridge is for hardware and services that do not speak Buttplug. When enabled, it sends the
same scenes as newline-delimited JSON to an adapter running on your computer. Minegasm does not listen
for incoming connections, and the bridge is off by default. See the [bridge protocol](docs/bridge/PROTOCOL.md)
for the wire format and a small reference adapter.

## A note about safety

Minegasm controls real hardware. Treat the first run like you would any unfamiliar device: begin at a
low intensity, use the test button before enabling gameplay, and keep the panic key within reach.

The mod stops output when it disconnects, shuts down, loses its transport, trips its watchdog, or
receives a panic command. It also limits output strength and positional travel, expires old commands in
real time, and keeps its work queues bounded. Connections stay on your computer unless you explicitly
allow a remote address. [SAFETY.md](docs/SAFETY.md) explains these protections and their limits.

## Building it yourself

The code is split into three main parts:

```text
engine/   Minecraft-independent haptic engine, protocols, configuration, and tests
modern/   Stonecutter build for Minecraft 1.19.2 through 26.2
classic/  Unimined build for Minecraft 1.7.10 through 1.16.5
```

To build all modern jars:

```bash
cd modern
./gradlew chiseledBuild
```

To build all Classic jars:

```bash
cd classic
./gradlew build
```

The wrappers provision the Java toolchains they need. The first build will need network access to
download Minecraft, loader, and build-tool dependencies. On Windows, you can run the fast engine-only
test loop without Gradle:

```powershell
pwsh .localbuild/build.ps1 -Test
```

If you want to work on the project, start with the [developer guide](docs/DEVELOPER_GUIDE.md). The
[architecture notes](docs/ARCHITECTURE.md), [testing guide](docs/TESTING.md), and
[decision records](docs/adr/) cover the details without burying them here. Changes are listed in the
[changelog](CHANGELOG.md).

This repository uses AI-assisted development with human direction and review. The scope is documented
in [AI_DISCLOSURE.md](AI_DISCLOSURE.md).

## Where things stand

This is a beta, but it is not a prototype: the shared engine has unit and integration tests, and every
supported jar is built and packaged in the release workflow. Automated tests, a simulated Intiface
device, in-game checks, and physical-device tests are tracked separately so one is not mistaken for
another. See [docs/STATUS.md](docs/STATUS.md) for the current results.

One gameplay hook is still missing: nearby explosions do not fire automatically yet. The event and its
recipe work, and you can try them with `/minegasm trigger explosion`.

## License and history

Minegasm is available under the [GNU Affero General Public License v3.0](LICENSE).

This is a ground-up rewrite of RainbowVille's original Minegasm. It keeps familiar behavior and can
import the old configuration, but it uses a new engine and codebase. The original releases use `0.x`;
this rewrite starts at `1.x`. [ADR-001](docs/adr/ADR-001-rewrite-and-license.md) explains the history
and licensing decision.
