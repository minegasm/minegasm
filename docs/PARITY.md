# Minegasm compatibility (Classic pack)

Minegasm reproduces the user-visible behaviour of legacy Minegasm (RainbowVille's
`com.therainbowville.minegasm`) as a **full rewrite** on a new engine. Values below are the
compatibility contract, taken from the legacy `AbstractVibrationState`/`VibrationState*` behaviour in
the `minegasm-legacy` repo (newest line reviewed). They are encoded as data in `recipe/Presets` (mode
intensities) and `recipe/ClassicRecipePack` (durations/envelope), and snapshot-tested in `PresetTest`.

## Mode intensity table (0..1, normalized from legacy 0..100)

| Event | NORMAL | MASOCHIST | HEDONIST | CUSTOM |
|---|---:|---:|---:|---:|
| attack | 0.60 | 0.00 | 0.60 | config |
| hurt | 0.00 | 1.00 | 0.10 | config |
| mine / block break | 0.80 | 0.00 | 0.80 | config |
| place | 0.20 | 0.00 | 0.20 | config |
| xp change | 1.00 | 0.00 | 1.00 | config |
| harvest | 0.10 | 0.00 | 0.20 | config |
| fishing | 0.50 | 0.00 | 0.50 | config |
| vitality | 0.00 | 0.10 | 0.10 | config |
| advancement | 1.00 | 0.00 | 1.00 | config |

A base of 0 disables the event in that mode (matching the legacy short-circuit). `ACCUMULATION` uses
the charge model in `recipe/AccumulationProcessor` instead of this table.

## Classic envelope (legacy feel)

Flat plateau at the mode base intensity, with a short `+0.20` "feedback" boost at the start of most
events, vibration-only, at the legacy durations:

| Event | Plateau | Boost window | Notes |
|---|---:|---:|---|
| attack | 3.0 s | 1.0 s | |
| hurt | 3.0 s | 1.0 s | off in NORMAL (base 0) |
| block break | 3.0 s | 1.0 s (ore only) | ore adds the boost window |
| place | 3.0 s | — | |
| xp | ~1–4 s | 1.0 s | legacy `ceil(ln(amount+0.5))` s, approximated from magnitude |
| advancement | 5 / 7 / 10 s | 1.5 s | task / goal / challenge |
| fishing | 1.5 s | — | |
| harvest | 0.15 s | — | |
| vitality | 3.0 s | 3.0 s | edge-triggered, mode-dependent condition |

Device level = the max active base across states (legacy `max(states)/100`), reproduced by the mixer
taking the max per endpoint.

## Accumulation (legacy charge)

Contributions per event: attack +5, hurt +10, ore break +1, plain break +0.25, place +0.5,
xp +amount/5, advancement +1, harvest +0.5; capacity 100. Legacy used a hold-then-step decay; this
uses the brief's continuous real-time decay (default 1.5/s), which is smoother and cannot grow
unbounded. Tunable in `accumulation` config.

## Legacy config migration

`LegacyMinegasmImporter` reads a legacy `minegasm-client.toml` (read-only), maps `serverUrl`,
`vibrate`→enabled, `mode`, and the `*Intensity` values (accepting both 0..1 and 0..100 scales), and
produces a **preview** plus a config that replays through the **Classic** pack for faithful
behaviour. It never enables experimental outputs.

## Known deltas from legacy Minegasm (intentional)

- Modern **Balanced** pack is the default for new users (shaped envelopes, mining texture, ducking);
  Classic is opt-in for migrating users.
- Mining is modelled as a continuous texture in Balanced; Classic keeps legacy break-only behaviour.
- Explosion and low-health warning are modern additions, config-gated.
- Exact "harvest" semantics should be re-pinned against a chosen commit in the `minegasm-legacy` repo at kickoff
  (brief open question #4).
