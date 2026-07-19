# Appendix D - Stonecraft and NeoForge build plan

## Bootstrap

1. Install Java 25 and a current Gradle-compatible IDE.
2. Generate/clone the current official Stonecraft template.
3. Configure only NeoForge loader variants:
   - `26.2-neoforge`
   - `26.1.2-neoforge`
4. Verify a minimal client mod launches for each.
5. Pin exact Stonecraft, Stonecutter, Gradle wrapper, NeoForge, and dependency versions.
6. Commit dependency lock/checksum information if supported.

## Build constraints

- No dynamic version (`+`) in protected/release branches.
- Java toolchain 25 in Gradle.
- UTF-8 compilation/resources.
- Mojang/default mappings.
- Reproducible archive timestamps/order where possible.
- Dependencies included using the loader-supported jar-in-jar/shading method; avoid class conflicts.
- Client-only mod metadata and entrypoint.

## Version isolation

Create a small version boundary for:

- NeoForge event registration/signature changes;
- Minecraft client field/method differences;
- advancement/fishing/mining observation differences;
- UI screen API differences;
- config API differences.

Core domain, Buttplug protocol DTOs, scheduler, recipes, and tests should compile identically across variants.

## CI jobs

```text
validate-wrapper
format-and-static-analysis
unit-tests
protocol-integration-tests
build-26.2-neoforge
build-26.1.2-neoforge
inspect-jars
license-report
```

## Local developer commands

The exact tasks depend on the template. Document in the repository after bootstrap:

- select active Stonecutter version;
- run client for each variant;
- run unit tests;
- run GameTests/client tests;
- build all variants;
- produce release artifacts.

Do not publish guessed commands in user documentation until they are verified against the pinned workspace.
