# Appendix B - Event and recipe catalog

Values below are starting recommendations, not hard safety guarantees. All are normalized before device calibration and caps.

## Priority bands

| Band | Values | Examples |
|---|---:|---|
| Control | 1000 | StopAll, disable, invariant failure |
| Critical | 100-110 | explosion shock, death/critical state |
| Combat | 80-95 | hurt, shield/fall impact, attack confirmation |
| Important notification | 60-75 | advancement, fishing bite, vitality warning |
| Reward/completion | 45-65 | block break, harvest, XP |
| Texture/action | 30-45 | mining, placement |
| Ambient | 0-25 | future footsteps/environment |

## Attack

- Trigger: confirmed local attack initiation.
- Classic: approximately upstream mode intensity for roughly its compatibility duration.
- Balanced vibration: 35-90 ms impulse, level 0.20-0.45; stronger for charged/critical attack if reliably known.
- Position: 2-6% calibrated travel snap, optional and disabled by default.
- Coalescing: one per target/tick; drop if expired after 180 ms.
- Conflict: may overlay mining; lower than received hurt.

## Hurt

- Trigger: effective-health decrease.
- Strength: reference damage configurable; smoothstep curve.
- Balanced vibration: 90-190 ms impact, level 0.30-0.80.
- Position: 5-16% travel push/rebound; neutral return.
- Merge: 80-120 ms window.
- Expiry: 250 ms.
- Conflict: ducks mining and low-priority rewards.

## Active mining

- Trigger: local destroy progress active.
- Update: 100-150 ms, latest-wins.
- Vibration: level 0.10-0.32; material modifies grain/density.
- Position: tiny 2-5% travel micro-segments only after calibration.
- Stop: target changed, action ended, block broken, pause/world change.
- Conflict: dropped/ducked by combat and explosions.

## Block break

- Trigger: attributed target block transitions to broken/replaced.
- Vibration: 50-100 ms completion pop, 0.20-0.52.
- Ore accent: optional second micro-beat or +0.08 level, rather than only longer flat output.
- Position: 3-8% travel pop.
- Expiry: 250 ms.
- Conflict: may overlay mining, then closes mining continuous state.

## Placement

- Trigger: confirmed successful block placement.
- Vibration: 40-80 ms, 0.12-0.30.
- Material modifier: metal/glass sharper, soft blocks lower.
- Expiry: 200 ms.

## Harvest

- Trigger: verified crop/harvest compatibility action.
- Vibration: light two-beat reward, 0.15-0.35.
- Avoid duplicate block-break scene; resolve to one combined completion scene.
- Expiry: 300 ms.

## Fishing bite

- Trigger: one bite transition.
- Pattern: 45 ms beat, 75 ms gap, 60 ms stronger beat.
- Level: 0.30-0.55.
- Refractory period: approximately 600 ms.
- Position: small tug/release, 5-10% travel.

## XP

- Coalesce window: 100-150 ms.
- Small: one 35-50 ms beat at 0.12-0.22.
- Medium: two beats.
- Large: three ascending beats, cap around 0.45.
- Level up: final 70-100 ms accent.
- Combat conflict: drop or defer only if still before expiry; do not play late.

## Advancement

- Task: three ascending beats over 350-500 ms.
- Goal: task pattern plus broad 100 ms accent.
- Challenge: stronger accent and optional 250 ms decaying rumble.
- Classic pack may reproduce legacy durations separately.

## Vitality

- Edge-trigger when predicate becomes true.
- Repeat no faster than 2-5 seconds depending preset.
- Full-vitality pattern: soft paired pulse 0.15-0.30.
- Critical pattern: distinct warning pair 0.30-0.50.
- Must not obscure hurt/combat signals.

## Explosion (enhancement beyond strict legacy parity)

- Shock: 100-150 ms, 0.55-0.90, priority 100, expiry 200 ms.
- Aftershock: 300-800 ms decay, 0.20-0.55.
- Distance and explosion power determine strength.
- Position: one bounded push/rebound, never full travel.
- Optional for MVP if event acquisition is reliable; otherwise phase immediately after parity.
