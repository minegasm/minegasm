/**
 * The Fabric mod entrypoint (brief §5.1, §H, docs/adr/ADR-012-add-fabric-loader.md). Bootstraps the
 * loader-independent engine exactly like {@link net.minegasm.neoforge.MinegasmMod}, but wired to
 * Fabric Loader + Fabric API. It reuses the loader-agnostic Minecraft-facing classes shared with the
 * other loaders (see {@link net.minegasm.neoforge} for what's shared and why) rather than duplicating
 * them here.
 *
 * <p>Fabric has no built-in "config screen from the mods list" extension point (that convention
 * belongs to the third-party ModMenu mod, deliberately not taken on as a dependency here). Instead a
 * dedicated {@code key.minegasm.config} keybinding opens {@link net.minegasm.neoforge.MinegasmConfigScreen}
 * directly. Optional ModMenu integration is a documented follow-up, not implemented.
 *
 * <p>The entrypoint lives in the shared root {@code src} behind a whole-file {@code //? if fabric}
 * Stonecutter loader guard (docs/adr/ADR-013): Fabric API types are only on the classpath for Fabric
 * variants, so the guard comments the entire class out of every non-Fabric compile. A single copy
 * serves both Minecraft lines. The one vanilla API that differs between them ({@code Minecraft}'s
 * screen setter and toast-manager accessor) is funnelled through {@link net.minegasm.neoforge.McCompat}.
 *
 * <p><strong>Build note:</strong> compiled only by the Gradle + Stonecraft + Fabric toolchain (Java
 * 25), not by the standalone JDK core harness in {@code .localbuild}. The event/API classes here were
 * sourced from the real {@code FabricMC/fabric-loader} {@code 0.19.3} tag and the
 * {@code FabricMC/fabric-api} {@code 26.2} branch, not guessed from NeoForge/Forge's APIs. Fabric's
 * client lifecycle/command/key-mapping APIs are shaped very differently (event objects with
 * {@code Event<T>.register(...)}, a client-command source type distinct from vanilla
 * {@code CommandSourceStack}). Still validate against the pinned Fabric build at kickoff (brief §2.4,
 * §4.2, ADR-003).
 */
package net.minegasm.fabric;
