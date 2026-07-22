/**
 * The Fabric mod entrypoint (brief §5.1, §H, docs/adr/ADR-012-add-fabric-loader.md). Bootstraps the
 * loader-independent engine exactly like {@link net.minegasm.neoforge.MinegasmMod}, but wired to
 * Fabric Loader + Fabric API. It reuses the loader-agnostic Minecraft-facing classes that already
 * live in the shared {@code net.minegasm.neoforge} package (sampler, config/settings screens, list
 * widgets, provider factory) — those reference only vanilla Minecraft and pure-Java types, so
 * duplicating them here would just be sync-by-hand maintenance debt.
 *
 * <p>Fabric has no built-in "config screen from the mods list" extension point (that convention
 * belongs to the third-party ModMenu mod, deliberately not taken on as a dependency here). Instead a
 * dedicated {@code key.minegasm.config} keybinding opens {@link net.minegasm.neoforge.MinegasmConfigScreen}
 * directly. Optional ModMenu integration is a documented follow-up, not implemented.
 *
 * <p>This package lives under {@code versions/<mc>-fabric/src}, not the shared root {@code src}: like
 * the Forge entrypoint, Fabric API types are only on the classpath for Fabric variants, so this stays
 * out of the tree every other loader compiles.
 *
 * <p><strong>Build note:</strong> compiled only by the Gradle + Stonecraft + Fabric toolchain (Java
 * 25), not by the standalone JDK core harness in {@code .localbuild}. The event/API classes here were
 * sourced from the real {@code FabricMC/fabric-loader} {@code 0.19.3} tag and the
 * {@code FabricMC/fabric-api} {@code 26.2} branch, not guessed from NeoForge/Forge's APIs — Fabric's
 * client lifecycle/command/key-mapping APIs are shaped very differently (event objects with
 * {@code Event<T>.register(...)}, a client-command source type distinct from vanilla
 * {@code CommandSourceStack}). Still validate against the pinned Fabric build at kickoff (brief §2.4,
 * §4.2, ADR-003).
 */
package net.minegasm.fabric;
