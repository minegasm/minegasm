<!--
SPDX-AI-Disclosure: ai-generated
SPDX-AI-Model: gpt-5.6-sol
SPDX-AI-Provider: OpenAI
SPDX-AI-Scope: Full repository second follow-up review performed by GPT-5.6 Sol with extra-high reasoning effort. The reviewer inspected the current implementation, tests, Claude's latest response, prior reviews, briefs, ADRs, safety documentation, and UX under human direction.
SPDX-AI-Date: 2026-08-13
-->

# Second follow-up comprehensive code review

Reviewed revision: `2620bc9b51b45aa27a59a49456a339ff4a5a6d1d`

Date: 2026-08-13

## Verdict

Claude fixed a substantial part of the previous review. The new backend quarantine, bridge stop generation, Buttplug compensating stop, TCP writer cleanup, complete bridge fingerprint, stricter pack parsing, and XToys client namespacing are all useful changes. Both Java build matrices and the new Go tests pass.

I would not close the review yet. The watchdog is still not independent of every blocking lifecycle path, and the new output state can let a user resume a watchdog stop or let a watchdog overwrite a user panic. Bridge exclusivity only filters new frames and does not retract a lower-priority scene already held by an adapter. XToys reconnect is not an atomic state transfer, so a partial resync can still abandon an owed zero or briefly replay stale output. Those are safety and correctness issues, not polish.

The central logical-destination model also remains necessary. It should be built as the final architecture, with the bridge protocol and Buttplug renderer consuming the same resolved output model. Hardware testing is needed to validate feel, but the lack of hardware should not be used as a reason to leave the architecture split.

E-stim and schema migration are not counted as beta blockers, per project direction. They are covered near the end so their release requirements stay visible.

## Scope and approach

This was another review of the full maintained implementation, not a check of only the seven commits after the prior report. I revisited the engine, runtime safety model, Buttplug providers, bridge protocol and transports, XToys adapter, pack loading, modern and classic UI paths, tests, review responses, briefs, ADRs, and safety documentation. Historical prototypes were not treated as shipping code.

The deepest pass focused on the changed safety and reconnect code and the state that consumes it. I traced stop, panic, watchdog, pause, unload, disconnect, reconnect, quarantine, and adapter resynchronization as concurrent state transitions. I also compared the claims in `2026-08-13-follow-up-review-response.md` with the behavior and tests now present.

## Findings

### P1-1: The watchdog can still be blocked before it runs

Locations:

- `engine/src/main/java/net/minegasm/runtime/HapticRuntime.java:116-154`
- `engine/src/main/java/net/minegasm/runtime/LifecycleController.java:24-55`
- `engine/src/main/java/net/minegasm/runtime/Watchdog.java:28-51`
- `engine/src/test/java/net/minegasm/runtime/HapticRuntimeWatchdogTest.java:30-69`

The active, unpaused tick path now checks the watchdog before `onResume`, which fixes the exact hang covered by the new test. The other lifecycle branches still run first. A world unload calls `onWorldUnload`, and a pause transition calls `onPause`, before `watchdog.check`. Both paths can take the worker monitor through `stopAll`, `pauseAll`, or `discardPauseAll`.

If a backend is hung inside the synchronized worker cycle when the game pauses or the world disappears, the client thread can block on that monitor and never reach the out-of-band watchdog stop. The watchdog also depends entirely on Minecraft client ticks, so it is not an independent observer when the client thread itself is delayed.

Recommendation: give the watchdog its own small scheduled executor with no dependency on the worker monitor or client tick. Let it observe an atomic heartbeat and issue only out-of-band stop events. Client lifecycle signals should feed the same safety controller, but they should not be the clock that keeps the watchdog alive. Add tests for active play, pause, world unload, a blocked client tick, worker shutdown, and repeated stop delivery.

### P1-2: The output state has no safe transition authority

Locations:

- `engine/src/main/java/net/minegasm/runtime/HapticWorker.java:142-184`
- `engine/src/main/java/net/minegasm/runtime/Watchdog.java:34-50`
- `engine/src/main/java/net/minegasm/runtime/LifecycleController.java:57-67`
- `modern/src/main/java/net/minegasm/neoforge/MinegasmHubScreen.java:52-60`
- `modern/src/main/java/net/minegasm/neoforge/MinegasmHubScreen.java:133-154`
- `modern/src/main/java/net/minegasm/neoforge/MinegasmHubScreen.java:201-207`
- `classic/common/src/main/java/net/minegasm/classic/ClassicCommands.java:55-65`

`OutputState` is a good direction, but it is currently a second volatile value beside `outputEnabled`, not the single authority the response describes.

Two deterministic bad transitions remain:

1. If the user is in `USER_STOPPED` and the worker later stalls, `emergencyStop` overwrites the state with `WATCHDOG_STOPPED`. Once a healthy heartbeat returns, watchdog recovery changes it to `RUNNING` and enables output. The watchdog has therefore cleared a user panic.
2. While the state is `WATCHDOG_STOPPED`, the hub button and `/mg resume` call `clearUserStop` without checking the current cause. That method sets `RUNNING` and enables every backend even if the worker is still stalled.

The methods that enter and clear user stop are not serialized with watchdog entry and recovery either, so concurrent transitions can leave `outputState`, `outputEnabled`, and backend latches out of agreement. The hub makes this easier to trigger: its banner reads the live boolean, but its button label is built once and its refresh conditions do not observe output state. A watchdog event can leave a visible `Stop` button whose click actually resumes output.

Recommendation: replace the enum plus boolean pair with one safety-state authority. Model independent blocking causes, such as user stop, watchdog fault, global disable, lifecycle stop, and backend fault. Output is permitted only when no blocking cause is active. A user resume may clear only the user-stop cause. Watchdog recovery may clear only the watchdog cause after a defined healthy criterion, and it must never clear a user latch. Return a structured transition result so commands and screens can say why resume was accepted or refused. Drive every screen from one immutable `OutputStatus` snapshot and test the full transition matrix, including concurrent events.

### P1-3: Bridge exclusivity does not retract a scene already held by the adapter

Locations:

- `engine/src/main/java/net/minegasm/runtime/GovernedSceneForwarder.java:57-87`
- `engine/src/main/java/net/minegasm/runtime/GovernedSceneForwarder.java:98-126`
- `engine/src/test/java/net/minegasm/runtime/GovernedSceneForwarderTest.java:158-169`
- `docs/bridge/xtoys/main.go:372-455`

The new per-role filter works only when competing scenes first arrive in the same forward call. It does not cancel a lower-priority scene that was forwarded on an earlier cycle and is still live in the adapter.

For example, assume a continuous ambient scene at level 0.9 has already reached XToys. A higher-priority exclusive warning at level 0.3 then appears on the same role. The forwarder drops ambient from the current Java list and sends warning, but the wire protocol sends no scene cancellation or authoritative role snapshot. XToys keeps both live scenes until their separate TTLs expire and chooses the maximum, so ambient remains at 0.9 and the exclusive warning does not take control. The new test starts both scenes together, which misses this stateful case.

Recommendation: finish the backend-neutral logical destination resolver and make its output authoritative. Resolve routing, priority, exclusivity, ducking, and body-region conflicts once, then give each backend a resolved snapshot or ordered delta with a generation. The bridge protocol needs either destination snapshots or explicit tombstones so removal is as meaningful as addition. Buttplug should render that same resolved model instead of resolving conflicts independently. Add cross-backend conformance tests before hardware feel tests. This is final-state architecture and should be implemented before more adapters make the current split harder to change.

### P1-4: XToys reconnect can lose an owed zero or replay stale output

Locations:

- `docs/bridge/xtoys/main.go:270-305`
- `docs/bridge/xtoys/main.go:372-479`
- `docs/bridge/xtoys/main.go:488-524`
- `docs/bridge/xtoys/main_test.go:121-168`

The new reconnect loop and full-state resend are worthwhile, but the transfer is not transactional.

`resyncAll` clears `pendingZero` before it knows that all six role writes succeeded. If the socket fails during that resend while there are no active scenes, the adapter can finish with `pendingZero` false and `active` empty. The reconnect loop then has no reason to dial again, even though XToys may still be holding a nonzero value.

A failed nonzero `WriteMessage` is also ambiguous: the peer may have received the frame even though the local call reports an error. `onDisconnected` decides whether a zero is owed only from the last confirmed values, not from the nonzero write that just failed. If that scene expires while offline, the zero obligation can be lost.

The adapter also holds its global mutex during the WebSocket dial and every downstream write. A stop from Minegasm can wait behind a five-second dial or write. If a reconnect is already dialing, it can acquire the connection, resend the stale active nonzero snapshot, and only then let the waiting stop clear it.

Recommendation: use a single-owner connection actor or an equivalent generation-based state machine. Snapshot desired output under the state lock, perform dial and writes outside it, then commit delivery state only if the generation is still current. Keep the zero obligation latched until a complete authoritative zero state has been delivered successfully. A stop must advance the generation immediately, invalidating any in-flight resync before it can commit or replay. Add fault-injection tests that fail each role of resync, race stop against dial, race expiry against write failure, and confirm that zero remains owed until delivery.

### P1-5: Buttplug queued writes are not scoped to a connection session

Locations:

- `engine/src/main/java/net/minegasm/buttplug/b4j/Buttplug4jProvider.java:231-252`
- `engine/src/main/java/net/minegasm/buttplug/b4j/Buttplug4jProvider.java:295-348`
- `engine/src/main/java/net/minegasm/buttplug/StopCompensation.java:19-31`
- `engine/src/test/java/net/minegasm/buttplug/StopCompensationTest.java:17-40`

The compensating stop closes the same-session race from the previous review: if `stopAll` changes the send epoch while a blocking write is executing, another stop is queued after that write. The helper test demonstrates that sequencing.

The epoch is not advanced by `disconnect`, a detected socket drop, or a new connection session. Registry generation is checked before a command enters the send executor, but not again when the queued task executes. A backlog created before a drop can therefore survive long enough to run after reconnect and look up features from the new client device list. Those are stale commands from a different transport and registry session.

Recommendation: scope every output task to both a connection generation and a registry generation, rechecking them immediately before dispatch. Advance the output generation on every stop, disconnect, transport loss, connect replacement, and close, then purge or invalidate queued work. Introduce an injectable buttplug4j client seam and test blocked writes, queued writes, disconnect, reconnect, device-index reuse, and final stop ordering at provider level. The dependency-free helper is useful, but it is not a substitute for testing the real integration boundary.

### P1-6: Safety lifecycle failures are still silently discarded

Locations:

- `engine/src/main/java/net/minegasm/backend/BackendCoordinator.java:71-75`
- `engine/src/main/java/net/minegasm/backend/BackendCoordinator.java:159-208`
- `engine/src/main/java/net/minegasm/backend/BackendCoordinator.java:217-230`
- `engine/src/test/java/net/minegasm/backend/BackendCoordinatorTest.java:38-51`

Render exceptions are now recorded, stopped, and quarantined. That resolves the central part of the earlier finding. Lifecycle calls still use `guard` and discard the result. This includes ordinary panic stop, watchdog emergency stop, pause, resume, start, close, and output-latch fan-out.

The test for a throwing panic stop proves that other backends are still reached, which is correct, but it does not require the failed stop to become visible. A backend can fail the operation most important to safety while the runtime and UI continue to report a generic stopped state. Asynchronous provider stop failures are even further removed from this model because the backend returns before their completion stage settles.

Recommendation: make backend lifecycle operations return typed outcomes and feed them into persistent backend health. Fan-out must still continue after one failure, but the failed backend should enter an unresolved fault state and the global output status should say that stop delivery was not confirmed. Track asynchronous completion where the provider API is asynchronous. The hub, status command, logs, and recovery action should all read the same health snapshot.

### P2-1: XToys bounds do not yet bound clients or retained key memory

Locations:

- `docs/bridge/xtoys/main.go:43-49`
- `docs/bridge/xtoys/main.go:88-100`
- `docs/bridge/xtoys/main.go:118-151`
- `docs/bridge/xtoys/main.go:325-338`
- `docs/bridge/xtoys/main.go:372-389`

The global scene count and listener count are now finite. They do not implement the full bound described in the prior recommendation.

The TCP accept loop still creates an unlimited number of client goroutines and scanners. Connections after the listener limit still drive output; they are only omitted from status notifications. Scene IDs and continuous keys can consume most of the one-megabyte line allowance, so 1,024 retained keys can still approach a gigabyte. There is no per-client quota, maximum key length, or maximum TTL.

The reconnect loop also dials only while a scene is active or a zero is owed. If XToys returns while the system is idle, the adapter remains unavailable and the hub cannot show a ready chain until the next effect arrives. That contradicts the response's claim that the link is kept alive independently of output changes.

Recommendation: enforce a real client limit at accept time, per-client scene quotas, a small key limit, a sane TTL ceiling, and explicit rejection or closure for excess input. Keep the downstream connection supervised with bounded backoff while the adapter is running, or at least while one Minegasm client is connected, so connection state is useful before a test starts.

### P2-2: Backend fault visibility and recovery are incomplete

Locations:

- `engine/src/main/java/net/minegasm/client/MinegasmClient.java:376-394`
- `engine/src/main/java/net/minegasm/client/MinegasmClient.java:652-678`
- `modern/src/main/java/net/minegasm/neoforge/MinegasmHubScreen.java:127-185`
- `classic/1.16.5-common/src/main/java/net/minegasm/classic/HubScreen16.java:120-169`
- `classic/common/src/main/java/net/minegasm/classic/BridgeStatus.java:17-49`

Bridge rows now show quarantine, but the Buttplug row never checks `buttplugFaulted`. Its hub refresh keys also do not observe Buttplug quarantine. A quarantined Buttplug backend can therefore remain labelled ready with its device count even though it has been removed from output fan-out.

`MinegasmClient.connect` clears Buttplug quarantine before URL parsing, remote-host validation, or connection success. An invalid or refused connect attempt can re-enable the faulted backend without proving that anything changed. Bridge transport reconnect does not clear bridge quarantine, despite the broad wording in the response, and the UI has no explicit recovery result.

Recommendation: represent integration health independently from transport connection state. Show fault, last transition time, and the failed operation on every loader. Clear quarantine only after a successful, generation-scoped recovery handshake or an explicit user action that returns a result. Do not let a connect request itself count as recovery.

### P2-3: Integer pack fields still accept fractional and overflowing numbers

Locations:

- `engine/src/main/java/net/minegasm/pack/ScenePackCodec.java:302-342`
- `engine/src/test/java/net/minegasm/pack/ScenePackCodecTest.java:69-86`

Quoted numbers and non-finite floats are now rejected, which closes the most visible part of the previous parser finding. Integer fields still use Gson's `getAsInt` after checking only that the JSON primitive is numeric. That accessor can coerce a fractional number by truncation and can narrow a value outside the integer range before duration clamping or schema validation sees it.

Recommendation: parse integer fields through an exact decimal representation, require zero fractional scale, and reject values outside the Java integer range before conversion. Add cases for `1.5`, negative fractions, exponent notation at the boundary, positive and negative overflow, and huge exponents. Also enforce the one-megabyte pack limit on the read itself rather than relying only on a size check followed by `readAllBytes`.

## Status of Claude's response

- **Watchdog reachability and latch: partially resolved.** The active path is fixed, but pause and unload can block first, and stop causes can overwrite each other.
- **Buttplug in-progress write compensation: substantially resolved for one connection.** Disconnect and reconnect do not invalidate queued work, and the real provider boundary remains untested.
- **Bridge removal and stop generation: resolved for governed scene fan-out.** The generation prevents a governed effect from being appended behind a stop. Direct test injection is still outside that generation.
- **Backend render quarantine: substantially resolved.** Render faults quarantine correctly. Lifecycle failures, Buttplug hub visibility, and recovery ordering remain open.
- **XToys reconnect and state resend: partially resolved.** Happy-path resend works. Partial resync, ambiguous writes, and stop races remain unsafe.
- **Bridge priority and exclusivity: not resolved for already-forwarded scenes.** Filtering additions cannot retract adapter state. The final central resolver is still required.
- **Bridge structural fingerprint: resolved for the current primitive model.** The new signature covers the reviewed non-level shape fields.
- **Unsupported delivery modes: resolved.** Only the implemented mode is accepted.
- **TCP writer cleanup: resolved for the reviewed leak.** Failed transports shut down their writer and are closed before replacement.
- **XToys client namespace and bounds: partially resolved.** Namespacing works. Client, per-client, key, and TTL bounds remain incomplete.
- **Pack numeric validation: partially resolved.** Numeric type and finite float checks work; exact integer validation remains open.

## UX direction

The UI should now be built around truthful state and recovery, not around more toggles.

1. **Make safety status a shared product model.** Every screen should consume the same immutable `OutputStatus`, with active blocking causes, first and last transition times, backend acknowledgements, and permitted actions. The visual treatment can vary by Minecraft version, but the state and wording should not.
2. **Separate Stop from Resume.** Stop is always available. Resume appears only for a user stop and only clears that cause. A watchdog fault should show `Waiting for healthy output loop` or `Recovery required`, not reuse a generic Resume action.
3. **Show integration state as a chain.** For a bridge, show Minegasm, adapter, downstream service, and physical-output readiness as distinct hops. For Buttplug, show provider connection, device registry, backend health, and output permission. Do not label a quarantined integration ready.
4. **Give recovery an observable result.** Reconnect, retry, resume, reload, and test should report accepted, running, completed, refused, or failed with a concise reason. This is the right place for the previously deferred structured command results.
5. **Make tests controllable.** Show the target integration, features or roles, level, remaining duration, and a Stop test action. A test should be rejected with a visible reason when any safety cause is active.
6. **Finish the pack manager flow.** Keep the selected pack in view, show metadata and file-specific validation errors, and provide Reload and Open folder. Surface exact numeric and schema errors without collapsing them into a generic load failure.

This should be implemented as a shared view-state and action-result layer in the engine-facing client API. Repeating boolean logic across the modern and classic screens has already produced stale labels and different fault visibility.

## E-stim and schema scope

### E-stim

E-stim is not a blocker for this beta because there is no supported e-stim modality and the XToys documentation now says not to route the generic adapter to it. That fail-closed product boundary is acceptable for the current scope.

If e-stim is part of the final release, ADR-016's architecture must be implemented and reviewed before it is exposed: a distinct capability, native-unit limits, timed arming, finite pulses, cooldown, whole-body accounting, physical confirmation, a threat model, hardware-in-the-loop tests, and a dedicated safety review. If final release still excludes e-stim, keep the capability absent rather than adding a partial arming facade.

### Schema evolution

Maintaining beta schema versions and writing migrations for data shapes that are still changing is not a blocker. Before the first stable release, the project does need an explicit compatibility contract for config and packs, backup and rollback behavior, downgrade handling, atomic migration, and tests across every supported stable version. Define that ownership as part of the final architecture, then add concrete migrations when a stable schema actually exists.

## Verification performed

- Modern `chiseledBuild`: passed across the configured Fabric, Forge, and NeoForge matrix.
- Classic `build`: passed across 1.7.10 Forge, 1.8.9 Forge, 1.12.2 Forge, and both 1.16.5 loaders.
- Fresh modern `:1.21.1-neoforge:test`: passed with all tasks executed.
- Fresh classic `:1.12.2-forge:test`: passed with all tasks executed.
- Fresh XToys `go test -count=1 ./...`: passed.
- XToys `go vet ./...`: passed.
- Whitespace and patch-integrity check from `828a67b` through `HEAD`: passed.

The Go race detector could not run because this Windows Go installation has CGO disabled. No hardware, in-game, live Intiface, or live XToys smoke test was performed.

## Recommended order of work

1. Build the single safety-state authority and independent watchdog, then test the complete transition matrix.
2. Make XToys stop and reconnect generation-safe, with zero obligations committed only after successful full-state delivery.
3. Build the backend-neutral logical destination resolver and an authoritative bridge snapshot protocol. Move Buttplug conflict handling behind that resolved model.
4. Scope Buttplug output work to connection and registry generations, and add an injectable provider test seam.
5. Make every backend lifecycle outcome observable and complete the shared integration health model across all UI families.
6. Finish adapter quotas and exact integer parsing, then run the manual hardware and live-bridge safety matrix.

The manual matrix should cover panic during a blocked write, watchdog stop, pause, world unload, client-thread delay, disconnect, reconnect, device-index reuse, adapter restart, partial XToys resync failure, backend quarantine, bridge repoint, and backend switching.
