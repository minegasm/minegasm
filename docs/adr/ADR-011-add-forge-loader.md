# ADR-011: Add Forge alongside NeoForge; blocked on Architectury Loom

**Status:** resolved — Forge is now a registered, buildable variant on both Minecraft lines. **Update
(ADR-013):** the Architectury Loom incompatibility described below (`convertAccessWideners is final`) is
fixed in Loom **1.17.491** — one of several releases that postdate the 1.17.487 this ADR tested.
Pinning that version via a `resolutionStrategy.force` in `settings.gradle.kts`, adding
`forge_version=26.1.2-64.0.14` to `versions/dependencies/26.1.2.properties`, and re-adding `forge` to
both `mc(...)` calls unblocks it. The Forge entrypoint was then centralized into shared `src` behind a
`//? if forge` guard alongside the other loaders (ADR-013), not kept per-version. The historical
analysis below is preserved as written.

**Context.** ADR-002 scoped the MVP to NeoForge only, with the loader boundary (`net.minegasm.neoforge`
holds all Minecraft/loader-specific code; everything else is loader-independent) deliberately left open
for a second loader later. Forge was requested next. Both target Minecraft lines (`26.2`, `26.1.2`) have
active Forge builds (`26.2-65.0.9`, `26.1.2-64.0.14` on `files.minecraftforge.net`), so Forge itself is a
live target — the blocker turned out to be the build toolchain, not Forge availability.

**What's in place.** A Forge mod entrypoint at
`versions/26.2-forge/src/main/java/net/minegasm/forge/MinegasmMod.java`, matching
`net.minegasm.neoforge.MinegasmMod` feature-for-feature but wired to Forge's real API — sourced from the
`MinecraftForge/MinecraftForge` `26.2`/`26.1.2` branches directly (the `com.example.examplemod.ExampleMod`
test fixture and the FML/event-bus sources), not guessed from NeoForge's API, since the two have diverged
materially post-fork (Forge's event-bus-6 `BusGroup`/static `EventBus<T> BUS` pattern vs. NeoForge's
`IEventBus`; Forge has no `ClientStoppingEvent` equivalent, so shutdown uses a JVM shutdown hook instead).
It reuses the loader-agnostic classes already in `net.minegasm.neoforge` (sampler, config/settings
screens, widgets, `ProviderFactory`) rather than duplicating them — none of those reference NeoForge
types. A matching `META-INF/mods.toml` (real Forge's file name and schema — `mandatory=true`, not
NeoForge's `type="required"`) and `pack.mcmeta` exist under `versions/26.2-forge/src/main/resources`, and
`forge_version=26.2-65.0.9` is recorded in `versions/dependencies/26.2.properties`. The entrypoint lives
under the per-version `versions/26.2-forge/src` tree rather than the shared root `src`, specifically so
that Forge-only imports never reach a NeoForge compile.

**Why it's not registered in `settings.gradle.kts`.** The `gg.meza.stonecraft` plugin (pinned `1.12.2`,
confirmed still broken on the latest `1.12.4`) does support Forge as a first-class loader — `ModData`
recognizes `forge`/`neoforge`/`fabric`/`quilt` from the project name suffix, and its `configureLoom`
calls `loom.getForge().getConvertAccessWideners().set(...)` only on the Forge path. That call currently
throws:

```
java.lang.IllegalStateException: The value for property 'convertAccessWideners' is final and cannot be
changed any further.
    at gg.meza.stonecraft.configurations.LoomKt.configureLoom(Loom.kt:32)
```

Reproduced by adding `"forge"` to either `mc(...)` line in `settings.gradle.kts` and running literally
any Gradle invocation, including `./gradlew projects`. Because Gradle configures every subproject eagerly
(and stonecutter's `chiseledBuild` needs all of them regardless), this is not a Forge-only failure: the
moment any Forge variant is registered, **every** variant — NeoForge included — fails at plugin-apply
time, before any compilation starts. This matches the plugin's own logged warning (present since before
this change): *"Forge 1.21 and above is not really supported by Architectury anymore and issues may arise
when using it with Architectury Loom."* The resolved `dev.architectury:architectury-loom:1.17.487` is
the newest available build; there is no newer release to try.

**Decision.** Keep the Forge source/resource scaffolding in the repository (it costs nothing at rest and
is ready to go), but leave `forge` out of the active `mc(...)` variant list until the Loom incompatibility
is resolved — either an upstream fix in `architectury-loom`/`gg.meza.stonecraft`, or a different approach
to Forge's access-widener conversion that avoids the finalized-property path. Re-adding `"forge"` to the
`26.2` and `26.1.2` `mc(...)` calls in `settings.gradle.kts` is the entire re-enable step once that lands.

**Consequences.** No regression to the working NeoForge build (verified: both `26.2-neoforge` and
`26.1.2-neoforge` still compile cleanly with Forge left unregistered). Forge support is real but blocked
on a dependency outside this project's control; `docs/STATUS.md` should not claim Forge is buildable
until this is cleared and a real `chiseledBuild` run (or targeted `:26.2-forge:build`) succeeds.
