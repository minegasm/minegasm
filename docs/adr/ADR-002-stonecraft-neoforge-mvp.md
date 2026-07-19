# ADR-002: Stonecraft + NeoForge-only MVP, Java 25

**Status:** accepted.

**Decision.** Build from a current Stonecraft template with only the NeoForge loader, for the two
current lines `26.2-neoforge` and `26.1.2-neoforge`, on a Java 25 toolchain. Pin exact Stonecraft
(1.12.2), Stonecutter (0.8+), Gradle wrapper, and NeoForge build numbers; no dynamic `+` versions on
release branches. Use Mojang mappings. One jar per variant.

**Rationale.** Stonecutter gives a shared source set (the whole engine) compiled per version, with
version-specific code isolated in `neoforge/`. Fabric/Forge/Quilt and server components are non-goals
for the MVP but the boundaries permit adding a loader later.

**Consequences.** NeoForge 26.x builds are currently `-beta`; release notes must say so and CI must
pin the tested build. Core domain, protocol, scheduler, and recipes compile identically across
variants.
