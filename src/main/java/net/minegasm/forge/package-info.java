/**
 * The Forge mod entrypoint (brief §5.1, §H, docs/adr/ADR-011-add-forge-loader.md). Bootstraps the
 * loader-independent engine exactly like {@link net.minegasm.neoforge.MinegasmMod}, but wired to
 * Forge's own FML/event-bus API. It reuses the loader-agnostic Minecraft-facing classes shared with
 * the other loaders (see {@link net.minegasm.neoforge} for what's shared and why) rather than
 * duplicating them here.
 *
 * <p>The entrypoint lives in the shared root {@code src} behind a whole-file {@code //? if forge}
 * Stonecutter loader guard (docs/adr/ADR-013): Forge types ({@code net.minecraftforge.*}) are only on
 * the classpath for Forge variants, so the guard comments the entire class out of every non-Forge
 * compile. A single copy serves both Minecraft lines. The one vanilla API that differs between them
 * ({@code Minecraft}'s toast-manager accessor) is funnelled through
 * {@link net.minegasm.neoforge.McCompat}.
 *
 * <p><strong>Build note:</strong> compiled only by the Gradle + Stonecraft + Forge toolchain (Java
 * 25), not by the standalone JDK core harness in {@code .localbuild}. The event/context API here was
 * sourced from the real {@code MinecraftForge/MinecraftForge} 26.2 branch (the
 * {@code com.example.examplemod.ExampleMod} test fixture and the FML/event-bus sources), not guessed
 * from NeoForge's API. The two have diverged materially since the fork (e.g. Forge's event-bus-6
 * {@code BusGroup}/static {@code EventBus<T> BUS} pattern vs. NeoForge's {@code IEventBus}, and Forge
 * has no direct equivalent of NeoForge's {@code ClientStoppingEvent}). Still validate against the
 * pinned Forge build at kickoff (brief §2.4, §4.2, ADR-003).
 */
package net.minegasm.forge;
