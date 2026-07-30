# ADR-016: Electrostim as a gated opt-in output modality

**Status:** accepted. Grew out of brief 0003's device-neutral seam. Supersedes the electrostim
exclusion in brief 0002 (§2.2, §4.7, §11).

**Context.** Brief 0002 excluded electrical stimulation from the output model entirely: not a generic
output kind, not a hidden toggle, not a config string, with any OpenShock integration limited to
vibration, sound, and stop. Its premise was correct: shock is a distinct risk class that a generic
scalar model must not be able to reach by accident.

Three things have changed the calculus since:

1. Users already run OpenShock and XToys estim with Minecraft through ad-hoc bridges that have no
   fail-stop, no panic key, no watchdog, no bounded queues, and no independent caps. Refusing to
   support shock does not prevent it; it pushes users to setups strictly more dangerous than one we
   would build.
2. The engine already owns the exact safety machinery a responsible shock path needs: panic, watchdog,
   pause/world-unload/disconnect stops, monotonic command expiry, bounded queues, and hard caps after
   user scaling. Withholding shock wastes that safety investment rather than applying it.
3. The backend-neutral seam (brief 0003 §3, and its central-governance decision in §3.3) lets shock be
   a distinct, contained modality with its own caps and policy, reachable only through a shock-capable
   backend the user explicitly configured. The thing 0002 feared, shock reachable through a generic
   scalar, becomes architecturally impossible rather than merely forbidden.

So we keep 0002's premise and reject only its conclusion.

**Decision.** Support electrostim hardware. Vibration and sound are available by default on a
connected device; actual shock is available only behind an explicit, revocable, per-session opt-in
with independent hard limits.

Shock is modeled as a **distinct output modality, never an `OutputKind`**. `OutputKind` is Buttplug's
verb set and is governed by ADR-008; shock does not belong there. Electrostim gets its own capability
type, its own caps table, and its own policy object. Only a dedicated shock-capable backend (OpenShock
directly, or through the local bridge) can advertise or accept it. No Buttplug feature, recipe pack, or
preset can produce it. Intensity is expressed in the device's native units, not the engine's 0..1
scale, so there is no silent scalar bridge from an ordinary effect into a shock.

**Safety model (binding constraints).**

- **Off by default, twice.** Master output is off by default, and shock is separately off even when a
  shock-capable backend is connected. Such a device does vibration and sound only until the user arms
  shock.
- **Explicit multi-step opt-in.** Arming requires an acknowledged risk notice, a per-shocker enable,
  and a per-session arm with a timeout that auto-disarms. It is never a single toggle.
- **Independent hard limits, outside user scaling and outside packs.** Maximum intensity ceiling,
  maximum single-pulse duration, minimum inter-shock interval, cooldown, rate limiting, and a mandatory
  ramp. These live in code and local config only; no shared pack, preset, or scaling can raise them.
- **Governed against the whole body.** Shock is subject to the central aggregate body budget (brief
  0003 §3.3), so its intensity is limited against concurrent output on the same body, not only against
  its own per-modality caps. Shock and a strong vibration cannot stack on one body unnoticed.
- **Fail-closed everywhere.** Disconnect, watchdog, panic, pause, world unload, config reset, transport
  error, or any unknown/new action type results in no shock and an immediate stop. Every shock command
  has a finite duration; the system never depends on a later stop message to end one.
- **Never shareable.** Shock enablement and shock routing are local-only, explicit user mappings to
  specific events. Importing a scene pack can neither enable shock nor set any shock parameter. Hard
  rule, tested.
- **Honest UX.** The backend row shows local/LAN/cloud classification, credentials are redacted from
  logs and exports, and a physical-device confirmation gates first arming.
- **Ship gate.** Shock ships only after a separate threat-model document and a safety review, on the
  proven fail-stopped foundation. It is the last thing built, not the first.

**Scope limits.** No EMS, medical, or therapeutic framing. The vibration-and-sound-only path remains
the default and stays fully supported for users who never arm shock.

**Implementation.** A new electrostim capability and policy type outside `core/OutputKind`; an
electrostim caps table separate from `render/SafetyCaps`; enforcement of the aggregate body budget in
the central governor (brief 0003 §3.3); a shock-capable backend (OpenShock direct or via the local
bridge) behind the `HapticBackend` seam; arming state and redaction in config and UI. None of this
exists yet; it is gated behind the safety review above.

**Consequences.** Users get a supported path that is safer than the ad-hoc bridges they use today, and
the generic scalar model still cannot reach shock. 0002's premise is preserved; only its blanket
prohibition is lifted. The cost is a distinct modality type, its own caps and policy, and a mandatory
safety review before release. A Buttplug-only or shock-disabled user is unaffected.

**References.** Brief 0003 (§3.3 central governance and the backend seam it realizes), brief 0002
(§2.2, §4.7, §11, the superseded exclusion), ADR-008 (output type policy), OpenShock safety rules
(<https://wiki.openshock.org/home/safety-rules>).
