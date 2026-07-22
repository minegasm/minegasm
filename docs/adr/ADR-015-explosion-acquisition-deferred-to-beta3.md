# ADR-015: Defer automatic explosion acquisition to beta.3 (client-only mixin)

**Status:** deferred — planned for `1.0.0-beta.3`.

**Context.** `GameEventKind.EXPLOSION` is wired end-to-end — intent mapping (`HapticAggregator`
reads `power`/`distance`), recipes, settings, and the manual `/minegasm trigger explosion` path all
exist — but nothing raises it automatically from gameplay. It is the only remaining trigger without
automatic acquisition; ADR-014 landed advancement and left explosion here. The brief marks explosion
acquisition explicitly optional ("Optional for MVP if event acquisition is reliable; otherwise phase
immediately after parity", appendix B).

Unlike every other automatic event, explosion cannot be recovered by per-tick polling of client state
(`MinecraftSampler`) or by a public/stable client API (the route used for advancement, ADR-014).
There is no mixin-free client signal that carries an explosion's **position** and **power**, so the
project's mixin-free design (ADR-002/003) has kept it manual through beta.1 and beta.2.

**Decision.** Adopt a **mixin** to acquire explosions automatically, deferred to **beta.3** rather
than rushed into beta.2. The mixin must preserve the mod's **client-only** stance (brief §2.4): it may
only hook the client's explosion **receive path** (the explosion packet handler / client-side
explosion processing), never server-side explosion computation — that code does not run on the
player's machine on an unmodified multiplayer server, and hooking it would silently break client-only.
It will be declared in a client-only mixin config, verified against each supported mapping
(`26.1.2`/`26.2` × each loader).

**Known caveat.** Even a correct client-side hook only sees what the server sends the client. The raw
explosion `power` is generally **not** transmitted; the client receives position and effect/knockback
data. So automatic acquisition will derive an *approximate* power/distance from what the packet
carries, not the server's true value — acceptable for haptic feedback, but the recipe input is an
estimate. This is part of why it is worth doing carefully in its own cycle.

**Why beta.2 ships without it.** beta.2's feature scope (Fabric + Forge loaders, advancement
auto-acquisition, `/minegasm enable`/`disable`) is complete and mixin-free. Adding the project's first
mixin — a new per-loader, per-mapping bytecode surface — under beta.2 time pressure trades that safety
for one optional event. Deferring keeps beta.2 both client-only and honest: explosion remains reachable
via `/minegasm trigger explosion` and is documented as the sole manual trigger.

**Consequences.** No change to beta.2. For beta.3: add a client-only explosion mixin on the receive
path, verify it on the full variant matrix, and update `CHANGELOG.md`, `README.md`, and `docs/STATUS.md`
to move explosion from "manual only" to "automatic (approximate power)". Until then the mixin-free
guarantee (ADR-002/003) still holds for the shipped mod.
