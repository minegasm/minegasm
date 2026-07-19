# Minegasm Compatibility Changes

This package updates the earlier Feelcraft MVP scaffold with a Minegasm-compatible surface.

## Added

- `MinegasmMode`: `NORMAL`, `MASOCHIST`, `HEDONIST`, `CUSTOM`.
- `LegacyEventType`: `ATTACK`, `HURT`, `MINE`, `XP_CHANGE`, `HARVEST`, `VITALITY`.
- Preset intensity mapping matching Minegasm's public docs.
- Event sampling for attack, XP gain, harvest-like block break, and vitality.
- Scene resolution for attack snap, XP sparkle, harvest pop, and vitality pulse.
- `docs/MINEGASM_REPLACEMENT.md` and rendered PDF.

## Existing retained

- Buttplug WebSocket provider.
- Buttplug feature mapping.
- Tick buffer, aggregator, mixer, command scheduler.
- Mining texture, hurt impact, block-break pop, low-health warning.
- Stonecraft/NeoForge multi-version scaffold for 26.2 and 26.1.2.

## Public release TODO

- Replace hardcoded defaults with NeoForge config persistence.
- Add config UI and test buttons.
- Test with Intiface Central and device simulator.
- Verify current Minecraft mappings for `player.totalExperience` and `ClientTickEvent.Post` on both target versions.
