/**
 * The <strong>only</strong> package permitted to reference Minecraft and NeoForge types (brief §5.1,
 * §H). It is a thin observation/bootstrap adapter over the pure engine: it samples client-visible
 * state once per client tick into a {@link net.minegasm.observe.ClientStateSnapshot}, emits discrete
 * {@link net.minegasm.core.RawGameEvent}s, and forwards lifecycle signals — never sending device
 * commands directly.
 *
 * <p><strong>Build note:</strong> this package is compiled only by the Gradle + Stonecraft +
 * NeoForge toolchain (Java 25), not by the standalone JDK core harness in {@code .localbuild}. The
 * class/method names below target the 26.x Mojang mappings; the exact event signatures and client
 * field names must be validated against the pinned NeoForge build at kickoff (brief §2.4, §4.2,
 * ADR-003) and any version differences isolated here with Stonecutter guards.
 */
package net.minegasm.neoforge;
