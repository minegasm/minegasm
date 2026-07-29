# Developer guide: contributing to Minegasm

This guide is for a Java developer who has never touched Minecraft or its modding scene. By the end you
should understand what this mod does, how the codebase is laid out and why, how to build and run each
part, and how to make and test a change. It links out to the deeper docs (`ARCHITECTURE.md`,
`TESTING.md`, the ADRs) rather than repeating them.

You do need to be comfortable with Java and Gradle. You do not need any Minecraft-modding background;
the next section is a crash course.

## Table of contents

1. [What Minegasm is](#1-what-minegasm-is)
2. [Crash course: Minecraft modding](#2-crash-course-minecraft-modding)
3. [How this project is organized (and why)](#3-how-this-project-is-organized-and-why)
4. [The haptic pipeline, end to end](#4-the-haptic-pipeline-end-to-end)
5. [Setting up your machine](#5-setting-up-your-machine)
6. [Building and running](#6-building-and-running)
7. [Testing your changes](#7-testing-your-changes)
8. [Your first contribution](#8-your-first-contribution)
9. [Where things live](#9-where-things-live)
10. [Conventions and gotchas](#10-conventions-and-gotchas)
11. [Loaders and Minecraft versions, with 26.x as the reference](#11-loaders-and-minecraft-versions-with-26x-as-the-reference)
12. [Further reading](#12-further-reading)

---

## 1. What Minegasm is

Minegasm is a **client-side** mod that turns what happens in your Minecraft game into **haptic feedback**
on compatible devices. Mine a block, take damage, catch a fish, gain a level, and the mod drives a
connected device accordingly.

It does not talk to hardware directly. It speaks the **Buttplug** protocol to a local **Intiface** server
(a small desktop app), and Intiface owns the actual Bluetooth/USB devices. So the mod's job is: watch the
game, decide what should happen, and send commands to Intiface over a local WebSocket.

"Client-side" matters: the mod runs in the player's game, never on a Minecraft server, and it works on
normal multiplayer servers that have never heard of it. It only observes what any client can already see.

## 2. Crash course: Minecraft modding

If you have modded before, skim this. If not, read it; the rest of the guide assumes these ideas.

### The game runs on a loop

Minecraft runs a **client** (what the player sees) and, in multiplayer, a separate **server**. Both run a
**tick** loop at 20 ticks per second. A mod hooks into that loop to run code every tick. Minegasm samples
the player's state once per client tick and reacts. Single-player is really an integrated server plus a
client in one process; our mod only ever runs on the client side.

### Minecraft's code was obfuscated, so mods use "mappings"

For most of Minecraft's history Mojang shipped the game with obfuscated names (`net.minecraft.class_1234`,
methods like `func_71165_d`). To write readable mod code, toolchains apply **mappings** that rename things
to human names. That changed recently: the 25w snapshots that led into 26.x ship with Mojang's own real
names, so there is nothing to deobfuscate on 26.x and nothing to reobfuscate when you release. Every
version this project targets *except* 26.x is still obfuscated and still needs mappings. There are several
mapping sets, and which one you see depends on the version and toolchain:

- **MCP / SRG (searge)**: the old Forge-era names (`ClientPlayerEntity`, `func_71165_d`). Used by the
  legacy Classic versions here.
- **Mojang official ("mojmap")**: Mojang's own names, available from 1.14.4 up (`LocalPlayer`, `chat`,
  `Level`, `BlockState`). Modern-looking even on older versions. Used by 1.16.5 here and by the modern
  build.
- **Intermediary / Yarn**: Fabric's stable mapping set.

The important consequence: **the same Minecraft class has different names in different versions**, which
is why the code that touches Minecraft is written per version. On the obfuscated versions, when your mod
is packaged for release it is **reobfuscated** back to the names the shipped game uses, so it loads in
production; the build does this for you (`remapJar`). On 26.x the mapped names already are the shipped
names, so that step is a no-op. Section 11 walks the whole spread of versions and loaders with 26.x as the
baseline.

### Mod loaders

A **mod loader** is the framework that loads mods into the game. There are three you will hear about:

- **Forge**: the oldest, split into an early era (1.7.10-1.12.2, `cpw.mods.fml`, `mcmod.info`,
  `@Mod.EventHandler`) and a modern era (1.13+, `net.minecraftforge.fml`, `mods.toml`, an `@Mod`
  constructor).
- **Fabric**: lightweight, 1.14+ only, uses `fabric.mod.json` and a small `fabric-api`.
- **NeoForge**: a 2023 fork of Forge, used by the modern build for 1.20.2+.

Each loader has its own way to register a mod, hook the tick, add key bindings, register commands, and
show screens. That per-loader glue is small; the bulk of the logic is loader-independent (see section 3).

### Mixins

Sometimes a loader gives you no clean hook for what you need, and you have to modify Minecraft's own
bytecode at load time. **Mixin** is the library that does this: you write a small class that "injects"
into a target method. We use exactly one mixin, on 1.16.5 Forge, because that version has no event for
client-only commands (see `classic/1.16.5-forge/.../mixin/LocalPlayerChatMixin.java`). Mixins are powerful
and fragile; prefer a normal API hook whenever one exists.

### Resource files you will meet

- `mods.toml` (Forge 1.13+), `mcmod.info` (Forge <1.13), `fabric.mod.json` (Fabric): the mod's metadata
  (id, version, entrypoint, dependencies).
- `pack.mcmeta`: required at the jar root on 1.13+ if the jar ships any `assets/` (Minecraft treats it as
  a resource pack).
- `assets/<modid>/lang/*.lang` or `*.json`: translations, including key-binding names.

## 3. How this project is organized (and why)

The repo is three sibling modules plus shared docs:

```
minegasm/
  engine/    Java 8, no Minecraft. The shared brain and its unit tests.
  modern/    Stonecutter + Architectury Loom. MC 1.19.2-26.x, Java 17-25.
  classic/   unimined. Forge on 1.7.10/1.8.9/1.12.2 (Java 8) and Forge+Fabric on 1.16.5.
  docs/  .localbuild/  (repo-level)
```

The single most important idea in the whole project: **almost all of the logic lives in `engine/`, which
knows nothing about Minecraft.** It observes an abstract "client state snapshot" and emits abstract
"events," turns those into device commands, and talks to Intiface. The Minecraft-facing code is a thin
adapter that feeds the engine and drives its key bindings, commands, and config screen.

Why three modules instead of one:

- **`engine/` is Java 8 source.** The legacy Minecraft versions run on a Java 8 JVM, and Java 8 cannot
  load Java 17 features like records. So the shared brain is written in plain Java 8 (no records, no
  `var`, no `sealed`, no Java 9+ APIs) and both products compile the exact same source.
- **Modern and Classic cannot be one Gradle build.** Modern needs Gradle 9 / Java 25 (Stonecutter);
  Classic needs Gradle 8.8 on a JDK 21 daemon (unimined). They are two independent builds linked only by
  sharing the `engine/` source through a Gradle `srcDir`.
- **Classic spans four Minecraft versions and two loaders** as separate subprojects
  (`1.7.10-forge`, `1.8.9-forge`, `1.12.2-forge`, `1.16.5-forge`, `1.16.5-fabric`), because the
  preprocessor the modern build uses (Stonecutter) conflicts with unimined on the Gradle version. Each
  subproject is its own single-version build; they share the engine and a small `common/` of
  Minecraft-free glue.

`classic/PORTING.md` documents, in a table, every place the Minecraft API differs across the Classic
versions. Read it before touching any Classic Minecraft code.

## 4. The haptic pipeline, end to end

`docs/ARCHITECTURE.md` has the authoritative diagram and invariants. Here is the same flow in words, so
you can follow a single mining action through the code:

1. **Observe** (`engine/observe`, fed by the per-version sampler in the Minecraft layer). Once per client
   tick the sampler reads the player and world and produces a `ClientStateSnapshot` (health, food, XP,
   mining, on-fire, underwater, fishing, ...) plus discrete `RawGameEvent`s (attack, block break,
   placement, fishing bite). The sampler is the *only* part that differs per Minecraft version.
2. **Intent** (`observe` -> `core`). The observation layer turns raw events and state transitions into
   device-independent **intents** (an abstract "this should feel like a sharp impact of strength X").
3. **Recipe / scene** (`recipe`, `core`). A **recipe pack** (Classic-parity or Balanced) resolves intents
   into **scenes**: timed, prioritized bundles of haptic **layers** and **primitives**. Behaviour lives in
   data here, not in `if` branches (see `recipe/Presets`, `recipe/ClassicRecipePack`).
4. **Runtime / mix** (`runtime`, `render`). A single **haptic worker** thread mixes overlapping scenes by
   priority, applies expiry, fatigue protection, and safety caps, and schedules per-feature
   `OutputCommand`s. Everything is timed with `System.nanoTime()` via a `Clock`, not tick counts, so it
   behaves the same under lag.
5. **Provider** (`buttplug`). The provider encodes commands into the Buttplug protocol and sends them over
   a WebSocket to Intiface, which drives the device. The default backend is the `buttplug4j` library; a
   dependency-free JDK-WebSocket backend also exists.

`client/MinegasmClient` is the loader-independent facade that ties this together. The Minecraft layer
constructs one, feeds it snapshots and events each tick, and calls `panic()`, `connect()`, etc. from key
bindings and commands. If you understand `MinegasmClient`'s public methods, you understand the seam
between "Minecraft" and "engine."

## 5. Setting up your machine

You will need:

- **Git**, and a clone of this repo.
- **JDKs**: the builds provision most toolchains automatically, but you need a base JDK to run Gradle.
  - Modern build: **Java 25**.
  - Classic build: a **JDK 21** (Gradle 8.8 and unimined do not run on Java 25). The Classic build pins a
    JDK 21 daemon via `classic/gradle/gradle-daemon-jvm.properties`, so its `./gradlew` selects or
    provisions JDK 21 regardless of your `JAVA_HOME`.
  - Engine-only fast loop: any recent JDK.
- **An IDE**: IntelliJ IDEA is the norm for Minecraft mods. Import `modern/` and `classic/` as separate
  Gradle projects (they are separate builds). `engine/` opens as plain Java.
- **Intiface Central** (from intiface.com) for the real device path. You do not need hardware: Intiface
  has a built-in device simulator.

First network-dependent build is slow; it downloads Minecraft, the loader, and the mappings.

## 6. Building and running

There are three build surfaces. Match the JDK to the module.

### The engine only (fastest inner loop, no Gradle, no Minecraft)

The engine has no Minecraft or Gradle dependency, so it compiles and unit-tests with just a JDK, Gson,
and the JUnit console jar. From the repo root:

```powershell
pwsh .localbuild/build.ps1 -Test
```

This compiles `engine/` and runs its 18 test files. Use it whenever your change is inside `engine/`; it is
seconds, not minutes.

### The modern mod (Stonecraft + NeoForge/Fabric/Forge, Java 25)

```bash
cd modern
./gradlew build            # builds the active Stonecutter variant
./gradlew chiseledBuild    # builds every registered variant
```

Artifacts land in `modern/versions/<variant>/build/libs/`. See `docs/TESTING.md` for running a dev client.

### Minegasm Classic (unimined, JDK 21)

```bash
cd classic
./gradlew build            # builds all subprojects (1.7.10/1.8.9/1.12.2/1.16.5 x loaders)
./gradlew installJars      # builds all, then copies each jar into a mods folder (see below)
```

`installJars` reads `classic/mods-install.env` (gitignored; copy `mods-install.env.example`), one
`<subproject>=<mods folder>` line per instance, and copies each reobfuscated jar into the matching
Minecraft instance's `mods` folder, clearing any previous Minegasm jar for that version+loader first. That
is the fastest way to test in-game across versions.

To run a specific Classic version in-game you need a Minecraft launcher (PrismLauncher works well) with an
instance for that version and loader. Note the runtime prerequisites: **1.16.5 Fabric** needs Fabric API +
Mod Menu installed; **1.16.5 Forge** needs MixinBootstrap for the `/minegasm` command (key bindings and
the config screen work without it).

## 7. Testing your changes

Match the test to the layer you touched.

- **Engine logic** (`engine/`): unit tests via `pwsh .localbuild/build.ps1 -Test`, or run them from the
  modern build (`cd modern && ./gradlew :26.2-neoforge:test`, which compiles and runs the same suite).
  This covers the mixer, recipes, config round-trips, the Buttplug codec, and more, with no Minecraft.
- **The real device path** (no Minecraft, no hardware): the `intifaceProbe` harness drives a device
  through Intiface's simulator using the exact provider code the mod uses. See `docs/TESTING.md`
  (`cd modern && ./gradlew :26.2-neoforge:intifaceProbe --args="--backend buttplug4j"`).
- **In-game**: build and install (modern `installJars` in `stonecutter.gradle.kts`, or Classic
  `installJars`), launch the instance, and exercise the mod. The full preflight checklist is in
  `docs/TESTING.md`. The final gate (a client drives a device through Intiface) needs Intiface and a
  device or the simulator.

A useful rule: if your change is in `engine/`, it is unit-testable and you should add or update a test. If
it is in a Minecraft layer, it usually is not unit-testable and needs an in-game check.

## 8. Your first contribution

Two starting points, easiest first.

### A) Change how something feels (engine only, fully testable)

Say you want mining a block to feel a little stronger. The intensity for each gameplay event lives in
data, not code:

- Per-mode base intensities: `engine/src/main/java/net/minegasm/config/` and `recipe/Presets`.
- Per-event enablement and multiplier defaults: `config/HapticConfig` (`defaultEvents()`).
- How an event becomes a timed scene: `recipe/ClassicRecipePack` and `recipe/BalancedRecipePack`.

Make the change, run `pwsh .localbuild/build.ps1 -Test`, and add/adjust a test under
`engine/src/test/...`. Because it is all in the engine, you never open Minecraft to validate the logic.

### B) React to a new gameplay event (touches a Minecraft layer)

This is the fuller cross-layer path, and a good way to learn the seams:

1. Add the event to `core/GameEventKind` (the engine's vocabulary).
2. Emit it from the samplers. In the modern build that is `neoforge/MinecraftSampler`; in Classic it is
   each version's handler/sampler (`ClassicClientHandler` for the legacy versions, `Sampler16` for
   1.16.5). Consult `classic/PORTING.md` for the per-version API names.
3. Give it a default in `config/HapticConfig.defaultEvents()` and a feel in the recipe packs.
4. Unit-test the engine parts; test the sampler in-game.

Start with (A). It teaches the pipeline without the Minecraft-version complexity, and most balance work
lives there anyway.

## 9. Where things live

```
engine/src/main/java/net/minegasm/
  core/      Domain vocabulary: GameEventKind, HapticScene/Layer/Primitive, MaterialFeel, Priorities.
  observe/   ClientStateSnapshot, event buffering, state transitions -> intents.
  recipe/    Recipe packs + presets: events -> timed scenes (behaviour as data).
  runtime/   The haptic worker, mixer, scheduler, fatigue governor.
  render/    Scenes -> device output commands; safety caps.
  device/    Device registry, features, capability model.
  buttplug/  Providers (buttplug4j + native), codec, transport to Intiface.
  config/    HapticConfig (persisted), RuntimeConfig (runtime view), ConfigStore.
  client/    MinegasmClient: the loader-independent facade.
  time/      The Clock abstraction (monotonic time).

modern/src/main/java/net/minegasm/
  neoforge/  The modern observation/UI layer (sampler, screens, entrypoint). Stonecutter //? guards
             span 1.19.2-26.x. fabric/ and forge/ are thin entrypoints.

classic/
  common/          Minecraft-free glue shared by all Classic versions (command parser, classifier).
  forge/           net.minecraftforge.fml entrypoint shared by 1.8.9 and 1.12.2.
  1.7.10-forge/    cpw.mods.fml (its own entrypoint + handler).
  1.8.9-forge/     handler; shares forge/ entrypoint.
  1.12.2-forge/    handler; shares forge/ entrypoint.
  1.16.5-common/   Vanilla-facing 1.16.5 sampler, config screen, keybinds, commands (both loaders).
  1.16.5-forge/    Forge entrypoint + the client-command mixin.
  1.16.5-fabric/   Fabric entrypoint + Mod Menu integration.
  PORTING.md       Per-version Minecraft API differences (read this).
```

## 10. Conventions and gotchas

- **Commits use Conventional Commits**: `feat`, `fix`, `build`, `chore`, `ci`, `docs`, `refactor`, etc.,
  with an optional scope like `feat(classic): ...`. Match the surrounding history.
- **The engine is Java 8 source.** No records, no `var`, no `sealed`, no switch expressions, no
  `List.of`/`Stream.toList`/`java.net.http`. If you add engine code, keep it Java 8 or the Classic build
  breaks. Keep public accessor names stable; both products and the tests depend on them.
- **The mod is client-only.** Never touch a Minecraft client class from code that can run on a dedicated
  server. On loaders without a client-only flag (1.7.10, and the 1.16.5 `@Mod` constructor) the entrypoint
  guards the side explicitly before constructing anything client-facing.
- **Match the JDK to the module.** Modern is Java 25; Classic is JDK 21. Running the Classic build on
  Java 25 fails with an opaque "Unsupported class file major version 69"; the daemon pin usually prevents
  this, but if you override `JAVA_HOME` be aware.
- **Minecraft names differ per version.** Do not copy a Classic handler from one version to another
  without checking `PORTING.md`. When in doubt, `javap` the mapped jar that unimined produced under
  `classic/.gradle/unimined/...` to confirm a name, rather than guessing.
- **Legacy Forge has no jar-in-jar**, so Classic shades its dependencies (buttplug4j, Gson, Jetty,
  Jackson) into the jar with the Gradle Shadow plugin and strips `META-INF/versions/**` (the old class
  scanner cannot read Java 11+ multi-release entries).
- **1.16.5 specifics**: it ships `assets/`, so it needs `pack.mcmeta`; its Forge commands need a mixin,
  which needs MixinBootstrap installed (a shared launch-service jar, not a normal mod, so it is not a
  declared dependency).
- **Never push or amend without being asked.** Build locally, keep commits small and green.

## 11. Loaders and Minecraft versions, with 26.x as the reference

This project spans two loader families and nine version/loader combinations. The Minecraft-facing code
looks different in each, which is intimidating until you pick a fixed point and read everything else as a
delta from it. Use **26.x** as that fixed point. It ships with Mojang's own unobfuscated names and the
newest APIs, so its code is the most readable and the closest to current Minecraft documentation. Learn
what 26.x looks like, then learn each older line as "here is what changes."

For the legacy trio (1.7.10 / 1.8.9 / 1.12.2) the exhaustive, cell-by-cell matrix lives in
`classic/PORTING.md`. This section is the wider map: it includes the modern versions and the 1.16.5
middle ground, and it explains the *kinds* of things that move, so the matrix makes sense.

### The lay of the land

| Version | Loaders here | Mappings | Runtime Java | Build tool |
| --- | --- | --- | --- | --- |
| 1.7.10 | Forge (`cpw.mods.fml`) | MCP / searge | 8 | unimined |
| 1.8.9 | Forge | MCP / searge | 8 | unimined |
| 1.12.2 | Forge | MCP / searge | 8 | unimined |
| 1.16.5 | Forge, Fabric | mojmap (1.16-era names) | 8 | unimined |
| 1.19.2 | Forge, Fabric | mojmap | 17 | Stonecutter + Loom |
| 1.20.1 | Forge, Fabric | mojmap | 17 | Stonecutter + Loom |
| 1.21.1 | NeoForge, Forge, Fabric | mojmap | 21 | Stonecutter + Loom |
| 26.1.2 | NeoForge, Forge, Fabric | official (unobfuscated) | 25 | Stonecutter + Loom |
| 26.2 | NeoForge, Forge, Fabric | official (unobfuscated) | 25 | Stonecutter + Loom |

NeoForge only exists from 1.20.1 onward, and only its modern coordinates (1.21.1+) are wired up here;
1.20.1 and 1.19.2 are Forge + Fabric. Forge exists for every row. Fabric starts at 1.14, so it covers
1.16.5 up. The legacy trio is Forge-only because nothing else existed for those versions.

### The reference: what 26.x code looks like

On 26.x you are writing against current, documented Minecraft. The pieces this mod touches:

- **Screens** extend `net.minecraft.client.gui.screens.Screen`. You add widgets with
  `addRenderableWidget(...)` and the screen renders and routes input to them for you.
- **Widgets** are `Button` (built with `Button.builder(text, onPress).bounds(...).build()`), `EditBox`
  for text, `AbstractSliderButton` for sliders, and `ObjectSelectionList` for scrollable lists. All live
  under `net.minecraft.client.gui.components`.
- **Text** is `net.minecraft.network.chat.Component`: `Component.literal("Connect")` for a fixed string,
  `Component.translatable("minegasm.settings.save")` for a localized one.
- **Drawing** happens through the retained render pipeline: you override `extractRenderState(...)` and draw
  with the `GuiGraphicsExtractor` it hands you (`graphics.text(...)`, `graphics.centeredText(...)`), and a
  list entry draws itself in `extractContent(...)`. Lists clip their own contents.
- **The window** is `com.mojang.blaze3d.platform.Window` via `minecraft.getWindow()`
  (`getGuiScale()`, `getHeight()`).
- **Commands** are Brigadier trees registered from the client-command event. **Key bindings** are
  `KeyMapping`s registered from a key-registration event.
- **The loader** is NeoForge: metadata in `neoforge.mods.toml`, a `@Mod` constructor, mod-bus events, and
  `IConfigScreenFactory` for the mods-list "Config" button. The package root is `net.neoforged`.

Everything in `modern/src/main/java/net/minegasm/neoforge/` is written against this and then dialed back
for older versions with Stonecutter comments (see below). Despite the `neoforge` package name, the same
source compiles for Fabric and Forge on every modern version.

### Reading older code: what moves, and how

Going backward from 26.x, five things change. None of them touch the engine; they are all in the thin
Minecraft layer.

**1. Names (mappings).** 26.x uses Mojang's shipped names. 1.19.2 through 1.21.1 use the *same* names
via mojmap, so most modern code reads identically there; the differences are real API changes, not
renames. 1.16.5 also uses mojmap, but 1.16-era, so a few classes have older names (`TextComponent` instead
of `Component.literal`, `GuiComponent` for the static draw helpers). The legacy trio uses MCP/searge, an
entirely different naming set (`GuiScreen`, `GuiButton`, `fontRendererObj`, `displayString`), which is why
that code looks least like the rest. Fabric additionally runs against *intermediary* names at runtime, but
you still develop against mojmap here.

**2. Text.** `Component.literal(x)` (1.19+) becomes `new TextComponent(x)` (1.16.5-1.18), and on the
legacy trio becomes `ChatComponentText`/`IChatComponent` (1.7.10, 1.8.9) or `TextComponentString`/
`ITextComponent` (1.12.2). Localized text is `Component.translatable(...)` on modern; the Classic screens
just hardcode English strings.

**3. Screens and widgets.** `Screen` becomes `GuiScreen` on the legacy trio. `addRenderableWidget(...)`
becomes `addButton(...)` on 1.12.2 and 1.16.5, and plain `buttonList.add(...)` on 1.7.10/1.8.9 (no helper
there). `Button.builder(...)` is only 1.19.4+; 1.19.2 and 1.16.5 call `new Button(x, y, w, h, text,
onPress)`, and the legacy trio uses `GuiButton` with a mutable `displayString`. `EditBox` is `GuiTextField`
on the legacy trio (and its constructor even loses its id argument on 1.7.10). Sliders are
`AbstractSliderButton` on modern and 1.16.5 but Forge's `GuiSlider` on the legacy trio (from `cpw.mods.fml`
on 1.7.10, `net.minecraftforge.fml` on 1.8.9/1.12.2, reading `getValueInt()` vs `getValue()`). The font is
`this.font` on modern and 1.16.5, `fontRenderer` on 1.12.2, `fontRendererObj` on 1.7.10/1.8.9.

**4. Rendering.** The entry point and draw calls change with almost every era:

| Era | Render hook | Draw a string | Clip a region |
| --- | --- | --- | --- |
| 26.1.2+ | `extractRenderState(GuiGraphicsExtractor, ...)` | `graphics.text(font, ...)` | lists clip themselves |
| 1.20.1 - 26.1.1 | `render(GuiGraphics, ...)` | `graphics.drawString(font, ...)` | `graphics.enableScissor(...)` |
| 1.19.2, 1.16.5 | `render(PoseStack, ...)` | `GuiComponent.drawString(pose, font, ...)` | GL scissor (`GL11`) |
| legacy trio | `drawScreen(int, int, float)` | `drawString(fontRenderer, ...)` (instance) | GL scissor (`GL11`) |

A concrete consequence lives in the list widgets. On 1.21.1+ an `ObjectSelectionList` clips its own rows.
On 1.20.1 and 1.19.2 this mod turns off the list's background masks (they otherwise paint over the rows),
which also removes the built-in clipping, so the widget adds a scissor of its own. On the legacy trio there
is no list widget at all, so the Classic dashboard draws the device and error lists as plain text panels
with hand-rolled mouse-wheel scrolling. Same feature, three implementations, one reason: the list API only
arrived later.

**5. Registration, config screens, commands, keys (per loader).** This is where the *loader*, not just the
version, decides the shape:

- **Metadata and entry.** NeoForge reads `neoforge.mods.toml` and a `@Mod` constructor. Modern Forge
  reads `mods.toml` and a `@Mod` constructor. Legacy Forge reads `mcmod.info` and `@Mod.EventHandler`
  lifecycle methods, under `cpw.mods.fml` on 1.7.10 and `net.minecraftforge.fml` on 1.8.9/1.12.2. Fabric
  reads `fabric.mod.json` and a `ClientModInitializer` entry, on every version it supports.
- **The mods-list "Config" button.** NeoForge/Forge expose it through a config-screen factory
  (`IConfigScreenFactory` on NeoForge, `ConfigScreenHandler.ConfigScreenFactory` on modern Forge,
  `IModGuiFactory` on legacy Forge, whose shape even shifts between 1.7.10/1.8.9 and 1.12.2). Fabric has no
  such hook, so the config screen opens through optional **Mod Menu** (`ModMenuApi`) instead.
- **Commands.** Modern is Brigadier registered from a client-command event on both loaders. Forge on
  1.16.5 is the awkward one: it has Brigadier but no event for client-only commands, so this is the single
  place the mod uses a **Mixin** to inject one. The legacy trio predates Brigadier entirely and
  hand-implements `ICommand`, registered through `ClientCommandHandler`.
- **Client-only safety.** Modern loaders and 1.8.9/1.12.2 Forge have a client-side flag or dist marker, so
  the loader keeps the mod off dedicated servers. 1.7.10 Forge has no such flag, so its entrypoint guards
  the side by hand before constructing anything client-facing.

### How the two builds express all this

The split maps onto the two build tools:

- **Modern (`modern/`)** keeps a *single* source tree and selects an API branch per variant with
  **Stonecutter** preprocessor comments. A `//? if >=1.20.1 { ... }` block compiles only for the versions
  that match, and the rest is left commented. So the same file carries the `extractRenderState`,
  `render(GuiGraphics, ...)`, and `render(PoseStack, ...)` branches side by side, and Stonecutter picks one
  when it builds each variant. When you edit modern UI code you are editing every modern version at once,
  which is why those files look busy.
- **Classic (`classic/`)** does the opposite: each version/loader is a *separate subproject* with its own
  copy of the Minecraft-facing code, sharing only the engine and the Minecraft-free `common/`. There is no
  preprocessor; the differences are just different files. The 1.16.5 pair share a `1.16.5-common`
  subproject because both loaders speak the same 1.16.5 API.

If you are adding something to the Minecraft layer, the practical rule is: on modern, write it against 26.x
first, then add the older branches Stonecutter complains about; on Classic, copy the nearest sibling
version and fix up the names using `classic/PORTING.md`. When a mapped name is uncertain, confirm it with
`javap` against the mapped jar the build produced rather than guessing.

## 12. Further reading

- `docs/ARCHITECTURE.md` - the layered design, threading model, and key invariants.
- `docs/TESTING.md` - the testing levels and the full in-game preflight checklist.
- `docs/SAFETY.md` - the safety model (panic, caps, fatigue, stop-wins).
- `docs/adr/` - the decision records; ADR-002/003/004/005/006 explain the core design choices, and
  ADR-011/012/013 the loader work.
- `classic/PORTING.md` - the per-version Minecraft API map for the Classic build.
- `engine/README.md` - the standalone engine and its fast test loop.
- The root `README.md` - user-facing overview and the build/run quickstart.

Welcome aboard. Start in `engine/`, run the fast test loop, and read one ADR; that is the shortest path to
being productive here.
