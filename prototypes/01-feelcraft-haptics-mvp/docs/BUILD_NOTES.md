# Build notes

## Stonecraft

The project uses Stonecraft plugin `1.12.2` and Stonecutter `0.8+`.

Stonecraft's docs state that it wires Stonecutter and Architectury to support multi-loader and multi-version Minecraft development from one workspace. This MVP only enables NeoForge in `settings.gradle.kts`.

## Minecraft / NeoForge versions

Targets:

- `26.2-neoforge`
  - `minecraft_version=26.2`
  - `neoforge_version=26.2.0.7-beta`
- `26.1.2-neoforge`
  - `minecraft_version=26.1.2`
  - `neoforge_version=26.1.2.12-beta`

These were selected from publicly indexed NeoForge version information available when the pack was generated. If Gradle cannot resolve a version, update only the matching file in `versions/dependencies/`.

## Expected adjustment points

If Stonecraft changes task names or property names, check:

- `settings.gradle.kts`
- `build.gradle.kts`
- `versions/dependencies/*.properties`

If NeoForge event names changed, check:

- `NeoForgeClientEvents.java`
- `MinecraftSampler.java`

The haptic core has no NeoForge imports and should not need changes when porting event adapters.
