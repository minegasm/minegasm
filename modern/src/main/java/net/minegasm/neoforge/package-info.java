/**
 * The package permitted to reference Minecraft and NeoForge types (brief §5.1, §H). It is a thin
 * observation/bootstrap adapter over the pure engine: it samples client-visible state once per
 * client tick into a {@link net.minegasm.observe.ClientStateSnapshot}, emits discrete
 * {@link net.minegasm.core.RawGameEvent}s, and forwards lifecycle signals, never sending device
 * commands directly.
 *
 * <p>This shared-source package holds the classes that reference vanilla Minecraft and pure-Java
 * types ({@code MinecraftSampler}, the config/settings screens, the list widgets,
 * {@code ProviderFactory}), so it compiles cleanly under every loader. The Forge and Fabric
 * entrypoints ({@code net.minegasm.forge.MinegasmMod}, {@code net.minegasm.fabric.MinegasmMod})
 * import and reuse these directly rather than duplicating them under a loader-neutral package name.
 *
 * <p>{@code MinegasmMod}, the one class here that actually touches NeoForge's own API (event bus,
 * lifecycle events, extension points), also lives in this shared source tree, behind a whole-file
 * {@code //? if neoforge} Stonecutter guard, exactly like the Forge and Fabric entrypoints are guarded
 * by {@code //? if forge} and {@code //? if fabric} (docs/adr/ADR-013-centralize-loader-entrypoints.md).
 * The guard comments the entire file, including its NeoForge imports, out of every non-NeoForge
 * compile, so the shared package still compiles cleanly for Forge and Fabric variants.
 *
 * <p><strong>Build note:</strong> this package is compiled by the Gradle + Stonecraft toolchain (Java
 * 25) for every loader, not by the standalone JDK core harness in {@code .localbuild}. The class/
 * method names below target the 26.x Mojang mappings; the exact event signatures and client field
 * names must be validated against the pinned build at kickoff (brief §2.4, §4.2, ADR-003) and any
 * version differences isolated here with Stonecutter guards.
 */
package net.minegasm.neoforge;
