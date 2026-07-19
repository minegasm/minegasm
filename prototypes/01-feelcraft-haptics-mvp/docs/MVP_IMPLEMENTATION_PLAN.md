# MVP implementation plan

## Done in this scaffold

- Stonecraft multi-version NeoForge project layout.
- NeoForge client tick adapter.
- Client-side state sampler.
- Raw tick event buffer.
- Aggregator, scene resolver, mixer, scheduler.
- Buttplug v4 WebSocket provider.
- DeviceList to internal device/feature mapping.
- OutputCmd renderer for Vibrate, Oscillate, Rotate, Position, HwPositionWithDuration.
- StopAll lifecycle hooks.
- Markdown and PDF design documentation.

## Next code tasks

1. Add a Mod Menu / NeoForge config screen equivalent for connect/scan/test.
2. Persist `HapticConfig` to TOML or JSON under config directory.
3. Add a test scene button for light tap, strong thump, mining texture, and StopAll.
4. Add device calibration screen.
5. Replace block-break heuristic with a more reliable client event or packet-aware hook.
6. Add explosion detection.
7. Add unit tests around JSON parsing and scheduler behavior.
