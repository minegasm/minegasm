/**
 * The Forge mod entrypoint (brief §5.1, §H, docs/adr/ADR-011-add-forge-loader.md). Bootstraps the
 * loader-independent engine exactly like {@link net.minegasm.neoforge.MinegasmMod}, but wired to
 * Forge's own FML/event-bus API. It reuses the loader-agnostic Minecraft-facing classes that already
 * live in the shared {@code net.minegasm.neoforge} package (sampler, config/settings screens, list
 * widgets, provider factory) — those reference only vanilla Minecraft and pure-Java types, so
 * duplicating them here would just be sync-by-hand maintenance debt.
 *
 * <p>This package lives under {@code versions/<mc>-forge/src}, not the shared root {@code src}: Forge
 * types (e.g. {@code net.minecraftforge.*}) are only on the classpath for Forge variants, so a Forge
 * entrypoint in the shared tree would break every NeoForge build that also compiles it. Each
 * supported Minecraft line gets its own copy of this file rather than one shared file guarded by
 * Stonecutter conditionals, to avoid nesting a loader condition inside the existing per-Minecraft-
 * version conditionals this class already needs.
 *
 * <p><strong>Build note:</strong> compiled only by the Gradle + Stonecraft + Forge toolchain (Java
 * 25), not by the standalone JDK core harness in {@code .localbuild}. The event/context API here was
 * sourced from the real {@code MinecraftForge/MinecraftForge} 26.2 branch (the
 * {@code com.example.examplemod.ExampleMod} test fixture and the FML/event-bus sources), not guessed
 * from NeoForge's API — the two have diverged materially since the fork (e.g. Forge's event-bus-6
 * {@code BusGroup}/static {@code EventBus<T> BUS} pattern vs. NeoForge's {@code IEventBus}, and Forge
 * has no direct equivalent of NeoForge's {@code ClientStoppingEvent}). Still validate against the
 * pinned Forge build at kickoff (brief §2.4, §4.2, ADR-003).
 */
package net.minegasm.forge;
