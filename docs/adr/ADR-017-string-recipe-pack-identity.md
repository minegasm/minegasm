# ADR-017: String recipe-pack identity for built-in and file packs

**Status:** accepted. Extends ADR-009 (it does not supersede it: the built-in Classic/Balanced packs
and their config compatibility are unchanged). Stems from brief 0003 §2.5.

**Context.** Brief 0003 lets users load shareable scene packs as recipes. A pack has to be selectable
the same way the built-ins are. The config already stores the selection as a **string**,
`profile.recipePack` (default `"balanced"`), and only converts it to the `RecipePackId` enum through
`RecipePackId.fromString(name, BALANCED)`. That converter has a trap: any name it does not recognize,
including every file-pack id, falls through to **BALANCED**. So reading the selection through the enum
is blind to file packs and would silently route a file-pack user to the built-in Balanced pack.

**Decision.**

- Selection identity is the **raw string**, not the enum. `RuntimeConfig.recipePackName()` exposes it
  unchanged; `recipePack()` (the enum) stays for the built-in compatibility ADR-009 relies on, but is
  never used to choose between a file pack and a built-in.
- `RecipePack.id()` returns a **String**: `"classic"`, `"balanced"`, or a file pack's own id. Built-in
  and file packs share one identity space, matching the config selector. (The method had no callers, so
  the type change is contained.)
- One `RecipeEngine.selectPack(config)` is the single place selection happens: a loaded file pack whose
  id equals `recipePackName()` wins; otherwise the built-in via the enum. Both the resolve path and the
  Balanced-only rhythmic-stroke gate route through it, so they cannot drift. The stroke gate checks the
  selected pack is the Balanced instance, not that the enum is "not CLASSIC", so a file pack does not
  drive the built-in stroke over its own motion layers.
- A file pack is materialized and scaled by the user's volume only; the mode preset's per-event base
  does not gate it (see `FileRecipePack`). It is its own recipe, not a variant of a built-in.

**Consequences.** A file-pack name selects the file pack; a name with no matching loaded pack falls
back to a built-in (Balanced), which is the existing `fromString` behavior and is now explicit rather
than accidental. Built-in-only users are unaffected: with no packs loaded, `selectPack` reduces to the
old enum choice, and the recipe test suite stays green. Momentum (accumulation) mode still short-circuits
before pack selection, so a file-pack user in that mode feels accumulation rather than their pack; that
is a known Tier 1 limitation, not a bug.

**References.** Brief 0003 (§2.4 file packs, §2.5 identity), ADR-009 (Classic pack and migration),
`RecipePackId.fromString`, `RecipeEngine.selectPack`, `FileRecipePack`.
