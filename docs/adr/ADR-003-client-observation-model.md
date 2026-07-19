# ADR-003: Client-observation event model for multiplayer compatibility

**Status:** accepted.

**Context.** The mod is entirely client-side and must work on multiplayer servers that do not install
it. Server-oriented NeoForge events cannot be assumed to fire on the physical client in that case.

**Decision.** Client-observable state is the canonical event source. `MinecraftSampler` samples player
state once per client tick into an immutable `ClientStateSnapshot` and derives discrete events from
observed transitions (attack-key edge on an entity, targeted block turning to air, block appearing at
the look position, a fishing-bite heuristic). `StateTracker` computes transitions; `HapticAggregator`
turns them into intents with damage merging, XP coalescing, refractory periods, and vitality
edge/repeat. NeoForge events may be added later only as accelerators where they are reliably emitted
client-side.

**Consequences.** Behaviour is identical in singleplayer and multiplayer. Some causes known only
server-side are approximated; that is acceptable because the goal is pleasant feedback, not
authoritative analytics. The sampler is the single class that varies by Minecraft version.
