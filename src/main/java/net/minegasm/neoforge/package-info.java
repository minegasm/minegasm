/**
 * The package permitted to reference Minecraft and NeoForge types (brief §5.1, §H). It is a thin
 * observation/bootstrap adapter over the pure engine: it samples client-visible state once per
 * client tick into a {@link net.minegasm.observe.ClientStateSnapshot}, emits discrete
 * {@link net.minegasm.core.RawGameEvent}s, and forwards lifecycle signals — never sending device
 * commands directly.
 *
 * <p>This shared-source package holds only the classes that reference vanilla Minecraft and pure-Java
 * types — {@code MinecraftSampler}, the config/settings screens, the list widgets,
 * {@code ProviderFactory} — so it compiles cleanly under every loader. The Forge and Fabric
 * entrypoints ({@code versions/<mc>-forge/} / {@code versions/<mc>-fabric/}, ADR-011/ADR-012) import
 * and reuse these directly rather than duplicating them under a loader-neutral package name.
 *
 * <p>{@code MinegasmMod} — the one class that actually touches NeoForge's own API (event bus,
 * lifecycle events, extension points) — is <strong>not</strong> in this shared package. It lives
 * per-version under {@code versions/26.2-neoforge/src/.../net/minegasm/neoforge/MinegasmMod.java} and
 * {@code versions/26.1.2-neoforge/src/.../net/minegasm/neoforge/MinegasmMod.java}: since this package
 * is now shared with Forge and Fabric variants too, a NeoForge-importing file in the shared tree would
 * break every non-NeoForge compile (the same reason the Forge/Fabric entrypoints live per-version
 * rather than in shared source).
 *
 * <p><strong>Build note:</strong> this package is compiled by the Gradle + Stonecraft toolchain (Java
 * 25) for every loader, not by the standalone JDK core harness in {@code .localbuild}. The class/
 * method names below target the 26.x Mojang mappings; the exact event signatures and client field
 * names must be validated against the pinned build at kickoff (brief §2.4, §4.2, ADR-003) and any
 * version differences isolated here with Stonecutter guards.
 */
package net.minegasm.neoforge;
