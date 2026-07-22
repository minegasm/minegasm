# ADR-012: Add Fabric alongside NeoForge

**Status:** accepted. **Superseded in part by ADR-013:** the "Restructuring forced by a second loader"
section below moves the loader entrypoints into per-version `versions/<mc>-<loader>/src` trees (one copy
per Minecraft line). ADR-013 returns them to a single shared-source copy per loader behind Stonecutter
`//? if <loader>` guards, once the plugin's loader constants were found. The rest of this ADR (Fabric
registration, entrypoint API shape, no mods-list config screen) still stands.

**Context.** ADR-002 scoped the MVP to NeoForge only, leaving the loader boundary
(`net.minegasm.neoforge` holds Minecraft/loader-specific code; everything else is loader-independent)
open for later loaders. An attempt to add Forge alongside NeoForge (ADR-011) is blocked by a
`gg.meza.stonecraft`/Architectury Loom incompatibility that breaks the whole build. Fabric was tried
next and has no such blocker: Fabric Loader `0.19.3` and Fabric API `0.155.2+26.2` / `0.155.2+26.1.2`
are current, stable releases for both target Minecraft lines, and the `gg.meza.stonecraft` plugin's
Fabric support path (`ModData.isFabric`, the `fabric_version`/`loader_version` dependency wiring) has
no equivalent warning or failure.

**Decision.** Register `fabric` alongside `neoforge` for both `26.2` and `26.1.2` in
`settings.gradle.kts`. Add a Fabric entrypoint at
`versions/<mc>-fabric/src/main/java/net/minegasm/fabric/MinegasmMod.java` (one copy per Minecraft
line, per the restructuring below), `fabric.mod.json` + `pack.mcmeta` resources, and
`loader_version`/`fabric_version` pins in `versions/dependencies/*.properties`. The entrypoint was
written directly against the real `FabricMC/fabric-loader` `0.19.3` tag and `FabricMC/fabric-api`
`26.2` branch sources, not guessed from the NeoForge/Forge entrypoints — Fabric's client API is a
different shape entirely: `ClientModInitializer.onInitializeClient()` instead of a DI constructor,
`Event<T>.register(...)` instead of an annotated event bus, `KeyMappingHelper.registerKeyMapping(...)`
(no separate "register category" event), `ClientLifecycleEvents.CLIENT_STARTED`/`CLIENT_STOPPING` for
startup/shutdown (a real equivalent of NeoForge's `ClientStoppingEvent`, unlike Forge which has none),
and `ClientCommandRegistrationCallback` running against `FabricClientCommandSource` — a distinct
source type from vanilla `CommandSourceStack` with `sendFeedback`/`sendError` instead of
`sendSuccess`/`sendFailure`, and `ClientCommands.literal(...)`/`argument(...)` instead of
`Commands.literal(...)`/`argument(...)`.

**No mods-list config screen.** Core Fabric has no extension point equivalent to NeoForge's
`IConfigScreenFactory` or Forge's `ConfigScreenHandler.ConfigScreenFactory` — that convention belongs
to the third-party ModMenu mod, deliberately not taken on as a dependency here (brief scope, ADR-010
local-first posture). Instead, a new `key.minegasm.config` keybinding opens
`MinegasmConfigScreen` directly. Optional ModMenu integration (implementing `ModMenuApi` as a soft
dependency) is a documented follow-up, not implemented.

**Restructuring forced by a second loader.** Adding Fabric exposed a real problem with the existing
layout: `net.minegasm.neoforge.MinegasmMod` — the one class in that package that actually imports
`net.neoforged.*` — lived in the *shared* `src` tree. Stonecutter feeds the entire shared tree into
every variant's compile, so the moment a Fabric (or Forge) variant existed, its compile also pulled in
this NeoForge-only file and failed (confirmed by reproducing the exact `package NeoForge does not
exist` error). Fixed by moving `MinegasmMod.java` out of shared source into
`versions/26.2-neoforge/src/.../MinegasmMod.java` and `versions/26.1.2-neoforge/src/.../MinegasmMod.java`
— one concrete copy per Minecraft line, each written directly against that line's vanilla Minecraft
API (no `//? if >=26.2` Stonecutter guard needed anymore, since each copy only ever targets one
version). The other seven classes in `net.minegasm.neoforge` (`MinecraftSampler`, the config/settings
screens, the list widgets, `ProviderFactory`) reference only vanilla Minecraft and pure-Java types, so
they stay in shared source and are imported directly by both the Fabric and (scaffolded) Forge
entrypoints rather than duplicated.

**Verification.** `./gradlew chiseledBuild` succeeds for all four active variants
(`26.2-neoforge`, `26.1.2-neoforge`, `26.2-fabric`, `26.1.2-fabric`), producing one jar each with the
bundled buttplug4j dependency and `META-INF/LICENSE-minegasm` present. Fabric jars record bundled
dependencies as `fabric.mod.json`'s `jars` array (Loom's Fabric jar-in-jar mechanism) rather than
NeoForge's `META-INF/jarjar/metadata.json`; `.forgejo/workflows/build.yml` and `.github/workflows/build.yml`
were updated to check the right format per artifact rather than assuming jarjar universally.

**Consequences.** Four supported variants now build and test cleanly. Fabric's config screen is only
reachable via keybind, not the mods list, until/unless ModMenu integration is added. In-game manual
verification (STATUS.md) still needs to happen for both Fabric variants, same as NeoForge.
