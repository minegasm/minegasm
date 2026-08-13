<!--
SPDX-AI-Disclosure: ai-generated
SPDX-AI-Model: gpt-5.6-sol
SPDX-AI-Provider: OpenAI
SPDX-AI-Scope: Full repository follow-up review performed by GPT-5.6 Sol with extra-high reasoning effort. The reviewer inspected the current implementation, tests, Claude's response, briefs, ADRs, safety documentation, and UX under human direction.
SPDX-AI-Date: 2026-08-13
-->

# Follow-up Comprehensive Code Review

Date: 2026-08-13

Reviewed revision: `ef4b5222f758e6b6016078c8e07fba617f05c092` on `main`

Related documents:

- `2026-08-12-comprehensive-code-review.md`
- `2026-08-12-review-response.md`

## Bottom line

Claude fixed a meaningful amount of the first review. Bridge identity is much healthier, a bridge added during panic inherits the output latch, isolated tests no longer survive stop transitions, config updates fail in the safer direction, connection attempts are better scoped, pack and transport inputs have useful first-line bounds, and the pack screens are now usable with long lists. Both Java build matrices pass, including fresh representative test runs.

I still would not sign off the current multi-backend beta as fail-safe. The watchdog fix is not reachable through the real stalled-worker path because the client tick takes the worker monitor before it checks the watchdog. Even when `emergencyStop` is called directly, it does not latch output off and an in-flight cycle can enqueue output after the stop. The dedicated buttplug4j stop executor improves responsiveness but does not order the stop after a device write already in progress. Bridge removal also mutates worker-confined forwarding state from the client thread. Finally, the XToys adapter neither reconnects nor resynchronizes safely after its downstream WebSocket drops.

For this beta, e-stim and config schema migration are policy-deferred items, not blockers. I have kept useful notes about them near the end without including them in the open finding counts.

Open findings in this pass:

- **P1:** 6 high-priority safety, stop-ordering, or cross-backend behavior defects.
- **P2:** 5 correctness, robustness, or UX defects.
- **Policy-deferred:** 2 non-blocking beta follow-ups covering e-stim and schema evolution.

Five of the P1 findings should be fixed before a public multi-backend beta. P1-6, central priority resolution, can be accepted temporarily only if the beta explicitly says that bridge output does not yet honor the same priority and exclusivity behavior as native Buttplug output.

## Scope and approach

This was a fresh review of the entire maintained implementation, not a diff-only review. I re-read the engine, both loader families, the bridge protocol and transports, the XToys adapter and script, current tests, the original review, Claude's response, relevant briefs, ADRs, and safety documentation. Historical prototypes were not treated as shipping code.

I paid extra attention to the claimed fixes and then followed their failure and recovery paths. That uncovered several cases where the happy-path fix is real but the safety claim is broader than the implementation.

No hardware was attached, no real Intiface or XToys session was used, and the Minecraft screens were not visually exercised in game. Hardware-library timing, physical feel, and final rendered layout still need manual validation.

## P1 findings

### P1-1: The real watchdog path still blocks before it can issue the out-of-band stop

Locations:

- `engine/src/main/java/net/minegasm/runtime/HapticRuntime.java:150-154`
- `engine/src/main/java/net/minegasm/runtime/LifecycleController.java:37-39`
- `engine/src/main/java/net/minegasm/runtime/HapticWorker.java:95-107`
- `engine/src/main/java/net/minegasm/runtime/HapticWorker.java:133-135`
- `engine/src/main/java/net/minegasm/backend/HapticBackend.java:80-87`
- `engine/src/test/java/net/minegasm/runtime/HapticWorkerEmergencyStopTest.java:23-55`

`HapticRuntime.onClientTickEnd` is meant to be the observer independent of the worker. In the unpaused path it first calls `lifecycle.onResume()`, which calls synchronized `HapticWorker.resumeAll()`. Only after that returns does it call `watchdog.check()`.

If a backend is hung inside synchronized `HapticWorker.cycle`, the client tick blocks at `resumeAll` on the same monitor. It never reaches the watchdog. The new unit test calls `driver.emergencyStop` directly, so it proves that the method itself does not take the monitor, but it does not test the path that is supposed to call it.

There is a second problem after direct invocation. `HapticWorker.emergencyStop` records a reason and fans out backend stops, but leaves `outputEnabled` true and leaves the governed scenes intact. That contradicts the `HapticBackend` contract, whose comment says callers keep output latched off until explicit re-enable. It also makes post-stop ordering unsafe:

- `BridgeBackend.emergencyStop` replaces pending frames with a stop, but a concurrently running forwarder can append a new effect behind that stop.
- A subsequent client tick immediately submits more scenes because the master latch is still on.
- The hub banner checks only `isOutputEnabled`, so it never shows a watchdog stop even though the response says it does.
- The emergency-stop test says the method latches output off, but never asserts the latch or verifies that no later effect is delivered.

Recommendation:

1. Check the watchdog before any call that can take the worker monitor. Also call `onResume` only on an actual inactive-to-active transition, not on every normal client tick.
2. Give emergency stop its own atomic safety generation or latch. A cycle must capture that generation before rendering and prove it is still current before any backend accepts output.
3. Keep output stopped until an explicit resume, or implement a clearly specified recovery handshake after several healthy cycles. Do not silently resume in the same cycle that triggered the stop.
4. Add an integration-level test that hangs a backend, drives `HapticRuntime.onClientTickEnd`, observes a stop without blocking, releases the backend, and proves that no effect is emitted until resume.

### P1-2: The buttplug4j stop can still be overtaken by an in-progress device write

Locations:

- `engine/src/main/java/net/minegasm/buttplug/b4j/Buttplug4jProvider.java:63-82`
- `engine/src/main/java/net/minegasm/buttplug/b4j/Buttplug4jProvider.java:230-248`
- `engine/src/main/java/net/minegasm/buttplug/b4j/Buttplug4jProvider.java:251-288`
- `engine/src/main/java/net/minegasm/buttplug/b4j/Buttplug4jProvider.java:292-323`

The dedicated stop executor is a real improvement. A stop no longer waits behind connect, scan, or refresh work on the lifecycle executor. The `sendEpoch` also cancels writes that are still waiting in the send queue.

It does not guarantee the stronger claim in the response that no write reaches a device after stop-all. A send task checks the epoch once and then enters a blocking `run*Float` call. If the epoch changes after that check, the independent stop executor can send stop-all first and the older device call can finish afterward, reasserting a non-zero value. There is no ordering or compensating stop between the two executors. A stop call that itself hangs also holds up every later stop on the single stop executor.

Recommendation: after every blocking dispatch returns, compare its captured epoch again. If it became stale, schedule another stop-all and do not allow new output until that compensating stop has been dispatched. Longer term, put output and stop ordering behind one backend-owned actor with an explicit emergency lane, or close the affected device session when an in-flight write cannot be safely ordered. Add an injectable buttplug4j seam so a test can pause a device write, issue stop, release the write, and verify that zero is the last command.

### P1-3: Removing or repointing a bridge races the worker and can discard its stop

Locations:

- `engine/src/main/java/net/minegasm/runtime/HapticRuntime.java:195-232`
- `engine/src/main/java/net/minegasm/bridge/BridgeBackend.java:150-157`
- `engine/src/main/java/net/minegasm/bridge/BridgeBackend.java:187-203`
- `engine/src/main/java/net/minegasm/runtime/GovernedSceneForwarder.java:28-35`
- `engine/src/main/java/net/minegasm/runtime/GovernedSceneForwarder.java:51-88`

`reconcileBridges` runs on the client thread while the worker can be iterating the coordinator's copy-on-write snapshot. It removes the backend from the coordinator, then calls `gone.stop()`. That stop resets the `GovernedSceneForwarder`, whose own documentation says it is confined to the worker thread and reset under the worker monitor.

The old worker snapshot can still be forwarding through the removed backend at the same time. This creates an ordinary data race in the forwarder's `HashMap` and `HashSet`. The method then calls `gone.close()` immediately after `gone.stop()`. `stop` only queues the frame, while `OutboundQueue.close` clears pending frames and the transport close interrupts its writer. If an effect is already in flight, the queued stop is cleared. Even without an in-flight effect, the asynchronous write can be interrupted before it reaches the adapter. The configuration says the bridge is gone while its last physical output may continue until TTL.

Recommendation: quiesce a bridge before removing it from the coordinator. The quiesce operation should atomically reject new submissions and enqueue an emergency stop without touching worker-confined maps. After every existing coordinator snapshot has drained, close it and clear forwarding state. A small backend generation checked by `submit` is enough to reject a stale cycle. Add a deterministic test that blocks a bridge submit while another thread disables or repoints it.

### P1-4: A rendering fault is recorded, but the backend is not quarantined and the worker is still marked healthy

Locations:

- `engine/src/main/java/net/minegasm/backend/BackendCoordinator.java:68-88`
- `engine/src/main/java/net/minegasm/runtime/HapticWorker.java:100-107`
- `engine/src/main/java/net/minegasm/client/MinegasmClient.java:651-668`
- `engine/src/test/java/net/minegasm/backend/BackendCoordinatorTest.java:74-81`

The new code does more than the old silent catch: it attempts a backend stop and records a bounded fault entry. That is useful.

It still treats the overall cycle as healthy, because `HapticWorker` ignores the fault count returned by `onGovernedScenes` and advances `lastHealthyCycleNs` unconditionally. The backend also remains in the fan-out. On the next cycle it may send output again, fail again, and be stopped again. If its stop throws, that exception is swallowed without its own fault record. A backend that alternates between partial output and exceptions can therefore keep re-entering service automatically while the watchdog reports a healthy worker.

The only visible reporting is appended to `/mg status`; the hub does not show a fault badge or require recovery.

Recommendation: quarantine a backend after a render fault. Reject its output until the user reconnects it or an explicit, tested recovery policy succeeds. Do not advance the worker's healthy heartbeat for a cycle with a backend fault. Record the exception type/message safely, separately record a failed stop, and surface a persistent integration-level fault state in the hub.

### P1-5: The XToys downstream reconnect path can miss both active output and required zeroes

Locations:

- `docs/bridge/xtoys/main.go:318-385`
- `docs/bridge/xtoys/main.go:394-430`
- `docs/bridge/xtoys/xtoys-minegasm.json:1-99`
- `docs/bridge/xtoys/README.md:53-55`
- `docs/bridge/PROTOCOL.md:56-67`

The adapter keeps a `last` intensity per role and only calls `send` when the newly computed value differs. When the WebSocket reader notices a downstream drop, it clears `conn` but deliberately keeps `last` unchanged. There is no reconnect loop. Redial happens only inside `send`.

That creates several bad recovery cases:

- A steady scene at the same level as `last` never calls `send`, so it never triggers a redial. A continuously refreshed scene can leave the downstream link offline indefinitely.
- If a different role or level eventually triggers a successful dial, only that changed role is sent. Other active roles are not replayed.
- If a scene expires or a panic occurs while XToys is offline, the adapter cannot deliver zero. Once the local scene is gone there may be no later state change to trigger a retry.
- The shipped XToys script sets role outputs directly and contains no independent timeout. The README and protocol promise that TTL or stop releases output, but that promise does not hold across an adapter-to-XToys outage.

This is safe only if XToys itself guarantees that every webhook disconnect immediately releases every output. Neither the code nor the shipped script establishes that guarantee.

Recommendation: reconnect independently of output changes whenever active scenes or an undelivered zero exist. On every new WebSocket, send a complete zero baseline first, then recompute and send the full current state for every role. Mark all cached role values unknown on disconnect. Keep retrying required zeroes until acknowledged by a successful write. Add Go tests with a fake WebSocket for drop during steady output, drop before expiry, panic while offline, reconnect with several roles, and repeated reconnect failure.

### P1-6: Central priority and exclusivity remain backend-dependent

Locations:

- `engine/src/main/java/net/minegasm/runtime/SceneGovernor.java:92-130`
- `engine/src/main/java/net/minegasm/runtime/SceneMixer.java:192-213`
- `engine/src/main/java/net/minegasm/bridge/BridgeCodec.java:33-50`
- `docs/bridge/xtoys/main.go:217-223`
- `docs/bridge/xtoys/main.go:320-337`
- `docs/adr/ADR-018-scene-level-central-governance.md:18-31`

Claude's contained mixer fix is correct: two exclusive candidates on one Buttplug endpoint now choose priority before amplitude. Connected bridges also count toward fatigue. Those changes close two real holes.

The larger contract in ADR-018 is still not implemented. `SceneGovernor.govern` expires, attenuates, and accounts scenes, but does not resolve priority or exclusivity before fan-out. Buttplug resolves conflicts by physical endpoint in `SceneMixer`. The XToys adapter ignores the transmitted priorities and coupling modes, then takes the maximum level per role. Identical governed scenes therefore produce different conflict results depending on backend.

This matters whenever native and bridge output run together, or when an XToys user routes several roles to one device. A low-priority scene can remain active on the bridge while a higher-priority exclusive scene ducks it on Buttplug.

Recommendation: introduce a small logical destination model at scene level, such as body region plus output class. Resolve exclusivity and priority there, then let each backend do only capability mapping, physical caps, and signal rendering. Until that is built and hardware-tested, label bridge priority behavior as a beta limitation and do not claim cross-backend semantic parity.

## P2 findings

### P2-1: Bridge change detection still omits wire-relevant primitive parameters

Locations:

- `engine/src/main/java/net/minegasm/runtime/GovernedSceneForwarder.java:20-31`
- `engine/src/main/java/net/minegasm/runtime/GovernedSceneForwarder.java:90-116`

The forwarder now distinguishes role, coupling, layer priority, primitive class, and duration. It also records a scene only after the sink accepts it. That resolves the dropped-frame bug and several stale-shape cases.

The signature still omits the actual non-level shape parameters. Two beat patterns with the same peak and total duration but different beat times compare equal. So do oscillations with different periods and sweeps whose endpoints change while the same maximum amplitude is retained. The code comment acknowledges this, while the response describes the fingerprint more broadly.

Recommendation: fingerprint the exact normalized JSON fields emitted by `PrimitiveJson`, excluding only the attenuation fields intentionally compared with epsilon. A canonical wire-content hash would avoid maintaining a second incomplete serializer.

### P2-2: `SUPPLEMENTAL` delivery is still accepted even though no runtime code consumes it

Locations:

- `engine/src/main/java/net/minegasm/core/DeliveryMode.java:6-16`
- `engine/src/main/java/net/minegasm/core/HapticRoute.java:18-62`
- `engine/src/main/java/net/minegasm/pack/ScenePackCodec.java:357-368`
- `docs/briefs/0001-initial-implementation-brief/MINEGASM_NEXT_IMPLEMENTATION_BRIEF.md:451-463`

Rejecting `BEST_PER_DEVICE`, `BEST_GLOBAL`, and `EXCLUSIVE` is a good fail-closed change. `SUPPLEMENTAL` is still accepted and described as wired end to end, but production code still has no branch on `HapticRoute.deliveryMode()`. It behaves like `ALL_COMPATIBLE`; only the ordinary per-output enable policy can happen to suppress its route.

Recommendation: either reject `SUPPLEMENTAL` too, or define and implement the user-controlled supplemental class it is supposed to consult. Add one behavior test per accepted enum, not only a parser rejection test.

### P2-3: Every used TCP bridge reconnect can leak a writer executor

Locations:

- `engine/src/main/java/net/minegasm/bridge/TcpLineBridgeTransport.java:65-79`
- `engine/src/main/java/net/minegasm/bridge/TcpLineBridgeTransport.java:133-183`
- `engine/src/main/java/net/minegasm/bridge/BridgeBackend.java:111-129`

After a successful TCP connection, the transport creates a single-thread writer executor. A read or write failure closes the socket and marks the transport closed, but `fail` does not shut down that executor. `BridgeBackend.ensureConnected` then replaces the closed transport without closing the old instance. Once the writer thread has been started by any frame, each drop and reconnect leaves another daemon thread behind.

Recommendation: make the transport own one idempotent cleanup method used by both `fail` and `close`, including writer shutdown. Close a superseded transport before replacing it. Add a reconnect stress test that checks resource counts or injects a tracked executor factory.

### P2-4: XToys adapter state is shared unsafely across Minegasm clients and has no live-scene bound

Locations:

- `docs/bridge/xtoys/main.go:90-123`
- `docs/bridge/xtoys/main.go:126-138`
- `docs/bridge/xtoys/main.go:228-241`
- `docs/bridge/xtoys/main.go:293-315`

The server accepts multiple Minegasm TCP connections, but every connection writes into the same `active` map using only scene ID or continuous key. Two clients with the same built-in scene keys overwrite each other. Disconnecting either client calls global `stopAll`, which clears output belonging to the other still-connected client.

The map and listener set also have no capacity or per-client quota. A local process, or a remote peer when the operator binds beyond loopback, can send unique long-lived scene IDs until memory grows without bound. The one-megabyte line limit does not bound the number of retained scenes.

Recommendation: either explicitly allow only one Minegasm client and reject later connections, or namespace scenes by connection and remove only that connection's scenes on disconnect. Put hard bounds on clients, live scenes per client, TTL, and ID length. Avoid holding the global XToys mutex while status writes can block for up to two seconds per listener.

### P2-5: Pack numeric validation still accepts coercible strings and non-finite floats

Locations:

- `engine/src/main/java/net/minegasm/pack/ScenePackCodec.java:275-340`
- `engine/src/main/java/net/minegasm/util/HapticMath.java:15-19`

The new file-size and cardinality caps are worthwhile. Numeric fields still use Gson's coercing accessors without first requiring a numeric JSON primitive. For example, a quoted numeric value is accepted despite the codec's stated wrong-type rejection. A quoted `"NaN"` reaches `getAsFloat`, and `HapticMath.clamp01` returns NaN because both range comparisons are false.

NaN is likely to collapse to zero at a later integer conversion, but it can also poison equality, epsilon, fatigue, and scheduler decisions. Invalid pack content should not be allowed to depend on incidental downstream behavior.

Recommendation: require `isJsonPrimitive && primitive.isNumber` for numeric fields, parse once, reject NaN and infinities explicitly, then apply range bounds. Add tests for quoted numbers, NaN, positive and negative infinity, exponent overflow, wrong collection element types, and file growth between the size check and read.

## Status of the original findings

| Original finding | Current status | Notes |
|---|---|---|
| P0-1 e-stim arming | Policy-deferred for beta | Not counted as a blocker in this review. The engine still has no supported e-stim modality. |
| P1-1 watchdog blocked by worker | Open | The direct method is out of band, but the real caller blocks on `resumeAll` first and the stop is not latched. |
| P1-2 buttplug4j stop ordering | Partially fixed | Dedicated executor and queued-write epoch are useful; an in-progress write can still finish after stop. |
| P1-3 new bridge ignores panic | Resolved | New and reconciled bridges inherit the worker output latch before joining fan-out. |
| P1-4 isolated test survives stop | Resolved | Test state is cleared on stop-like transitions. |
| P1-5 backend faults swallowed | Partially fixed | Stop attempt and reporting were added; health and quarantine are still wrong. |
| P1-6 central governance | Partially fixed | Exclusive conflict ordering and bridge fatigue improved; backend-neutral resolution is still absent. |
| P1-7 duplicate bridge identity | Substantially resolved | Immutable IDs fix the hidden backend problem. Hand-edited duplicate display names can still make name-based commands ambiguous. |
| P2-1 delivery modes ignored | Partially fixed | Three modes are rejected; `SUPPLEMENTAL` remains a no-op. |
| P2-2 stale connect completions | Resolved for the reviewed races | WebSocket attempts are scoped, TCP connect can be aborted, and buttplug4j has a connect generation. TCP failure cleanup still leaks its writer executor. |
| P2-3 bridge forwarding state | Partially fixed | Acceptance and reconnect resync improved; the structural signature remains incomplete. |
| P2-4 config publish ordering | Resolved | Disable fails toward stopped in-memory state; other changes persist before publication. |
| P2-5 unbounded input | Partially fixed | File, frame, and several array/string bounds exist. Numeric validation and XToys retained-state bounds remain. |
| P2-6 future schema handling | Policy-deferred for beta | The current preservation behavior is sensible, but schema maintenance is not a beta gate. |
| P2-7 pack identity/fallback | Resolved | Reserved IDs and missing-selection reporting address the original behavior. |
| P2-8 pack screen overflow | Resolved | Viewport scrolling and pinned Done controls are present across loaders. |

## UX recommendations

The recent hub and pack work is a good start. The next UX improvement should be clearer state, not more settings.

1. **Use one explicit output state everywhere.** Replace the overloaded `outputEnabled` boolean with states such as Running, User stopped, Watchdog stopped, Backend fault, and Globally disabled. Show the reason and time. This would have prevented the current watchdog banner mismatch.
2. **Keep the safety state visible on every Minegasm screen.** A compact red or amber header with a Stop or Resume action is more useful than a banner that appears only on the hub. Resume should be available only when the runtime is truly latched off.
3. **Show the bridge as a connection chain.** A row like `Minegasm -> adapter -> XToys` with a state for each hop is easier to understand than one combined label. Add last state-change time and a Retry action when the downstream hop is offline.
4. **Put backend faults in the hub.** A persistent badge should say which integration was quarantined and offer Details and Reconnect. Requiring `/mg status` hides the most important failure from ordinary users.
5. **Give test actions a result and a stop control.** Show whether the test was accepted, which integration or features it targeted, its remaining duration, and why it was skipped. A visible Stop test button is reassuring even when the global panic key exists.
6. **Turn packs into a small manager.** Scroll the selected pack into view when the screen opens, show author and description, list per-file validation errors, and offer Reload and Open folder. If the selected pack is missing, show the fallback in the pack screen instead of only adding it to error history.
7. **Improve classic navigation parity.** The new buttons make every row reachable, but wheel, keyboard, page position, and focus behavior should be consistent where the Minecraft version allows it.
8. **Label beta capability boundaries in product UI.** Say that e-stim is unavailable, and say that bridge priority parity is not implemented yet. A short honest limitation is better than making users infer it from an ADR.

A small shared view-state layer would make these states consistent across five screen implementations. It does not need to be a sweeping presenter rewrite. Start with immutable `OutputStatus`, `BridgeChainStatus`, `TestResult`, and `PackLoadResult` models, then let each loader render them in its own API.

## Non-blocking beta follow-ups

### E-stim

Claude was right not to improvise ADR-016's arming system without a threat model, hardware validation, and a dedicated safety review. For the current beta, treat e-stim as unavailable rather than partially supported. I would also change the XToys wording from “unsupported and at your own risk” to a direct “do not route this adapter to e-stim” message. The generic XToys route cannot enforce modality safety, so documentation and UI need to make the product boundary unmistakable.

When e-stim work starts, it should be a separately reviewed capability with native-unit caps, finite pulses, timed arming, cooldown, whole-body accounting, physical confirmation, and hardware-in-the-loop tests. None of that should be inferred from ordinary haptic scene intensity.

### Schema evolution and migration

Schema version discipline and migrations are not a beta blocker by project decision. The new future-schema preservation path is still a good defensive behavior and should remain. I would avoid spending more time on migration chains until the beta data model settles. Before a stable release, add explicit ownership of config and pack schema compatibility, downgrade behavior, backups, and migration tests.

## Verification performed

- Modern `chiseledBuild`: passed across the configured Fabric, Forge, and NeoForge matrix.
- Classic `build`: passed for 1.7.10 Forge, 1.8.9 Forge, 1.12.2 Forge, and both 1.16.5 loaders.
- Fresh modern `:1.21.1-neoforge:test`: passed with all tasks rerun.
- Fresh classic `:1.12.2-forge:test`: passed with all tasks rerun.
- XToys `go test ./...`: command passed, but the package has no test files.
- XToys `go vet ./...`: passed.
- `git diff --check 98ee7b4..HEAD`: found only the pre-existing Markdown hard-break whitespace in the original review.

No hardware or in-game testing was performed. The buttplug4j provider still lacks an injectable test seam, and the XToys adapter has no automated tests at all.

## What improved well

- Stable bridge IDs are the right foundation. Runtime identity no longer depends on a user-facing label.
- Applying the current output latch before a new backend becomes visible is the correct ordering.
- Clearing isolated test state on every stop-like transition closes a subtle and important resume bug.
- Config disable now fails toward stopped state if persistence fails.
- Connect-attempt scoping in the modern WebSocket transport is much easier to reason about and is covered by focused tests.
- Pack size, structure, and frame limits materially reduce accidental and hostile input risk.
- The pack and hub scrolling changes preserve access to fixed safety and Done controls.
- Adapter downstream status is useful state to expose, even though recovery still needs work.

## Recommended order of work

1. Make the watchdog reachable before any worker lock, latch the emergency generation, and test no post-stop output.
2. Close the in-progress buttplug4j write race with a compensating stop and an injectable timing test.
3. Add a thread-safe bridge quiesce path for disable, removal, and repoint.
4. Rebuild XToys downstream reconnect around zero-baseline and full-state resynchronization, then add Go tests.
5. Quarantine faulted backends and surface the state in the hub.
6. Decide whether central priority parity is required for this beta. If not, label the limitation. If it is, add logical destinations and cross-backend conformance tests.
7. Finish the P2 parser, fingerprint, transport cleanup, and adapter-bound issues.

After the first five items, I would repeat the fault-injection tests with a fake slow device call and a fake XToys server, then do a short hardware smoke pass focused on stop, disconnect, reconnect, world unload, pause, and backend switching.
