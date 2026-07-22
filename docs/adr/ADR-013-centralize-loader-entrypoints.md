# ADR-013: Centralize loader entrypoints in shared source via Stonecutter loader guards

**Status:** accepted. Supersedes the per-version entrypoint layout adopted in ADR-011 and ADR-012.

**Context.** ADR-012 moved the loader entrypoints (`net.minegasm.<loader>.MinegasmMod`) out of the
shared root `src` and into per-variant `versions/<mc>-<loader>/src` trees, because Stonecutter feeds
the entire shared tree into *every* variant's compile — so a NeoForge-only file in shared source broke
Fabric and Forge compiles with `package net.neoforged does not exist`. The fix worked but paid for
loader isolation with **version-axis duplication**: each loader entrypoint was copied once per
Minecraft line (`26.1.2-fabric` + `26.2-fabric`, `26.1.2-neoforge` + `26.2-neoforge`), and the copies
differed only in one or two vanilla-API lines that changed between 26.1.2 and 26.2 (`Minecraft`'s
screen setter and toast-manager accessor both moved onto `Minecraft.gui` in 26.2). The Fabric/NeoForge
resource manifests and `package-info.java` were likewise copied byte-for-byte per line. Roughly 640
lines of Java were duplicated purely along the version axis.

**Key realization.** The `gg.meza.stonecraft` plugin already registers Stonecutter *loader constants*:
`StonecutterKt.configureStonecutterConstants` calls
`constants.match(ModData.loader, "fabric", "forge", "neoforge", "quilt")` and adds the booleans
`fabricLike` / `forgeLike`. This means a Stonecutter `//? if <loader>` guard can discriminate by loader
inside shared source — the exact mechanism ADR-012 did not use. A whole-file loader guard
(`//? if neoforge { … //?}`) comments the entire file, `package` and `import`s included, out of every
non-matching variant's compile; a fully-commented `.java` is a valid empty compilation unit, so the
loader-specific imports never reach the other loaders.

**Decision.**

- Each loader entrypoint lives once in shared root `src`, wrapped in a whole-file
  `//? if <loader> { … //?}` guard: `net.minegasm.fabric.MinegasmMod` (`//? if fabric`) and
  `net.minegasm.neoforge.MinegasmMod` (`//? if neoforge`). `package-info.java` (pure Javadoc, no loader
  types) moves to shared source unguarded.
- The two vanilla APIs that differ between the 26.1.2 and 26.2 lines are funnelled through a new
  version-only compatibility shim, `net.minegasm.neoforge.McCompat`: `setScreen(mc, screen)` and
  `showToast(mc, id, title, detail)`, each carrying a `//? if >=26.2 { … //?} else { … //?}` guard.
  Because it touches only vanilla `Minecraft` / `SystemToast` / `Screen` / `Component` types, it needs a
  version guard but **no** loader guard, and the loader entrypoints become pure whole-file loader
  blocks with no inner version logic — so the whole-file loader guard never has to nest a version guard
  inside it.
- Committed shared source is always in the **active variant's** resolved form (`stonecutter active`
  is `26.2-neoforge`): the `//? if neoforge` block is committed uncommented, the `//? if fabric` block
  committed commented-out. Do not hand-edit the commented form — run the Stonecutter
  **`Refresh active project`** task (or **`Reset active project`**, which also restores the active
  variant) to let the comment processor produce it, including the `/^*`…`^/` escaping of nested block
  comments. Run `Reset active project` before every commit.

**Scope.** Forge stays scaffolded at `versions/26.2-forge/src` and is *not* moved into shared source:
it is not registered as a variant (ADR-011, blocked on Architectury Loom), so it has only a single copy
— no duplication to remove — and moving it in would add the nested-block-comment escaping hazard for no
benefit. Re-evaluate when Forge is unblocked and would otherwise gain a second per-line copy.

**Verification.** `./gradlew chiseledBuild` succeeds for all four registered variants
(`26.2-neoforge`, `26.1.2-neoforge`, `26.2-fabric`, `26.1.2-fabric`) — compile, test, and jar — with
one jar produced per variant. The load-bearing case was proven on the NeoForge entrypoint alone first
(`:26.2-neoforge:compileJava` includes it with the 26.2 toast API active; `:26.2-fabric:compileJava`
compiles clean, i.e. the guard kept `net.neoforged.*` off the Fabric classpath) before the Fabric
entrypoint was migrated.

**Resources.** The same version-axis duplication existed in the manifests. A loader's manifest is
byte-identical across its Minecraft lines (`fabric.mod.json`, `neoforge.mods.toml` — version-specific
values are `${...}` tokens resolved per variant), so each now lives once in a shared per-loader tree,
`loader-resources/<loader>`, wired into only that loader's variants via a `resources.srcDir(...)` in
the central `build.gradle.kts` (keyed off `project.name.substringAfterLast('-')`). Because the dir is
added only to that loader's variants, no jar carries another loader's manifest — verified by unzipping
all four jars: each contains exactly its own manifest with every token resolved to the correct per-line
value, and Loom's jar-in-jar `jars` array is still injected into `fabric.mod.json`. The per-version
`pack.mcmeta` copies were **deleted, not moved**: the `gg.meza.stonecraft` plugin excludes committed
`pack.mcmeta` from `processResources` and instead generates one from the Minecraft version's pack
format — but these 26.x lines are not in the plugin's `PackFormats` table, so nothing was generated and
the committed files never reached any jar (confirmed: the pre-change jars had no `pack.mcmeta` either).
They were dead weight. If a future plugin release maps 26.x, it will generate `pack.mcmeta`
automatically. Forge keeps its own manifest + `pack.mcmeta` under `versions/26.2-forge` while
unregistered.

**Consequences.** ~640 duplicated lines of Java collapse to one copy per loader, and the manifests to
one copy per loader (net −615 Java lines plus the removed resource copies). Adding a future Minecraft
line no longer duplicates the entrypoints or the manifests; only genuinely version-divergent vanilla
APIs need a new `McCompat` branch. The cost is that editing an entrypoint now requires awareness of
Stonecutter comment state — hence the `Reset active project` pre-commit step.
