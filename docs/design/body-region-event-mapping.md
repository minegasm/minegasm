# Built-in event to body-region mapping

Status: draft default, pending a hardware feel pass. Written for later review.

This records why the shipped recipe packs tag a few events with a body region and leave the rest whole
body. The region mechanism itself (the `BodyRegion` type, the governor and renderer resolution, the device
setting, the editor UI, and pack authoring) is described where it lives in the code; this file is only about
the default choice of which built-in events get a region.

## The mapping

| Event | Region | Reason |
|-------|--------|--------|
| `XP_GAIN` | `GENITAL` | Reward, the sensation most users route to a primary toy |
| `ADVANCEMENT` | `GENITAL` | Same reward family |
| `FISHING_BITE` | `GENITAL` | The catch, a reward moment |
| everything else | `WHOLE_BODY` | Damage, warnings, activity texture, and ambient are meant to be felt broadly |

`HURT`, `EXPLOSION`, and `VITALITY` stay whole body on purpose: they are damage and health signals that
should reach everywhere, and keeping their warning-style layers whole body preserves the "own the role"
suppression a broad exclusive is supposed to have. `ATTACK`, `MINING_ACTIVE`, `BLOCK_BROKEN`, `PLACE`,
`HARVEST`, and `AMBIENT` have no natural bodily locus, so they stay whole body too.

It lives in `EventRegions` and is applied once, centrally, in `Recipes.scene`, so both built-in packs
(Balanced and Classic) inherit it. Custom packs are not affected: they author a region per layer through the
pack format and never pass through `EventRegions`.

## Why this is safe

Region only narrows delivery when a user assigns a device to a specific region. A whole-body device, which
is the default and what every untagged device resolves to, overlaps every region, so it keeps receiving
every effect. Nothing is ever muted by this mapping:

- Untagged setup: no change at all. Every effect reaches every device.
- A device tagged `GENITAL`: gets the reward pulses and every broad effect.
- A device tagged `NIPPLE`: gets every broad effect, but not the genital-scoped reward. It is not silenced,
  it just does not receive the one family of effects scoped elsewhere.

## What this is not

Minecraft events do not have a real anatomy. This maps by sensation intent, not body part, so it is a
judgement call, not a fact about the game. `GENITAL` for the three reward events is the only place the
default deviates from whole body at all; the conservative alternative was to tag nothing and ship the
mechanism for users and pack authors to opt into.

The intent is that a hardware feel pass, with more than one toy in different places, confirms or revises
this. Likely revisions to weigh then: whether the reward family should really be genital or something
gentler, whether level-up deserves its own treatment, and whether any activity texture (mining, harvest)
reads better on a specific region than whole body. None of that changes the mechanism, only this table.
