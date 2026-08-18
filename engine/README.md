# Minegasm engine

The loader- and Minecraft-independent core: domain model, recipes and shareable scene packs, config,
mixer/scheduler/worker, the backend seam (Buttplug provider and the local bridge), and their
transports. No `net.minecraft` / `org.lwjgl` / Forge imports anywhere in here. This is the shared brain
both products build on:

- **Modern** (in `modern/`: Stonecutter + Loom, MC 1.19.2-26.x) compiles this source in-place via a
  `srcDir` pointing here.
- **Minegasm Classic** (in `classic/`: unimined, MC 1.7.10 through 1.16.5, Java 8) compiles it the same
  way, one `srcDir` per version.

This module is **Java 8 source** (records are plain final classes, `sealed` dropped, no Java 9+ API), so
the Java 8 Classic runtime can load it. Modern's Java 17-25 toolchain compiles the same source
unchanged. There is no separate engine build artifact: both products pull the source in directly.

## Building / testing standalone

`.localbuild/build.ps1 -Test` compiles this module and runs its JUnit suite with just a JDK, Gson, and
the JUnit console jar. No Gradle or Minecraft needed.

## Layout

`core · recipe · pack · config · runtime · render · observe · backend · buttplug · bridge · device ·
time · util · client · tools` under `src/main/java/net/minegasm/`, with the unit tests under
`src/test/java/net/minegasm/`.
