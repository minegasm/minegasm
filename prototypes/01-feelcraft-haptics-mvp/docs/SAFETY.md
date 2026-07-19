# Safety and comfort policy

This MVP targets pleasant haptic feedback, not punishment or pain.

## Enabled by default

- vibration
- small linear position movement where supported
- low-intensity oscillation

## Disabled by default

- constrict / squeeze
- temperature
- spray
- footsteps
- ambient continuous effects

## Mandatory stops

The mod sends StopAll on:

- no player/world
- client logout
- runtime stop
- provider close
- panic/config stop in future UI

## Rules

- Use event expiry; do not play stale damage after latency.
- Use scheduler timing gaps; do not spam Buttplug devices every Minecraft tick.
- Return position devices toward neutral after impulse effects.
- Keep mining texture subtle and latest-wins.
- Keep rotation and oscillation capped low until the user calibrates.
