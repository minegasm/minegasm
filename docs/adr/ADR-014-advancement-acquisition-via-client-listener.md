# ADR-014: Acquire advancement events via the vanilla client advancement listener

**Status:** accepted.

**Context.** `GameEventKind.ADVANCEMENT` is part of the legacy Minegasm parity surface (brief §3.2)
and is wired end-to-end — intent mapping (`HapticAggregator`), recipes (Classic and Balanced,
including the `task`/`goal`/`challenge` frame distinction), presets, accumulation, priority/expiry
timing, and the manual `/minegasm trigger advancement` path all exist. What was missing was
*acquisition*: nothing raised the event automatically from gameplay. `GameEventKind.EXPLOSION` is in
the same position, but the brief marks it explicitly optional ("Optional for MVP if event acquisition
is reliable; otherwise phase immediately after parity", appendix B), so this ADR lands advancement and
defers explosion.

Every other automatic event is observed by **per-tick polling of client state** in
`MinecraftSampler` (attack-key edges, targeted-block-to-air transitions, health/XP deltas, a
fishing-bite heuristic). That design is deliberate (ADR-002/003, brief §2.4): it uses only client-side
state so behaviour is identical in singleplayer and on an unmodified multiplayer server, and it keeps
the single version-specific class small.

Advancements do not fit that model. Verified against the pinned 26.1.2 and 26.2 client sources:

- `ClientAdvancements` holds progress in a **private `Map<AdvancementHolder, AdvancementProgress>`
  with no getter**, so there is nothing to poll and diff each tick.
- The only public hook is the single `ClientAdvancements.Listener` slot (`setListener`), which on
  attach synchronously replays all current progress via `progress.forEach(...)`.
- Reflecting the private map by field name is **not portable**: NeoForge/Forge run with official
  (Mojang) names at runtime but Fabric runs with intermediary names, so a `getDeclaredField("progress")`
  would fail on one of the three loaders.
- No loader event fits either: a NeoForge/Forge `AdvancementEvent` fires on the logical *server*, so
  against a remote vanilla server (no logical server on the client) it never fires. Fabric API exposes
  no client advancement event at all.
- A mixin into `ClientAdvancements`/`ClientPacketListener` would be robust and mapping-portable, but
  this project uses **no mixins anywhere**; introducing them means new mixin config across three
  loaders × two Minecraft lines, re-entering the exact build matrix (Loom pin, Stonecutter, CI) that
  ADR-011/012/013 just stabilized. That is too heavy for one parity event.

**Decision.** Acquire advancement events through the public `ClientAdvancements.Listener`, in a new
`AdvancementWatcher` that lives in shared `src` with no loader guard (it touches only vanilla
`net.minecraft.*` types, exactly like `MinecraftSampler`). The `Listener` interface and its
`AdvancementTree.Listener` supertype are byte-for-byte identical across 26.1.2 and 26.2, so no version
guard is needed on the watcher itself; the one line that differs between the lines — reading the
current screen (`mc.screen` in 26.1.2 vs `mc.gui.screen()` in 26.2) — goes through `McCompat`
alongside the screen-setter and toast-manager shims already there (ADR-013).

Mechanics that make the shared listener behave:

- **Shared listener slot.** The vanilla `AdvancementsScreen` also installs itself as the listener
  while open. `AdvancementWatcher` yields the slot whenever an `AdvancementsScreen` is open and
  re-asserts it (idempotently, tracked by an `installed` flag) on any other tick, so live progress
  still renders in that screen. The only lost window is an advancement earned while the advancements
  screen is literally open; re-attaching on close replays it and recovers it (one tick late).
- **Join/reload replay suppression.** Joining a world (and a datapack `/reload`, surfaced via
  `onAdvancementsCleared`) replays every already-earned advancement through the listener. The watcher
  records those into a seen-set during a short settling window (`SETTLE_TICKS`, ~3 s) without
  emitting, so only advancements earned live produce a pulse. This is biased to **under-fire**: an
  advancement earned within the first few seconds of joining is treated as replay and skipped, which
  is the correct trade-off for hardware output.
- **Filtering.** Only advancements with a `DisplayInfo` fire, which excludes recipe-unlock and other
  display-less internal advancements. The `frame` payload is `DisplayInfo.getType().getSerializedName()`
  (`task`/`goal`/`challenge`) — the exact tag the recipe packs key on — and `dedupe` is the
  advancement id hash so distinct advancements in one tick are not coalesced.
- **Cadence.** Callbacks queue onto a `ConcurrentLinkedQueue`; `MinecraftSampler.sample` drains it
  each client tick and emits `RawGameEvent`s, so advancement events enter the pipeline on the same
  tick cadence and through the same sink as every other observation.

**Consequences.** Advancement is now raised automatically on singleplayer and unmodified multiplayer
servers, with no new build infrastructure, no reflection, no mixins, and no per-loader source. It is
the first automatic event not sourced from `MinecraftSampler`'s polling, so the "only one
version-specific class" description from ADR-013 is now "the sampler and the advancement watcher, both
shared, version-differences via `McCompat`." Explosion acquisition remains deferred (its manual
trigger still works); if it is added later, a client-side signal that carries position and power
without a mixin should be found first, or the mixin-infrastructure cost accepted deliberately. The
watcher has no automated test because, like `MinecraftSampler`, it needs a live Minecraft client; it
is covered by in-game verification.
