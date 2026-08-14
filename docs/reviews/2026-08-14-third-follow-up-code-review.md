<!--
SPDX-AI-Disclosure: ai-generated
SPDX-AI-Model: gpt-5.6-sol
SPDX-AI-Provider: OpenAI
SPDX-AI-Reasoning: extra-high
SPDX-AI-Scope: Full repository third follow-up review performed by GPT-5.6 Sol with extra-high reasoning effort. The reviewer inspected the current implementation, tests, Claude's latest response, prior reviews, briefs, ADRs, safety documentation, build outputs, adapter behavior, and UX under human direction.
SPDX-AI-Date: 2026-08-14
-->

# Third follow-up comprehensive code review

Reviewed revision: `8a34f32a3651342e2999668e65d9403b9dfdfb7d`

Date: 2026-08-14

## Verdict

This pass is a real improvement. Independent stop causes close the state-clobbering bug, the watchdog now has its own scheduler, Buttplug4j writes carry connection and registry generations, the bridge protocol can retract vanished output, XToys state is bounded per client, exact integer parsing is in place, and backend stop exceptions are no longer silently discarded. The body-region work also gives the engine, packs, device configuration, and every loader UI a useful second routing axis. Both full Java build matrices and all fresh representative tests pass.

I would not close the review yet. Four P1 issues remain:

1. The bridge calls a nominal primitive amplitude the current output, so timing and signal shapes are wrong.
2. The central resolver is not yet a complete logical-destination stage. It resolves inactive layers, ignores output class and route, and accounts fatigue before suppression.
3. XToys still holds its state mutex during a potentially five-second WebSocket write, and an ambiguous failed write can still lose an owed zero.
4. Asynchronous send and stop failures do not reach backend quarantine or a confirmed-stop state.

Those are behavioral and architectural gaps, not a request for small beta patches. The right next pass should finish the shared destination, lifecycle-outcome, and view-state models that the final release needs.

E-stim support and beta schema migration are not counted as blockers, per project direction. Their eventual release requirements remain recorded near the end.

## Scope and approach

This was another review of the full maintained implementation, not only the commits after `6fbd655`. I revisited the engine, runtime safety and lifecycle paths, Buttplug providers, backend coordination, bridge protocol and transports, XToys and reference adapters, scene packs, body-region routing, modern and classic loader UIs, tests, the prior reviews, Claude's latest response, relevant briefs, ADRs, and safety documentation. Historical prototypes were not treated as shipping code.

The latest response was used as an entry point, then each claim was traced into its callers, state transitions, failure paths, and tests. I also compared the Buttplug renderer and bridge forwarder at the same scene time instead of treating receipt of the same scene list as proof of equivalent output.

## Open findings

### P1-1: The bridge's authoritative output is not the current output

Locations:

- `engine/src/main/java/net/minegasm/runtime/BridgeRoleForwarder.java:51-76`
- `engine/src/main/java/net/minegasm/runtime/SceneMixer.java:51-69`
- `engine/src/main/java/net/minegasm/render/PrimitiveEvaluator.java:16-40`
- `engine/src/test/java/net/minegasm/runtime/CrossBackendResolutionTest.java:35-68`
- `engine/src/test/java/net/minegasm/runtime/BridgeRoleForwarderTest.java:35-81`

`BridgeRoleForwarder.forward` receives `nowNs`, but `rolesOf` never uses it. It takes `layer.primitive().level()`, which is the authored or peak amplitude, not the instantaneous level. It also does not check the layer's start offset or expiry. The Buttplug renderer does both checks and evaluates the primitive through `PrimitiveEvaluator.levelAt`.

The result is a substantial cross-backend mismatch:

- A delayed layer starts immediately on the bridge.
- A short layer can continue until its parent scene expires.
- An impulse loses its attack and release.
- A sweep becomes a constant peak.
- A beat pattern loses its gaps.
- A texture or rumble loses modulation.
- An oscillation becomes a constant scalar.

The new conformance test cannot catch this because it uses a zero-offset `Hold` at a time when its nominal and instantaneous levels are identical. The bridge tests use the same special case.

This is more than a feel difference. It can make an external toy run earlier, longer, and more continuously than the in-process backend for the same governed scene.

Recommendation: define one coherent final wire model and test it against every primitive and timing boundary. My preference is a central, time-aware `ResolvedDestinationSnapshot` sampled once per governance cycle. It should carry normalized intended output keyed by logical destination, plus a generation, while each backend retains physical capability mapping and hard caps. If the project instead wants a semantic scene protocol, send an authoritative full set of resolved layers with timing and primitive data, and require adapters to evaluate that model. The current hybrid, an authoritative snapshot made from unevaluated peaks, should not remain.

### P1-2: Logical-destination resolution is still incomplete and runs in the wrong order

Locations:

- `engine/src/main/java/net/minegasm/runtime/SceneGovernor.java:93-131`
- `engine/src/main/java/net/minegasm/runtime/SceneGovernor.java:134-188`
- `engine/src/main/java/net/minegasm/runtime/SceneMixer.java:74-125`
- `engine/src/main/java/net/minegasm/core/HapticRoute.java:18-42`
- `engine/src/main/java/net/minegasm/core/BodyRegion.java:4-18`
- `docs/briefs/0003-shareable-haptics-and-multi-backend.md:157-176`

The body-region axis is useful, but the new resolver is not yet the single final authority described in the response.

First, `resolveExclusivity` has no time parameter. It collects every exclusive layer in every live scene, including a layer whose start offset is still in the future, whose own lifetime ended while another layer keeps the scene alive, or whose primitive currently evaluates to zero. Such a layer can suppress a lower-priority layer before it starts or after it ends. The Buttplug mixer skips inactive layers, while the bridge bug above drives them, so the supposedly shared resolved set still produces different behavior.

Second, competition is keyed only by role and body region. It ignores the layer route and output class. A position-only exclusive can therefore suppress a vibration-only layer even though those outputs do not compete. Concrete feature filters can be device-specific, but the engine still needs a device-neutral output-class axis so strength, motion, constriction, and any future restricted modality do not collapse into one destination.

Third, fatigue accounting happens before exclusivity. In the existing conformance example, a quiet exclusive at `0.3` suppresses a louder layer at `0.9`, but `achievedByRole` records `0.9` before the resolver drops it. Fatigue is therefore based on output the central model says did not survive. It is also still per role, not per role and body region as the multi-backend brief describes.

Finally, the bridge output is keyed only by role. Two surviving effects with the same role in different body regions are collapsed to one maximum, so an XToys adapter cannot route them independently. Phase 2 currently reaches Buttplug devices, but not the backend-neutral protocol.

Recommendation: finish the destination model before adding more adapter behavior. A destination should include at least role, body region, and output class. Resolve active timing, priority, exclusivity, and ducking against that model first. Account fatigue and future aggregate body load from the surviving intended output, then fan out one immutable snapshot. Device routes and caps can refine the snapshot without changing its logical competition. Add cross-backend tests for delayed and expired layers, zero phases, disjoint regions, disjoint output classes, whole-body overlap, and fatigue after suppression.

### P1-3: XToys stop delivery can still wait behind a downstream write

Locations:

- `docs/bridge/xtoys/main.go:341-371`
- `docs/bridge/xtoys/main.go:382-448`
- `docs/bridge/xtoys/main.go:451-482`
- `docs/reviews/2026-08-13-second-follow-up-review-response.md:39-51`

Moving the WebSocket dial outside the state mutex fixes the dial half of the previous finding. Writes still happen while the same mutex is held. `applyOutput`, `stopClient`, expiry, and reconnect resync all enter `recompute` under `x.mu`; `recompute` calls `send`; and `send` permits a WebSocket write to block until its five-second deadline.

A panic frame, client disconnect, TTL expiry, or a newer authoritative zero must acquire that mutex before it can change state. It can therefore wait behind the exact downstream operation it is trying to supersede. That conflicts with the comment that a stop is never delayed by the XToys path.

Claude's response correctly acknowledges the remaining ambiguous-write edge. If a nonzero write reached XToys but returned an error before the adapter could record it, then the last client disconnects, `pendingZero` may remain false. With no client, state, or recorded zero obligation, the reconnect loop has no reason to reconnect and deliver zero.

Recommendation: complete the single-owner connection actor or equivalent generation state machine already identified in the response. State changes and stop generations must be immediate. Dial and write should happen outside the state lock. Delivery state should commit only if the generation is still current, and uncertainty after any failed nonzero write should conservatively latch a zero obligation until a complete zero snapshot is acknowledged by a successful write. Add fault injection at every role of resync and races between panic, disconnect, expiry, reconnect, and blocked writes.

### P1-4: Late provider failures still cannot become unresolved backend faults

Locations:

- `engine/src/main/java/net/minegasm/backend/ButtplugBackend.java:65-77`
- `engine/src/main/java/net/minegasm/backend/ButtplugBackend.java:105-123`
- `engine/src/main/java/net/minegasm/backend/BackendCoordinator.java:159-208`
- `engine/src/main/java/net/minegasm/buttplug/b4j/Buttplug4jProvider.java:237-301`
- `engine/src/main/java/net/minegasm/buttplug/b4j/Buttplug4jProvider.java:305-337`

The coordinator now records a backend whose synchronous `stop` or `emergencyStop` call throws. That is useful, but neither Buttplug backend operation is synchronous at the hardware boundary.

`ButtplugBackend` discards the completion stages returned by `provider.send` and `provider.stop`. Buttplug4j dispatch runs later on an executor and catches device exceptions without reporting them. Its stop runs later on a separate executor, catches exceptions, writes only provider status, and still completes normally. The worker records a healthy cycle as soon as the enqueue calls return.

This means a stop can be accepted locally, fail later, and never quarantine the backend or enter an explicit unconfirmed-stop state. The UI can show the runtime as stopped while the physical result is unknown. A blocked output writer can also leave the worker heartbeat healthy forever because enqueueing continues even though delivery has stopped making progress.

Recommendation: make lifecycle and delivery outcomes part of the backend contract. A backend should report accepted, delivered, failed, timed out, and superseded outcomes with operation and session generations. Track stop requested separately from stop confirmed. A failed or timed-out stop must remain a visible unresolved fault and must not be cleared by an unrelated connection state change. Add an injectable Buttplug4j client seam and provider-level tests for blocked writes, late send errors, late stop errors, disconnect, reconnect, device-index reuse, and stop ordering. The current helper tests do not exercise the library boundary.

## P2 findings

### P2-1: The new output status is neither the single source nor an accurate global gate

Locations:

- `engine/src/main/java/net/minegasm/runtime/OutputStatus.java:7-77`
- `engine/src/main/java/net/minegasm/runtime/StopCause.java:3-21`
- `engine/src/main/java/net/minegasm/client/MinegasmClient.java:666-681`
- `modern/src/main/java/net/minegasm/neoforge/MinegasmHubScreen.java:53-69`
- `modern/src/main/java/net/minegasm/neoforge/MinegasmHubScreen.java:226-269`
- `classic/common/src/main/java/net/minegasm/classic/ClassicCommands.java:231-359`

Independent worker causes are implemented correctly, but the aggregate object is not used by the screens or commands. They continue to read `isOutputEnabled`, `isUserStopped`, `isWatchdogStopped`, config enablement, and backend faults independently. `MinegasmClient.outputStatus()` has no consumers.

Its model is also internally misleading. Any quarantined backend adds `BACKEND_FAULT`, and `OutputStatus.permitted()` becomes false whenever any cause exists. Quarantine is intentionally per backend, so healthy backends continue to drive. The aggregate can therefore say output is not permitted while output is flowing through another integration.

There is a smaller compound-state UX bug too. `userResumable()` returns true whenever `USER_STOP` is present, even when `WATCHDOG` is also present. The hub checks user stop first and offers Resume in that compound state. Clicking it is safe because only the user cause clears, but the button promises a result it cannot deliver.

Recommendation: replace this with a shared view state that separates the global output gate from per-backend health and actual body-driving state. Every loader and command should consume the same immutable state and the same structured action results. Show all active causes, not one precedence-selected reason. A backend fault should identify the integration and its last failed operation without pretending every healthy integration stopped.

### P2-2: The independent watchdog is not tested as an independent scheduler and its check is raced

Locations:

- `engine/src/main/java/net/minegasm/runtime/HapticRuntime.java:190-218`
- `engine/src/main/java/net/minegasm/runtime/Watchdog.java:14-46`
- `engine/src/test/java/net/minegasm/runtime/HapticRuntimeWatchdogTest.java:21-69`

The new scheduled thread closes the important pause and unload hole by inspection. The regression test still never calls `HapticRuntime.start`; it manually invokes the active client tick and its documentation still says the client tick is the watchdog's caller. Nothing currently proves that the timer fires while the client thread is blocked in pause or world unload.

`Watchdog.check()` is now called concurrently by the timer and client tick, but `lastFiredNs` is an ordinary unsynchronized field. Two checks can both fire, or a check that captured an old heartbeat can add a watchdog cause after another check observed recovery. The next poll should correct the false stop, but safety state should not depend on an untested data race. `HapticRuntime.start()` can also create another watchdog executor if called twice.

Recommendation: make the check transition atomic, make runtime start idempotent, inject the scheduler or poll trigger, and test the real timer path while pause, unload, and a worker cycle are blocked. Record timer exceptions in bounded diagnostics instead of silently discarding them.

### P2-3: The XToys adapter ignores the protocol version it documents as mandatory

Locations:

- `docs/bridge/PROTOCOL.md:23-26`
- `docs/bridge/xtoys/main.go:128-146`
- `docs/bridge/reference-adapter.py:41-49`

The protocol says an adapter should refuse an unknown version. The reference adapter does. XToys parses only `type` and accepts `output` or `stop` without reading `v`, so an incompatible future or malformed client can still drive output under version 1 assumptions.

Recommendation: validate `v` before any state mutation, close or explicitly reject incompatible clients, and add tests for missing, fractional, wrong-type, and unknown versions. Keep protocol negotiation fail-closed before adding version 2 destination fields.

## UX recommendations

The new fault badge and separate watchdog label are helpful. The next UX pass should be built on the shared state and result models above so behavior stays identical across loaders.

1. **Use one output summary with honest scope.** Show the global gate, whether any backend is currently body-driving, and each integration's health separately. If user stop and watchdog are both active, show both. Put the recovery action beside the cause it can actually clear.
2. **Turn integration rows into compact status cards.** Include connection, device count, downstream state, quarantine, last failed operation, and last transition time. Offer Connect, Reconnect, Test, and Details in context. A completed action should report what happened instead of relying on a later label refresh.
3. **Replace the body-region click cycle with a real chooser.** Cycling through Not set plus eight enum values is slow and easy to overshoot. Use a list or grid with a short explanation. Keep Unassigned and Whole body visibly distinct, and explain that they currently route the same way.
4. **Preview routing consequences before save.** A device row should say which kinds of built-in effects it will receive. For example, a nipple-tagged device will not receive the built-in XP, advancement, or fishing reward layers because those are tagged genital. Add a bounded Test this region action so the user can verify placement without triggering unrelated devices.
5. **Make pack mappings inspectable.** Keep the selected pack visible, show metadata and event-to-region mappings, surface the exact file and validation error, and provide Reload and Open folder. Region-specific built-in choices should not be discoverable only in a design document.
6. **Show structured test results.** Report integration, target set, cap, duration, and whether the command was delivered, rejected, timed out, or superseded. This is not minor presenter cleanup; it is the user-facing half of the lifecycle outcome architecture.

## Response and implementation status

- **Independent stop causes, mostly resolved.** User and watchdog causes no longer overwrite each other. Shared UI consumption remains open.
- **Independent watchdog thread, implemented with incomplete coverage.** The scheduler exists. Its concurrent check and blocked-lifecycle timer path are untested.
- **Authoritative bridge retraction, partially resolved.** Vanished roles now become zero, but timing, shapes, region, and output class are lost.
- **Central exclusivity, partially resolved.** Coarse role and region suppression exists. Active timing, output class, route, and post-resolution fatigue remain open.
- **XToys state and quotas, mostly resolved.** Clients, per-client state, line size, and TTL are bounded. Writer serialization and ambiguous delivery remain open.
- **Buttplug4j session scoping, implemented with an untested seam.** Epoch, connection generation, and registry generation are rechecked. No real client seam test covers the library boundary.
- **Stop failure visibility, partially resolved.** Synchronous exceptions are recorded. Late completion failures are not.
- **Hub fault display, partially resolved.** Buttplug quarantine is visible. A shared multi-backend health and action-result model is still absent.
- **Exact integer parsing, resolved.** Fractional and out-of-range integer fields fail closed.
- **Body-region Phase 2, partially resolved.** Engine, packs, Buttplug routing, config, and loader UIs are present. Bridge destinations and region-aware fatigue are not.

## E-stim and schema scope

### E-stim

E-stim is not a blocker for this beta because the engine exposes no supported e-stim modality and the generic XToys documentation says not to route it to e-stim. Keep that capability absent and fail-closed.

If e-stim becomes part of the final release, ADR-016 still needs to be implemented as a distinct capability with native-unit limits, timed arming, finite pulses, cooldown, aggregate body accounting, physical confirmation, a threat model, hardware-in-the-loop tests, and a dedicated safety review. It must not be inferred from an ordinary role or region scalar.

### Schema evolution

Maintaining beta schema versions and migration chains is not a blocker while the data model is still moving. Before the first stable release, define the compatibility contract for config, packs, and bridge protocol, including backup, rollback, downgrade behavior, atomic migration, and supported-version tests. The architectural ownership should be decided now; concrete migration chains can wait until a stable schema exists.

## Verification performed

- Full modern `chiseledBuild`: passed across the configured Fabric, Forge, and NeoForge matrix. All 140 tasks completed successfully.
- Full classic `build`: passed across 1.7.10 Forge, 1.8.9 Forge, 1.12.2 Forge, and both 1.16.5 loaders. Nine of 27 tasks executed and the remainder were up to date.
- Fresh modern `:26.2-neoforge:test`: passed with all six tasks executed.
- Fresh classic `:1.12.2-forge:test`: passed with all four tasks executed.
- Fresh XToys `go test -count=1 ./...`: passed.
- XToys `go vet ./...`: passed.
- Python reference adapter syntax check: passed.
- Repository patch-integrity check: passed.

The first parallel Java matrix attempt exceeded the command wrapper timeout and left three Gradle invocations running. Those exact invocations and their test workers were terminated, then all Java checks above were rerun sequentially and completed successfully.

The Go race detector was not run. No hardware, in-game, live Intiface, or live XToys smoke test was performed. The Buttplug4j library boundary still has no injectable provider-level test seam.

## Recommended order of work

1. Replace the bridge's nominal role peaks with a time-aware, destination-complete authoritative model.
2. Finish central resolution around role, body region, and output class, then move fatigue accounting after resolution.
3. Complete the XToys single-owner writer and generation-safe zero obligation.
4. Make send and lifecycle completion observable, with explicit requested, confirmed, failed, timed-out, and superseded outcomes.
5. Replace duplicated UI predicates with the shared global gate, per-backend health, and structured action-result model.
6. Add real independent-watchdog, cross-backend timing, provider-boundary, and adapter fault-injection tests.
7. Run the hardware and in-game feel matrix, then tune mappings and thresholds without changing the architecture.

The manual matrix should cover panic during a blocked write, simultaneous user and watchdog stops, pause, world unload, client-thread delay, device-write backlog, late stop failure, disconnect, reconnect, device-index reuse, adapter restart, partial XToys resync, backend quarantine, bridge repoint, backend switching, region routing, output-class separation, and every primitive shape at its timing boundaries.
