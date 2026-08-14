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

## Possible extension: grouping regions (a hierarchy)

Status: not built, captured here for the feel pass to decide.

Today `BodyRegion` is flat. The only grouping is `WHOLE_BODY`, which reaches everything. `overlaps` is "equal,
or either side is whole body" and `contains` is "whole body, or itself." So a region like `LOWER_BODY`, if it
were added now, would overlap only itself and whole body, not `GENITAL` or `ANAL`, which is not what a
grouping region should mean.

The model is built to grow into a hierarchy without changing anything that consumes it. The two relations
are already the seams; whole body is just the top of a tree that currently has one level. Generalize them
from the flat special case to a containment map, for example:

- `LOWER_BODY` contains `GENITAL`, `ANAL`, `PERINEAL`
- `UPPER_BODY` contains `NIPPLE`, `ORAL`
- `WHOLE_BODY` contains everything (the existing top)

Then `contains(a, b)` means a is an ancestor of b or equal to it, and `overlaps(a, b)` is
`contains(a, b) || contains(b, a)`. Everything else falls out with no other change:

- A device tagged `LOWER_BODY` receives genital and anal effects, since it contains both.
- An effect tagged `LOWER_BODY` reaches a genital-tagged device.
- A `LOWER_BODY` exclusive owns, and so suppresses, lower-priority genital and anal layers of the same role
  in the governor, since it contains their region. That is the intended "this warning owns the whole lower
  body" behavior, and it already works for whole body today.

Two things to keep in mind before building it. The flat `overlaps`/`contains` become hierarchy-aware (a
small ancestor map plus a walk), and every existing test has to still hold at the whole-body default, since
untagged setups must not change. And the groupings themselves are an anatomy and product call, not a code
question, so the taxonomy wants real hardware behind it. The mechanism is easy; the tree is the judgement.
