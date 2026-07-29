# Restructure plan: Modern + Classic on a shared engine

Status: Stage 1 done (engine extracted, committed on branch `restructure/engine-extraction`); Stages 2
onward still to do. This is the migration plan agreed after establishing that Minecraft
1.7.10/1.8.9/1.12.2 support is viable but needs a second toolchain and a Java 8 engine. Read
`platforms/forge-1.7.10/PORTING.md` for the original 1.7.10 findings that led here.

## Why

Everything below was verified empirically this session, not assumed:

- **One toolchain covers all three legacy versions.** [unimined](https://github.com/unimined/Unimined)
  1.4.1 built **1.7.10, 1.8.9, and 1.12.2** to completion (Forge, reobfuscated jars). Essential Loom
  floors at 1.8.9 (no 1.7.10); RFG rejects everything except 1.7.10 and 1.12.2. So unimined is the
  single Classic toolchain, and the RFG 1.7.10 scaffold gets **retired**.
- **Modern and Classic cannot be one Gradle build.** Modern runs on Gradle 9.x / Java 25; unimined
  rejects Gradle 9 and needs Gradle 8.8 on a JDK 22-or-older daemon (JDK 21 here). Two independent
  builds, linked only by shared **engine source**.
- **The engine can't be shared as Java 17 source.** Jabel downlevels sugar (switch expressions, `var`,
  text blocks) but not records. A record extends `java.lang.Record`, absent on a Java 8 JVM, so it
  fails to load. The engine has **38 record files**. A shared engine must therefore be **Java 8
  source**. This is a decision already taken: one engine, de-recorded, over duplicating ~6.7k LOC.

## Target layout

```
minegasm/
  engine/            Java 8, no Minecraft. The shared brain + its unit tests. Published as a plain library.
  modern/            Today's root build: Stonecutter + Architectury Loom, MC 1.19.2-26.x, Java 17-25.
  classic/           "Minegasm Classic": one Gradle build (unimined, Gradle 8.8 / JDK 21), Forge only, Java 8.
    common/src/      Version-agnostic Minecraft glue, shared into each subproject via srcDir.
    1.7.10/          unimined subproject (cpw.mods.fml).
    1.8.9/           unimined subproject (net.minecraftforge.fml).
    1.12.2/          unimined subproject (net.minecraftforge.fml).
```

Classic uses per-version subprojects rather than Stonecutter preprocessing. See "How Classic spans its
three versions" below for why (a Gradle-version conflict between Stonecutter and unimined).

Both `modern/` and `classic/` depend on `engine/` and shade it into their jars. Naming: **Minegasm**
(modern) and **Minegasm Classic** (legacy). Same mod id `minegasm`, same config, same haptic behaviour;
only the Minecraft-facing layer differs.

## The engine boundary (already exists)

`.localbuild/build.ps1` compiles the engine standalone today, so the split is real, not aspirational.
Engine = everything under `net/minegasm` **except** the Minecraft layer (`neoforge/`, `fabric/`,
`forge/`, and the `mc/` shim). Concretely, these packages make up `engine/`:

`core · recipe · config · runtime · render · observe · buttplug · device · time · util · client · tools`

All twelve are MC-free (verified: zero `net.minecraft`/`com.mojang`/`org.lwjgl` imports). Engine
external dependencies:

- **Gson**: config store and the native Buttplug codec.
- **buttplug4j**: the `buttplug.b4j` provider only (the default backend). Compiled as its own slice,
  the way `.localbuild` does it, so the core still builds without it.
- **slf4j**: logging api.

Tests: all 18 files live under the engine (buttplug, client, config, observe, recipe, render).

## Build wiring

Engine is a **standalone Java 8 Gradle library**, published to `mavenLocal` for dev (a real Maven for
release). Both products consume it as an ordinary dependency and bundle it:

- **modern/**: its own Gradle 9 build. Compiles `engine/` via a Java 8 toolchain (Gradle toolchains
  let one build mix Java levels), or consumes the published jar; either way it keeps Loom's
  `include(...)` for the runtime bundle. Modern's platform code stays Java 17-25.
- **classic/**: its own Gradle 8.8 / unimined build. Consumes the engine jar and shades it (plus
  buttplug4j and Gson) with unimined's Shadow integration, because 1.7.10-era Forge has no jar-in-jar.

Keeping the engine a plain published library (rather than a composite `includeBuild`) sidesteps the
Gradle-version mismatch between the two products entirely.

## The Java 8 downport (the real work)

Mechanical, bounded, and behaviour-preserving. **Rule: keep every public accessor name identical**
(`intent.kind()`, `scene.layers()`, and so on) so modern's call sites and tests don't change.

- **Records become final classes** (38 files): private final fields, a canonical constructor,
  same-named accessors, plus `equals`/`hashCode`/`toString` where the record's defaults were relied on.
  An IDE "convert record to class" refactor does most of this.
- **Drop `sealed`/`permits`** (1 file in core; a few more across the engine). Sealedness is a
  compile-time guarantee only; nothing depends on it at runtime.
- **Convert switch expressions, `var`, and text blocks** to statement switches, explicit types, and
  concatenated strings. (Jabel could keep them, but once records force a downport pass, adding Jabel
  buys little and pulls in a JitPack build dependency. Convert them and keep the toolchain plain.)
- **Replace `List.of` / `Map.of` / `Stream.toList()`** and other Java 9+ API with
  `Collections.unmodifiable*`, `Arrays.asList`, `collect(Collectors.toList())`.
- **Native provider transport**: `buttplug/WebSocketTransport.java` uses `java.net.http.WebSocket`
  (Java 11+). Replace it with a Java 8 WebSocket (the Jetty client already bundled for buttplug4j, or
  nv-websocket-client). The default `b4j` backend is unaffected; this only restores the `native`
  fallback on Java 8.

## Classic Minecraft layer (rewrite, per version)

Shares nothing with modern's `neoforge` package. One observation/UI layer, version-adapted:

- Entrypoint: `@Mod` plus `@Mod.EventHandler` lifecycle. **1.7.10 uses `cpw.mods.fml`**; **1.8.9 and
  1.12.2 use `net.minecraftforge.fml`**. That package move at 1.8 is the main split inside Classic.
- Client tick via `FMLCommonHandler`/`TickEvent`; panic and connect on `KeyBinding` (no `KeyMapping`).
- Commands through `ICommand` / `ClientCommandHandler`. There's no brigadier before 1.13, so `/minegasm`
  and `/mg` are hand-parsed.
- Feedback in chat (`ChatComponentText` / `TextComponentString`); no toast system.
- Config via commands plus the JSON file first; an immediate-mode `GuiScreen` is optional polish.

**How Classic spans its three versions (resolved).** Stonecutter, modern's `//? if` preprocessor,
**cannot** drive unimined: they conflict on Gradle itself. Stonecutter 0.9.6 hard-requires Gradle 9 or
newer (`"Stonecutter requires at least Gradle 9"`); unimined 1.4.1 hard-requires Gradle 8 or older
(rejects 9, verified). No clean pairing exists: unimined's only Gradle-9 support is an unreleased
`feature/2.0-rewrite` branch, and pinning an old Gradle-8 Stonecutter is fragile. So **Classic does not
use Stonecutter.**

Instead, Classic is one Gradle build with **three single-version unimined subprojects**
(`classic/1.7.10`, `classic/1.8.9`, `classic/1.12.2`), each the exact single-version unimined setup
already proven green. They share the version-agnostic Minecraft glue through a common source directory
(`sourceSets.main.java.srcDir("../common/src")`); the small per-version differences (the `cpw.mods.fml`
vs `net.minecraftforge.fml` split, minor API drift) live in each subproject's own source. No
preprocessing needed, given how localized the differences are.

## Staged migration (each stage is a checkpoint that must stay green)

1. **Extract `engine/` at Java 17.** ✅ **Done** (branch `restructure/engine-extraction`). `git mv` of
   the twelve packages and tests into `engine/` (106 renames, history preserved); modern compiles the
   engine in-place via a `srcDir`. No behaviour change. Verified: `:26.2-neoforge:build` green, and
   `:26.2-neoforge:test` re-ran green (93 tests), and the standalone engine suite (`.localbuild -Test`)
   ran green (93 tests) from the new location. **Confirmed empirically that Stonecutter honours the
   added `srcDir`**, the key unknown for this stage.
2. **Downport `engine/` to Java 8.** ✅ **Done.** All records became plain classes, sealed hierarchies
   became plain interfaces, and the switch-expression/`var`/Java-9+-API constructs were converted to
   Java 8, keeping accessors and Gson field names identical (the config graph deserializes through a
   `ConfigValue` marker + all-args constructor). The JDK-WebSocket transport moved to the loader layer
   so the engine is `java.net.http`-free; Classic connects through buttplug4j. Verified: engine
   compiles at `--release 8` (major version 52) and its 93 tests pass, and the 26.2-neoforge build
   stays green consuming the downported engine.
3. **Stand up `classic/`.** 🔨 **In progress.** The unimined multi-version build exists and **all three
   versions (1.7.10, 1.8.9, 1.12.2) build green**, each producing a reobfuscated Forge jar that carries
   the shared engine as Java 8 bytecode, with the right entrypoint per version (`cpw.mods.fml` for
   1.7.10; `net.minecraftforge.fml`, shared via `classic/forge`, for 1.8.9 and 1.12.2). The Buttplug
   stack is now **shaded into the jars** (no jar-in-jar on this Forge): a `shade` configuration feeds
   the Gradle Shadow plugin, which relocates buttplug4j, Gson, Jetty, Jackson, and slf4j under
   `net.minegasm.shadow.*`, and `remapJar` reobfuscates the shaded fat jar. Verified on all three: the
   final jar bundles the engine plus the relocated libraries with no leakage at the original
   coordinates. The **per-version observation/UI layer is now written**: each version drives the shared
   `MinegasmClient` once per client tick, sampling continuous player state into a `ClientStateSnapshot`
   and emitting the discrete events that read reliably client-side on an unmodified server (attack,
   block break, placement, fishing bite), the same shape as the modern NeoForge sampler. It registers
   panic/connect key bindings and a hand-parsed `/minegasm` (plus `/mg`) command with chat feedback. The
   command parsing and the block→material classifier are Minecraft-free and shared through
   `classic/common`; the Minecraft glue is per version, because the client API diverges too far to share
   (`mc.player`/`mc.thePlayer`, `RayTraceResult`/`MovingObjectPosition`, the `BlockPos` package moves,
   block name/hardness accessors, `ICommand` method names, and the text-component types), so 1.8.9 and
   1.12.2 each carry their own `ClassicClientHandler` behind the shared `classic/forge` entrypoint, and
   1.7.10 carries its own under `cpw.mods.fml`. Each version also has an in-game settings screen behind
   the mods-list "Config" button (master enable, intensity, pause behavior, auto-connect/auto-scan,
   server URL, variation, recipe pack, mode, fatigue protection, stop-on-world-unload, allow-remote, a
   status line, connect/test — the same set the modern screen exposes, using modes and recipe packs
   rather than per-event toggles), wired through a per-version `guiFactory`
   (the 1.7.10/1.8.9 `mainConfigGuiClass()` form and the newer 1.12.2 `createConfigGui` form). Its edits
   go through a shared, Minecraft-free `ClassicConfigModel` that copies the existing config and swaps only
   the edited fields, so nothing the screen does not show is disturbed (checked with an MC-free
   round-trip test). Verified: all three compile at `--release 8` and build to reobfuscated jars carrying
   the new layer. The final in-game gate (a client drives a device through Intiface) needs Intiface +
   hardware, the same as the modern side.
4. **Retire `platforms/forge-1.7.10/`** (RFG), superseded by `classic/1.7.10`. ✅ **Done.** The scaffold
   was committed once so it stays recoverable from history, then removed; `classic/1.7.10` on unimined
   replaces it.

Stages 1 and 2 touch modern's working tree; nothing is committed without the modern suite passing first.

## Risks & open items

- **unimined is single-maintainer.** Mitigated by it being the only tool that covers all three, and by
  pinning exact versions (unimined 1.4.1, Gradle 8.8, forge/mcp coords per version, JDK 21 daemon).
- **De-record volume**: 38 files; mechanical but not zero. Confined to the engine module.
- **Native transport swap**: the one non-mechanical downport item.
- **Two Gradle/JDK combos in the repo**: modern (9.x/25, in `modern/`) and classic (8.8/21, in
  `classic/`). Addressed: the README documents both builds, and `classic/` pins its own JDK 21 daemon via
  `classic/gradle/gradle-daemon-jvm.properties` so its `./gradlew` works regardless of `JAVA_HOME`. The
  modern build now lives in `modern/`, matching the target layout (engine/ + modern/ + classic/).

## Verified this session (so it isn't re-litigated later)

- unimined 1.4.1 builds 1.7.10 ✅, 1.8.9 ✅, 1.12.2 ✅ (Forge, Gradle 8.8 / JDK 21, `release 8`).
- RFG 2.0.2 builds 1.7.10 ✅ and 1.12.2 ✅ but rejects 1.8.9 ("Unsupported MC version"). Superseded.
- Essential Loom floors at 1.8.9 (no 1.7.10).
- Jabel cannot make records run on Java 8, so a shared engine must be Java 8 source.
- Engine packages are MC-free and already compile standalone via `.localbuild`.
- Stonecutter 0.9.6 requires Gradle 9 or newer; unimined 1.4.1 requires Gradle 8 or older, so they
  can't share a build, and Classic uses per-version unimined subprojects, not Stonecutter.
