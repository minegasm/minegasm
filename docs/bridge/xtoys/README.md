# XToys adapter

Forwards Minegasm's local bridge to [XToys](https://xtoys.app). Minegasm sends the current level per role
to this adapter over TCP; the adapter scales each role to an intensity and streams it to XToys over the
webhook's WebSocket, which the included script (`xtoys-minegasm.json`) matches by action name and uses to
drive your outputs.

Each role is device-independent (IMPACT, REWARD, TEXTURE, WARNING, AMBIENT, CONTROL).
The adapter exposes each role as its own XToys output instead of collapsing everything to one level, so
several actuators can run at once. It makes no device decisions: it sends role and intensity, and you
route each role to a toy in XToys. XToys' generic output is device-agnostic, so an output can drive a
vibrator, stroker, or rotator.

> **Do not route this adapter to an e-stim device.** XToys outputs are generic, so nothing physically
> stops you wiring a role to e-stim, but this adapter sends a plain 0..100 scene intensity with none of the
> safeguards a shock output needs: no separate arming step, no independent hard limits in the device's own
> units, no ramp or inter-pulse spacing, no whole-body budget (see
> `../../adr/ADR-016-electrostim-opt-in-modality.md` and `../../SAFETY.md`). E-stim is planned as its own
> reviewed, opt-in modality with those controls; until it lands, do not use this generic route for a shock
> device. An ordinary game event could deliver an unexpected level.

It builds to a single self-contained binary (one small pure-Go dependency, no runtime to install).
Because Minegasm supports several bridges at once, XToys can run alongside Buttplug and other bridge
integrations.

## Build

```
go build -o xtoys-adapter .
```

That produces one executable (`xtoys-adapter`, or `xtoys-adapter.exe` on Windows). The first build fetches
the WebSocket library; after that it builds offline.

## Set up XToys

The adapter holds one WebSocket to the webhook and writes a JSON message per role that changed:

```
wss://webhook.xtoys.app/<webhook-id>   <-  {"action":"minegasm-<role>","intensity":<0-100>}
```

You don't have to build the XToys script by hand. `xtoys-minegasm.json` in this folder is ready to load:

1. Open XToys and go to **My Scripts**, then **Load Script**, and paste the contents of
   `xtoys-minegasm.json` (or import the file).
2. The script adds six outputs, one per role: **IMPACT**, **REWARD**, **TEXTURE**, **WARNING**,
   **AMBIENT**, **CONTROL**. Connect a toy to each output you care about (add your toys in XToys and route
   them). You don't have to use all six; unrouted outputs are simply ignored, and you can send several
   roles to the same toy if you only have one.
3. Open the script's **Webhook** trigger and copy its webhook id.

Each `minegasm-<role>` action reads the `intensity` value (0..100) and sets that role's output, so
`intensity=0` releases it. The adapter zeros every role on a Minegasm stop/panic and when an output
snapshot's TTL lapses without a refresh.

## Run

```
xtoys-adapter -webhook YOUR_WEBHOOK_ID
```

Flags:

| flag | default | meaning |
|------|---------|---------|
| `-webhook` | (required) | the webhook id from the script's Webhook trigger |
| `-listen` | `127.0.0.1:12347` | TCP address Minegasm connects to |
| `-scale` | `100` | the `intensity` sent at full strength |
| `-min` | `20` | motor start threshold: any nonzero effect maps to at least this (`0` disables) |
| `-endpoint` | `wss://webhook.xtoys.app` | XToys webhook WebSocket base URL |
| `-verbose` | off | log every request |

The action names (`minegasm-impact`, `minegasm-reward`, and so on) are fixed to match the shipped
script, so there's no flag for them.

## Point Minegasm at it

In Minegasm's **Bridges** screen (or config), add a bridge whose URL is the adapter's listen address,
e.g. `tcp://127.0.0.1:12347`, and enable it. Start the adapter first, then launch the game.

## How intensity is derived

Minegasm sends the whole current level per role as one authoritative snapshot, so the adapter just
mirrors it: each role's level maps to an intensity and any role at 0 is off. A level of 0 sends 0; any
nonzero level maps into `[min, scale]`, so a faint effect still clears the motor's start threshold
instead of sending an imperceptible value. (A game hit can arrive near 0.04, which is a `4` without the
floor.) Only roles whose scaled value changed produce a message, and a snapshot where every role is
unchanged sends nothing, so a steady effect streams quietly. When several Minegasm clients are connected,
each role takes the strongest level across them. See `../PROTOCOL.md`.

Because every snapshot is the full state, an effect ending or being suppressed retracts as soon as its
role drops to 0; the adapter never tracks individual scenes or when they end. Priority and exclusivity
are resolved centrally in Minegasm before the snapshot is sent: within a role, a higher-priority
exclusive effect suppresses lower-priority ones, so a warning wins over ambient on the same role.
Cross-role behavior does not yet match native Buttplug's per-actuator ducking, where several roles
sharing one motor duck each other; on the bridge each role is a separate output and they run
independently. Full cross-backend parity is a known beta limitation.

## If the toy barely moves

Run with `-verbose` and read the `intensity=` values. A steady effect like `minegasm-texture` should sit
around 40-60. If those numbers look fine but the toy is weak or silent, the value isn't the problem, XToys
is:

- **Calibration.** Set the toy's own minimum/maximum in XToys so its usable range starts above its start
  threshold. Raise `-min` too if a role still reads low.
- **Multiple motors.** The shipped script already drives every motor of a multi-motor toy: each output's
  `setVolume` uses `"volumeChannel": "all"`. If you build your own action, set that or only the first motor
  runs.
