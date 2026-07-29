# Minegasm engine

The loader- and Minecraft-independent core: domain model, recipes, config, mixer/scheduler/worker, and
the Buttplug providers. No `net.minecraft` / `org.lwjgl` / Forge imports anywhere in here. This is the
shared brain both products build on:

- **Modern** (repo root: Stonecutter + Loom, MC 1.19.2-26.x) compiles this source in-place via a
  `srcDir` pointing here.
- **Minegasm Classic** (unimined, MC 1.7.10/1.8.9/1.12.2, Java 8) consumes it too.

Because Classic targets a Java 8 runtime, this module is on its way to Java 8 source (records become
classes, `sealed` dropped). See [`/RESTRUCTURE.md`](../RESTRUCTURE.md). It is still Java 17 source
today; the downport is Stage 2.

## Building / testing standalone

`.localbuild/build.ps1 -Test` compiles this module and runs its JUnit suite with just a JDK, Gson, and
the JUnit console jar. No Gradle or Minecraft needed. A dedicated Gradle module for `engine/` arrives
in Stage 2, when it becomes the published Java 8 library Classic depends on.

## Layout

`core · recipe · config · runtime · render · observe · buttplug · device · time · util · client · tools`
under `src/main/java/net/minegasm/`, with the unit tests under `src/test/java/net/minegasm/`.
