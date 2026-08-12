<!--
SPDX-AI-Disclosure: ai-generated
SPDX-AI-Model: gpt-5.6-sol
SPDX-AI-Provider: OpenAI
SPDX-AI-Scope: Comprehensive repository review performed by GPT-5.6 Sol with extra-high reasoning effort. The model inspected the implementation, tests, briefs, ADRs, and UX, then wrote and verified this report under human direction.
SPDX-AI-Date: 2026-08-12
-->

# Comprehensive Code Review

Date: 2026-08-12  
Reviewed revision: `98ee7b4` on `main`, including the entire maintained implementation rather than only the commits after `origin/main`.

## Executive summary

The implementation has a strong device-neutral core, useful separation between engine and loader code, broad cross-version compilation, monotonic timing, bounded outbound queues, and substantially better automated coverage than the historical code. Both build matrices pass.

It is not ready to ship as a safety-sensitive multi-backend implementation. The most serious issue is that the bundled XToys adapter explicitly permits an ordinary `0..1` scene scalar to be routed to e-stim. That bypasses the repository's own binding requirements for a distinct modality, multi-step arming, independent hard limits, finite pulses, whole-body governance, a threat model, and a safety review. The emergency-stop path also has several independent holes: it can block behind the worker it is meant to stop, the buttplug4j stop is asynchronous, a bridge added while panicked starts enabled, and an isolated Buttplug test can resume after panic is cleared.

Priority counts:

- **P0:** 1 release blocker involving potential physical harm.
- **P1:** 7 high-priority safety or output-control defects.
- **P2:** 8 correctness, robustness, compatibility, or UX defects.

Severity meanings:

- **P0:** do not ship or advertise this path until resolved.
- **P1:** fix before considering the multi-backend implementation production-ready.
- **P2:** fix in the next hardening cycle; some become P1 in hostile or failure-prone environments.

## Scope and verification

Reviewed:

- `engine/src/main` and `engine/src/test`
- modern Fabric, Forge, and NeoForge loader code and screens
- classic 1.7.10, 1.8.9, 1.12.2, and 1.16.5 loader code and screens
- the TCP bridge, protocol, Python reference adapter, and bundled XToys Go adapter
- architecture, safety documentation, briefs, ADRs, status, and testing documentation

`prototypes/` was treated as historical/non-shipping code. No hardware was attached and no in-game visual test was performed, so device-library behavior and rendered layouts were reviewed statically.

Verification performed:

- `modern/.\gradlew.bat chiseledBuild --warning-mode all` passed for every configured modern target (128 tasks, up-to-date).
- `classic/.\gradlew.bat build --warning-mode all` passed for every configured classic target (27 tasks, up-to-date).
- `modern/.\gradlew.bat :26.2-neoforge:test --rerun-tasks` passed with all six tasks freshly executed.
- `classic/.\gradlew.bat :1.12.2-forge:test --rerun-tasks` passed with all four tasks freshly executed.
- `go test ./...` in `docs/bridge/xtoys` compiled successfully. The adapter has no Go test files.

Passing builds are a useful baseline, but they do not exercise the safety interleavings and semantic gaps below.

## Findings

### [P0-1] The XToys adapter turns ordinary scene scalars into a supported e-stim path before any e-stim safety model exists

Locations:

- `docs/bridge/xtoys/main.go:8-12`
- `docs/bridge/xtoys/README.md:8-12`
- `docs/bridge/xtoys/main.go:137-164`
- `docs/bridge/xtoys/main.go:239-246`
- `engine/src/main/java/net/minegasm/bridge/BridgeCodec.java:16-19`
- `docs/adr/ADR-016-electrostim-opt-in-modality.md:31-60`
- `docs/adr/ADR-016-electrostim-opt-in-modality.md:65-73`
- `docs/SAFETY.md:55-57`

The adapter documentation explicitly says each generic XToys output may drive an e-stim device. The adapter reduces a scene to the maximum primitive level per role and maps every non-zero value into `[min, scale]`; the defaults include a 20% floor. The bridge intentionally omits route/output-kind information, so the adapter cannot distinguish a vibration-like effect from a restricted modality. This is exactly the “silent scalar bridge from an ordinary effect into a shock” that ADR-016 prohibits.

None of ADR-016's binding controls exists: no distinct electrostim capability, separate caps table, per-device enable, per-session timed arm, physical confirmation, maximum pulse, mandatory ramp, inter-shock interval, cooldown, aggregate body budget, or threat-model ship gate. Shared packs and ordinary game events can therefore indirectly drive whatever the user maps in XToys.

Recommendation:

1. Remove e-stim from the shipped adapter's supported-use claims immediately and treat connecting it as unsupported until the safety gate is complete.
2. Do not try to make this safe with only a lower `-scale`. A generic role scalar has insufficient modality and device information.
3. Before any supported e-stim release, implement ADR-016's distinct capability and policy end-to-end, including local-only routing, multi-step timed arming, hard native-unit limits, finite commands, cooldown/rate limiting, body-budget enforcement, physical confirmation, threat model, and dedicated safety review.
4. Add an acceptance test proving an imported pack and every ordinary bridge effect can never reach a shock-capable output while unarmed.

### [P1-1] The watchdog cannot stop a backend that hangs inside the synchronized worker cycle

Locations:

- `engine/src/main/java/net/minegasm/runtime/HapticWorker.java:95-107`
- `engine/src/main/java/net/minegasm/runtime/HapticWorker.java:115-120`
- `engine/src/main/java/net/minegasm/runtime/Watchdog.java:28-37`
- `engine/src/main/java/net/minegasm/backend/BackendCoordinator.java:52-58`
- `engine/src/main/java/net/minegasm/buttplug/b4j/Buttplug4jProvider.java:184-226`

`HapticWorker.cycle` holds the worker monitor while it invokes every backend inline. `Watchdog.check` detects a stale heartbeat and calls the synchronized `worker.stopAll`. If a backend blocks in `onGovernedScenes`, the watchdog blocks acquiring the same monitor and cannot issue the stop. The buttplug4j provider makes synchronous library calls such as `runVibrateFloat` in that path, so this is not merely a hypothetical future-backend violation.

The current design relies on the method-level contract that backends “must not block,” but a watchdog must protect against exactly that contract being broken by a library, socket, driver, or bug.

Recommendation: keep an out-of-band stop path that never needs the cycle lock. Isolate each backend behind a bounded single-consumer queue/actor, make the cycle publish immutable work without holding a global monitor across backend code, and let panic/watchdog atomically latch output off plus invoke an independent stop channel. Add a test backend whose render blocks indefinitely and prove watchdog/panic returns promptly and stops every other backend.

### [P1-2] buttplug4j does not satisfy the synchronous emergency-stop contract

Locations:

- `engine/src/main/java/net/minegasm/backend/HapticBackend.java:16-18`
- `engine/src/main/java/net/minegasm/backend/HapticBackend.java:65-66`
- `engine/src/main/java/net/minegasm/backend/ButtplugBackend.java:106-110`
- `engine/src/main/java/net/minegasm/buttplug/b4j/Buttplug4jProvider.java:229-259`
- `engine/src/main/java/net/minegasm/buttplug/b4j/Buttplug4jProvider.java:114-131`

`ButtplugBackend.stop` calls `provider.stop` and discards the returned stage. The buttplug4j provider schedules the actual stop on the same single-thread executor used for blocking connect and scan operations, then returns immediately. Panic can therefore return before a stop command is even attempted, and a slow/hung connect can delay it indefinitely.

Recommendation: make local suppression synchronous and dispatch the physical stop through a dedicated, priority stop path that cannot queue behind lifecycle work. If the library cannot provide a bounded synchronous stop, document and enforce a device-side command TTL or disconnect fallback. Test panic while the provider's normal executor is deliberately blocked.

### [P1-3] A bridge added or repointed while panic is latched starts output-enabled

Locations:

- `engine/src/main/java/net/minegasm/runtime/HapticWorker.java:38-40`
- `engine/src/main/java/net/minegasm/runtime/HapticWorker.java:163-170`
- `engine/src/main/java/net/minegasm/bridge/BridgeBackend.java:48-52`
- `engine/src/main/java/net/minegasm/runtime/HapticRuntime.java:194-225`

The worker's master panic latch is copied only to backends that exist when `setOutputEnabled` is called. `reconcileBridges` creates a new `BridgeBackend`, whose `outputEnabled` defaults to `true`, starts it, and adds it to fan-out without applying `worker.isOutputEnabled()`.

After panic clears existing scenes, gameplay can submit new scenes because panic is not the same as disabling configuration. Enabling, adding, renaming, or repointing a bridge during that state creates an output path that forwards those scenes despite the visible global stop latch.

Recommendation: make the coordinator own the authoritative output epoch/latch and apply it atomically before any backend becomes visible. Construct backends disabled, copy the current latch, then start/add them. Test panic → add/repoint bridge → submit scene → assert no effect; then explicitly resume and assert delivery.

### [P1-4] An isolated Buttplug test can resume after panic, stop, or pause

Locations:

- `engine/src/main/java/net/minegasm/backend/ButtplugBackend.java:42-47`
- `engine/src/main/java/net/minegasm/backend/ButtplugBackend.java:80-93`
- `engine/src/main/java/net/minegasm/backend/ButtplugBackend.java:106-135`
- `engine/src/main/java/net/minegasm/client/MinegasmClient.java:573-595`

The backend-local `testScene` is not cleared by `stop`, `pause`, `discardPause`, or `setOutputEnabled(false)`. If panic is cleared or output resumes before the test expires, the old test is appended to the next governed set and runs again. Unsafe tests can be configured for much longer than the normal 400 ms pulse, making the behavior material.

Recommendation: clear all backend-local test state on every stop-like transition, including panic, watchdog, pause, world unload, config reset, disconnect, backend swap, and close. Add a test that starts a long isolated test, panics, resumes before expiry, and observes no command.

### [P1-5] Backend exceptions are swallowed, but the worker is still marked healthy while prior output may remain held

Locations:

- `engine/src/main/java/net/minegasm/backend/BackendCoordinator.java:122-134`
- `engine/src/main/java/net/minegasm/runtime/HapticWorker.java:100-107`
- `engine/src/main/java/net/minegasm/runtime/Watchdog.java:28-37`

`BackendCoordinator.guard` silently catches every runtime exception. `HapticWorker.cycle` then unconditionally advances `lastHealthyCycleNs`. A backend that repeatedly fails after sending a non-zero held value is therefore invisible to health reporting; no backend stop is attempted, and the watchdog sees a healthy worker forever.

Recommendation: return per-backend results from fan-out. On an exception, latch that backend off, synchronously attempt its stop, quarantine it, and report a visible health error while allowing the other backends to continue. A worker cycle should not claim complete health when fan-out failed. Test a backend that throws after one non-zero frame and assert it is stopped/quarantined and the failure appears in diagnostics.

### [P1-6] Semantic bridges bypass the promised body-level governance

Locations:

- `engine/src/main/java/net/minegasm/runtime/SceneGovernor.java:92-130`
- `engine/src/main/java/net/minegasm/runtime/HapticWorker.java:100-106`
- `engine/src/main/java/net/minegasm/backend/HapticBackend.java:37-43`
- `engine/src/main/java/net/minegasm/runtime/SceneMixer.java:192-202`
- `docs/bridge/xtoys/main.go:137-164`
- `docs/bridge/xtoys/main.go:214-291`
- `docs/adr/ADR-018-scene-level-central-governance.md:18-31`

ADR-018 says priority and ducking are resolved centrally before fan-out. In practice, `SceneGovernor.govern` performs expiry and fatigue scaling but returns every scene/layer without priority or coupling resolution. Ducking exists only in the Buttplug endpoint mixer. The XToys adapter ignores `priority` and `coupling` and takes a per-role maximum across all live scenes, so a high-priority exclusive warning does not reliably duck lower-priority ambient/texture output.

There are two related inconsistencies:

- Bridge-only sessions never accrue fatigue because semantic backends inherit `isRenderingActive() == false`, even though XToys can drive physical devices.
- If two exclusive candidates collide on a Buttplug endpoint, `SceneMixer.dominant` compares amplitude before priority, so a louder low-priority exclusive layer beats a quieter high-priority exclusive layer.

This produces different authored semantics and different safety behavior depending on backend, and it does not govern concurrent load holistically.

Recommendation: resolve backend-neutral scene conflicts in a real central stage, using explicit logical destinations/body regions rather than physical Buttplug endpoints. Require adapters to declare capabilities and whether they are actively body-driving; conservatively count an active bridge for fatigue until richer feedback exists. Backends should receive the same resolved semantic layer set and only perform device-specific rendering/caps. Add cross-backend conformance tests using identical overlapping exclusive scenes.

### [P1-7] Duplicate bridge names create hidden, unremovable live backends

Locations:

- `engine/src/main/java/net/minegasm/config/HapticConfig.java:461-479`
- `engine/src/main/java/net/minegasm/runtime/HapticRuntime.java:91-98`
- `engine/src/main/java/net/minegasm/runtime/HapticRuntime.java:194-225`
- `modern/src/main/java/net/minegasm/neoforge/MinegasmBridgeEditScreen.java:106-130`
- `classic/common/src/main/java/net/minegasm/classic/BridgeList.java:57-75`

The name is documented and used as a unique runtime identity, but neither config construction nor any editor enforces uniqueness. During initial runtime construction, every duplicate is added to the coordinator while the name-keyed maps overwrite the earlier entry. The earlier backend remains in fan-out but is no longer addressable through the maps. Later reconciliation can remove only the mapped backend, leaving the orphan connected and receiving scenes even after the user removes or disables the bridge.

Default names based on `list.size() + 1` can also collide after a deletion. Names containing spaces are accepted by the UI but cannot be addressed by modern `StringArgumentType.word()` commands or classic whitespace-split commands.

Recommendation: give every endpoint an immutable generated ID and keep display name separate. Validate normalized display-name uniqueness for usability, migrate existing configs, and reject/dedupe duplicate IDs before constructing any backend. Reconciliation should derive coordinator membership from a single authoritative ID map. Add duplicate-load, delete/re-add, rename, and command-name tests.

### [P2-1] `DeliveryMode` is serialized and configurable but has no runtime effect

Locations:

- `engine/src/main/java/net/minegasm/core/DeliveryMode.java`
- `engine/src/main/java/net/minegasm/core/HapticRoute.java:18-42`
- `engine/src/main/java/net/minegasm/core/HapticRoute.java:61-62`
- `engine/src/main/java/net/minegasm/pack/ScenePackCodec.java:114-127`
- `engine/src/main/java/net/minegasm/runtime/SceneMixer.java:74-119`

A production-source search finds no consumer of `HapticRoute.deliveryMode()` outside its getter. `SceneMixer` routes every layer to every compatible enabled feature. Consequently `BEST_PER_DEVICE`, `BEST_GLOBAL`, `SUPPLEMENTAL`, and `EXCLUSIVE` delivery modes behave like `ALL_COMPATIBLE`. A file pack asking for one best destination can unexpectedly drive every device.

Recommendation: either implement destination selection/scoring before endpoint merging and test every enum value, or remove unsupported values from the public pack schema until their semantics are specified. Silent no-op configuration is worse than a smaller honest format.

### [P2-2] Connect/disconnect operations lack cancellation generations and can complete after close

Locations:

- `modern/src/main/java/net/minegasm/buttplug/WebSocketTransport.java:31-40`
- `modern/src/main/java/net/minegasm/buttplug/WebSocketTransport.java:58-67`
- `modern/src/main/java/net/minegasm/buttplug/WebSocketTransport.java:95-110`
- `engine/src/main/java/net/minegasm/bridge/TcpLineBridgeTransport.java:41-67`
- `engine/src/main/java/net/minegasm/bridge/TcpLineBridgeTransport.java:103-130`
- `engine/src/main/java/net/minegasm/buttplug/b4j/Buttplug4jProvider.java:113-131`

The modern WebSocket stores its socket only after the asynchronous handshake. Calling `close` before completion closes nothing; the continuation can then publish a live socket after disconnect. A callback from an older socket can also clear a newer socket because callbacks are not generation-scoped.

The TCP bridge similarly publishes its socket/writer/open state only after a blocking connect. Closing mid-connect cannot close the in-progress socket; completion can briefly report success and leaks the newly created writer executor even when the reader notices `closed`. The buttplug4j async connect can likewise rebuild state after a caller has disconnected.

Recommendation: assign every connect attempt a monotonically increasing generation/cancellation token. A completion must atomically prove it is still current or immediately close its resources. Scope callbacks to their socket/generation, retain a cancellable in-progress socket/future, and make close idempotent. Test delayed handshakes followed by close, rapid reconnect, and stale old-socket callbacks.

### [P2-3] Bridge forwarding records dropped frames as delivered and detects only peak-amplitude changes

Locations:

- `engine/src/main/java/net/minegasm/runtime/GovernedSceneForwarder.java:42-69`
- `engine/src/main/java/net/minegasm/runtime/GovernedSceneForwarder.java:78-83`
- `engine/src/main/java/net/minegasm/bridge/BridgeBackend.java:128-140`

The forwarder updates `continuous`/`discreteSent` after calling a `Consumer` sink, but `BridgeBackend.submit` silently drops when disconnected or output-disabled. A scene observed while disconnected is therefore marked sent. On reconnect, an active discrete scene is never sent and a continuous scene may wait until half its TTL before rearming (up to 30 seconds with the pack duration limit).

Change detection also fingerprints only the maximum primitive level. Changes to role, primitive type, beat timing, shape parameters, layer membership, coupling, or priority with the same peak are suppressed until rearm, so the adapter can play stale semantics for the same interval.

Recommendation: make the sink return an accepted/delivered result, reset forwarding state on link transitions, and only record successful queue admission for the current connection generation. Fingerprint all wire-relevant normalized content while applying epsilon only to intended floating-point attenuation fields. Test connect/reconnect during active discrete and continuous scenes plus equal-amplitude shape/role changes.

### [P2-4] Config updates mutate live state before the fallible save and safety side effects

Locations:

- `engine/src/main/java/net/minegasm/client/MinegasmClient.java:329-340`
- `engine/src/main/java/net/minegasm/backend/ButtplugBackend.java:65-77`

The method documentation says “persist, swap,” but the code swaps the `AtomicReference` before `configStore.save`. If saving throws, callers observe the new in-memory config while lifecycle stopping and bridge reconciliation never run. A failed attempt to disable master output stops new recipe generation and eventually zeros Buttplug, but an existing semantic bridge can retain previously held output until its TTL because the explicit config-reset stop was skipped.

Recommendation: validate and persist to an atomic temporary file first, then publish the runtime snapshot and reconcile. For safety-reducing transitions such as disable, panic/stop existing output before any fallible persistence and preserve that safe state even if the write fails. Return a structured result so UI/commands can report “applied but not saved” versus “not applied.” Add injected-save-failure tests for global disable and bridge removal.

### [P2-5] Input bounding is incomplete and one frame cap accepts a truncated prefix

Locations:

- `engine/src/main/java/net/minegasm/bridge/TcpLineBridgeTransport.java:116-127`
- `engine/src/main/java/net/minegasm/pack/PackLoader.java:46-55`
- `engine/src/main/java/net/minegasm/pack/ScenePackCodec.java:87-111`
- `engine/src/main/java/net/minegasm/pack/ScenePackCodec.java:161-169`
- `modern/src/main/java/net/minegasm/buttplug/WebSocketTransport.java:77-90`

`BufferedReader.readLine()` has no bridge inbound-line limit, so an explicitly allowed remote adapter can grow memory without a newline. Scene packs are loaded with `Files.readAllBytes` and have no byte, trigger, layer, beat, or string-length limits. Numeric durations are bounded, but structural cardinality is not.

The modern WebSocket cap stops appending once a message exceeds 1 MiB but still calls `onMessage` with the accumulated prefix on the final fragment. An oversized frame should be rejected as a whole, not transformed into a different message. The cap is also in UTF-16 characters while the documentation calls it bytes.

Recommendation: enforce byte limits before allocation, bounded line/frame readers, cardinality/string limits during pack parsing, finite-number validation, and fail-closed overflow behavior. Close or reject the offending frame and surface a diagnostic. Add boundary and one-over-limit tests for every input path.

### [P2-6] A newer config schema is silently relabeled as the current schema

Locations:

- `engine/src/main/java/net/minegasm/config/ConfigMigrations.java:17-35`
- `engine/src/main/java/net/minegasm/config/HapticConfig.java:22`

`migrateInPlace` rewrites any version not equal to the current version, including a future version, to version 1. An older build can therefore load a newer config, ignore fields it does not understand, and later save a lossy downgraded file labeled as current.

Recommendation: migrate only known older versions through explicit step functions. Reject future versions without mutation, retain a backup, and present a clear “config created by a newer Minegasm” error. Add tests for current+1 and very large versions proving the source file remains byte-for-byte unchanged.

### [P2-7] File packs can shadow built-ins, and missing selected packs silently become Balanced

Locations:

- `engine/src/main/java/net/minegasm/pack/PackLoader.java:46-55`
- `engine/src/main/java/net/minegasm/pack/PackRegistry.java:18-27`
- `engine/src/main/java/net/minegasm/recipe/RecipeEngine.java:89-100`
- `docs/briefs/0003-shareable-haptics-and-multi-backend.md:106-112`

The file registry has no reserved namespace, and `RecipeEngine.selectPack` looks up a file before choosing the built-in. A file with ID `classic` or `balanced` silently replaces the built-in behavior. Conversely, if a selected custom pack is deleted or fails validation on the next launch, its unknown ID collapses through `RecipePackId` to Balanced without telling the user.

Recommendation: reserve built-in IDs or namespace user packs (for example `user:<id>`), record origin in the registry/UI, and reject collisions. If the selected pack is unavailable, fail closed to disabled recipe output or require an explicit, prominently reported fallback rather than silently changing feel. Add collision and selected-pack-load-failure tests.

### [P2-8] The pack “manager” is an unscrollable startup-only selector across modern and classic UIs

Locations:

- `modern/src/main/java/net/minegasm/neoforge/MinegasmScenePackScreen.java:42-64`
- `classic/1.16.5-common/src/main/java/net/minegasm/classic/ScenePackScreen16.java:28-46`
- `classic/1.12.2-forge/src/main/java/net/minegasm/classic/ClassicScenePackScreen.java:30-49`
- `engine/src/main/java/net/minegasm/client/MinegasmClient.java:170-177`

Every pack becomes a fixed-position button; there is no viewport or scrolling. With enough packs, rows overlap the pinned Done button and then leave the screen, so installed packs become unreachable. Packs are discovered only during client construction, and the screen provides no import, reload, validation detail, origin, metadata view, or unavailable-selection warning.

Recommendation: reuse the existing row-scroller/list-widget pattern, keep only visible widgets mounted, and turn this into an actual manager: import/open-folder, reload, per-file validation errors, metadata/origin, selected/missing state, and confirmation before activating an untrusted pack. Add layout tests at minimum supported resolution and with 0, 1, 20, and 100 packs.

## UX recommendations

The core UX problem is not visual polish; it is that users cannot reliably tell what is active, what is physically reachable, or whether a safety action actually took effect.

### 1. Make global safety state persistent and deliberate

- Show a persistent, high-contrast “OUTPUT STOPPED” banner on every Minegasm screen while panic is latched.
- Do not turn the same adjacent emergency-stop button into a one-click Resume action. Resume should be visually distinct and require a deliberate confirmation/hold, especially after watchdog or backend failure.
- Distinguish master disabled, panic latched, paused, and backend fault. They have different recovery semantics.
- Report which backends acknowledged stop and which were force-disconnected or quarantined.

### 2. Show the whole connection chain, not one optimistic “connected” state

The bridge row currently reports only mod → adapter connectivity. For XToys, users need at least:

`Minegasm → local adapter → XToys webhook → mapped output/device`

Extend the bridge protocol with hello/version, capability declaration, downstream-ready/armed state, acknowledgements, and last-error/last-frame timestamps. Render these as separate states such as Disabled, Connecting, Adapter connected, Downstream unavailable, Ready, and Faulted. Until the protocol can know downstream state, label the current state honestly as “adapter socket connected,” not simply “connected.”

### 3. Redesign bridge identity and editing

- Use immutable internal IDs and editable display names.
- Reject duplicate names inline and either disallow whitespace for command aliases or quote/greedy-parse names everywhere.
- Validate host, port, scheme, remote classification, and transport with a specific inline error instead of only turning the field red.
- When “allow remote” is enabled, show a clear plaintext/no-auth warning; ideally require an authenticated/TLS transport before supporting remote bridges.
- Allow a truly empty bridge list. A forced disabled `local` placeholder makes “no integrations” look like a configured integration.
- Confirm or offer Undo for removal. Add “Save & test” for a new/repointed endpoint.

### 4. Make test output observable and trustworthy

- The GUI enables Buttplug Test when any device exists, not when a currently enabled/routable feature exists; `targetedFeatures` also ignores per-feature disablement and output-kind policy.
- Bridge commands report “Sent” for a known enabled bridge even when its adapter is disconnected, and neither command nor UI knows whether XToys is ready downstream.
- Return a structured test result: accepted targets, skipped targets with reasons, queue/transport acknowledgement, downstream acknowledgement where available, and automatic stop result. Surface it as a toast/status panel.
- Give tests a visible countdown and an always-available Stop button.

### 5. Make editing semantics consistent

Settings uses staged Save/Cancel, while pack selection and several integration actions apply immediately. Navigating from Settings into Packs can therefore persist one change even if the user later presses Cancel on Settings. Pick one model:

- a single draft transaction shared by child screens, committed by Save; or
- immediate application everywhere with explicit feedback and no misleading Cancel.

For safety settings, prefer a draft with validation plus immediate fail-safe application of reductions.

### 6. Make multi-endpoint commands match the UI

`/mg bridge on|off` still operates only on `bridges.get(0)` and reports no endpoint name, while the UI supports many endpoints. Replace it with `bridge list`, `bridge <id-or-name> on|off`, and an explicit `bridge all ...`. Preserve a deprecated first-endpoint alias only if its scope is stated in feedback.

### 7. Reduce cross-version UX drift

Modern and classic screens duplicate substantial layout/action logic. Continue the good pattern already used by `DeviceEditorModel` and `CustomizationModel`: move validation, draft state, action results, and list models into Minecraft-independent presenters, leaving each version with only rendering/input glue. Add presenter tests and small layout invariant tests (no overlap, visible Done/Stop, reachable list items) so safety and UX fixes do not need manual rediscovery in five screen implementations.

## Important missing tests

The existing suite covers many happy paths and basic safety transitions, but the following scenarios should become release gates:

1. Ordinary packs/bridge scenes cannot reach e-stim while unarmed; arming policy cannot be supplied by a pack.
2. A hung renderer cannot block panic/watchdog, and all other backends still stop.
3. buttplug4j panic while its lifecycle executor is blocked.
4. Panic followed by live bridge add/repoint/rename remains stopped.
5. Long isolated test followed by panic/resume never restarts.
6. Backend exception after a non-zero held command causes stop, quarantine, and visible unhealthy state.
7. Identical overlapping scenes have the same priority/ducking/fatigue result on Buttplug and XToys.
8. Duplicate bridge IDs/names cannot create more coordinator entries than configured endpoints.
9. Every `DeliveryMode` has a tested, documented routing result.
10. Close during delayed WebSocket/TCP/buttplug4j connect cannot reopen or leak resources; stale callbacks cannot clear a new connection.
11. Reconnect during live discrete/continuous effects immediately resynchronizes the adapter.
12. Save failure during disable/removal remains fail-stopped and reports a recoverable error.
13. Oversized frames/packs, excessive cardinalities, and non-finite numbers are rejected without partial parsing or excessive allocation.
14. Future config schemas are never mutated by an older build.
15. XToys adapter tests cover priority/ducking, reconnect, expiry, concurrent Minegasm connections, stop, malformed input, and downstream WebSocket failure.
16. Pack and bridge screens remain usable at minimum resolution with large lists.

## What is working well

- The engine/loader boundary keeps the core Java 8-compatible and makes the modern/classic build matrix feasible.
- Immutable value objects, monotonic clock use, generation-stamped device references, and dependency injection around providers are good foundations for deterministic tests.
- The outbound bridge queue is bounded and stop replaces queued effects; effect TTLs provide useful defense in depth.
- Loopback defaults and explicit remote opt-in are sound defaults.
- Centralizing scene holding/coalescing/fatigue is directionally correct even though priority, semantic-backend accounting, and the body budget are incomplete.
- The repository builds across a demanding version matrix, and the engine tests cover recipes, aggregation, mixing, scheduling, config, migration basics, lifecycle, reconnect, and bridge behavior.

## Recommended implementation order

1. **Stop advertising/block the e-stim-via-XToys path.** This is independent of the rest and should happen first.
2. **Rebuild the stop/fault path as out-of-band and fail-stopped.** Fix the worker lock, buttplug4j stop, local test cleanup, panic inheritance, and backend quarantine together.
3. **Define one backend-neutral governance contract.** Resolve priority/ducking centrally, account for body-driving bridges, and either implement or remove `DeliveryMode`.
4. **Fix bridge identity and connection generations.** Stable IDs also simplify status, commands, reconciliation, and UX.
5. **Make config/input handling transactional and bounded.** Then add future-schema and pack-identity protection.
6. **Build the UX on structured state/results.** A richer screen cannot compensate for ambiguous runtime state; expose acknowledgements, health, and validation first.
