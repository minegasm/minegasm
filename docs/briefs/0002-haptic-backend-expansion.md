---
title: "Minegasm Haptic Backend Expansion Brief"
subtitle: "Spatial wearables, integration bridges, and safe multi-backend output"
author: "Minegasm project planning"
date: "28 July 2026"
status: "Proposed"
lang: en-US
---

# Document status

**Audience:** Minegasm maintainers and contributors responsible for the haptic engine, device
integrations, configuration UI, testing, and release engineering.

**Status:** proposed implementation brief. It extends the initial Buttplug-focused architecture; it
does not supersede the existing safety, scheduling, or client-only design.

**Primary recommendation:** retain Buttplug v4 through Intiface as Minegasm's default broad device
layer, add bHaptics as the first native spatial backend, and add a versioned local bridge as the
extension point for XToys, DIY hardware, and future integrations.

# Executive summary

Minegasm currently routes semantic haptic scenes to devices exposed through Buttplug v4. Buttplug
should remain the preferred route for hardware it already supports, including gamepad rumble.
Minegasm should not duplicate those device integrations with vendor-specific backends unless the
vendor exposes capabilities that Buttplug cannot represent adequately.

The next phase should expand Minegasm by capability rather than by raw device count:

1. Refactor the current provider boundary into a genuinely backend-neutral contract.
2. Add bHaptics for spatial, body-region-aware feedback.
3. Add a versioned local WebSocket or OSC bridge for community adapters and DIY devices.
4. Add XToys as a bridge integration for its scripting and remote-session ecosystem.
5. Explore event-driven audio haptics for transducers, bass shakers, and Woojer-style hardware.
6. Evaluate Razer Sensa/Interhaptics and Skinetic after the first two new backends are stable.
7. If OpenShock is integrated officially, expose only vibration, sound, and stop. Electrical shock
   is outside the official Minegasm output model.

The engine must preserve device-independent scene meaning until backend rendering. Reducing every
effect to a single scalar before dispatch would make bHaptics little more than a vest-wide vibrator
and would prevent future spatial or authored-pattern backends from using their distinguishing
capabilities.

# 1. Product goal

Turn Minegasm from a Buttplug application into a general Minecraft haptics engine while preserving
the reliable, local-first experience of the existing implementation.

The expanded system should:

- continue to work with all existing Buttplug v4 devices and gamepads;
- support multiple enabled backends at the same time;
- let each backend render the same semantic scene according to its capabilities;
- add meaningful spatial feedback rather than merely duplicating one intensity across devices;
- provide a controlled extension point without embedding every vendor protocol in the mod;
- remain fail-stopped when a backend disconnects, stalls, or reports invalid capabilities;
- clearly distinguish ordinary vibrotactile output from higher-risk electrical stimulation.

# 2. Scope and priorities

| Priority | Integration | Delivery model | Decision |
|---|---|---|---|
| P0 | Backend-neutral engine boundary | Internal refactor | Required before adding a second backend |
| P1 | bHaptics | Native adapter or supported companion | First new hardware ecosystem |
| P1 | Local bridge | Outbound loopback WebSocket and/or OSC | Primary community extension point |
| P2 | XToys | Webhook/bridge adapter plus published XToys script | Support remote sessions and scripting |
| P2 | Audio haptics | Selectable dedicated audio output | Prototype for transducers and audio wearables |
| P3 | Razer Sensa/Interhaptics | Vendor SDK feasibility spike | Candidate mainstream gaming backend |
| P3 | Skinetic | Vendor SDK feasibility spike | Candidate spatial vest backend |
| Limited | OpenShock | Local bridge or restricted adapter | Vibration, sound, and stop only |

## 2.1 Explicitly not a separate backend

**Gamepad rumble remains under Buttplug/Intiface.** Minegasm already benefits from Buttplug's
gamepad support, including the tested Joy-Con path. A separate controller implementation would
duplicate discovery, native-library, platform, and device-maintenance work without adding a new
haptic capability.

The same rule applies to intimate devices already represented adequately by Buttplug. Direct
Lovense, Kiiroo, Handy, or similar vendor integrations should not be added merely to increase a
marketing device count.

## 2.2 Explicit non-goals

- Replacing Buttplug or embedding a second broad intimate-device protocol stack.
- Reimplementing devices that Intiface already exposes reliably.
- Treating every backend as a discoverable collection of scalar actuators.
- Loading arbitrary executable third-party code into the Minecraft process in the first release.
- Sending account credentials, device identities, or gameplay telemetry to a Minegasm service.
- Adding electrical shock, EMS, heat, spray, or other higher-risk output to the generic effect model.
- Guaranteeing Linux support for vendor SDKs that do not officially support Linux.
- Requiring an integration backend for users who only want Buttplug.

# 3. Current architecture and required change

The existing runtime already has the right high-level pipeline:

```text
Minecraft observation
  -> semantic intent
  -> haptic scene and layers
  -> mixer, fatigue, routing, and safety
  -> feature scheduler
  -> Buttplug provider
```

However, the current `HapticProvider` lifecycle, device registry, `OutputKind`, and `OutputCommand`
model are closely shaped around Buttplug device features. The scene mixer also chooses a
Buttplug-style output kind before dispatch.

The expanded pipeline should be:

```text
Minecraft observation
  -> semantic intent
  -> haptic scene and layers
  -> mixer, fatigue, routing, and safety
  -> backend-neutral rendered effect
       -> Buttplug renderer -> feature scheduler -> Intiface
       -> bHaptics renderer -> spatial pattern scheduler -> bHaptics Player
       -> bridge renderer -> versioned event/effect message
       -> audio renderer -> bounded PCM envelope
```

## 3.1 Backend contract

Introduce a backend-neutral contract such as `HapticBackend`. Exact names may change, but the
contract must cover:

- lifecycle: initialize, connect, disconnect, close;
- immutable status snapshots and status listeners;
- capability reporting without requiring hardware discovery;
- asynchronous, non-blocking effect submission;
- universal stop and backend-scoped stop;
- bounded queues and command expiry;
- backend health for the independent watchdog;
- optional discovery and device-list functionality.

Discovery must be an optional capability. Buttplug exposes scanning and device lists, while XToys
webhooks and simple OSC endpoints do not. Do not force every integration to fake a scan workflow.

The runtime should support fan-out through a coordinator that owns all enabled backends. A failure
in one backend must not block output or stopping on another backend.

## 3.2 Backend-neutral rendered effects

Preserve semantic information long enough to render effects such as:

- **Scalar effect:** normalized level and bounded duration for vibration-like devices.
- **Spatial effect:** body region or normalized coordinates, direction, spread, and intensity.
- **Pattern effect:** named authored pattern plus bounded parameters and duration.
- **Audio effect:** amplitude envelope, frequency band, stereo/spatial placement, and duration.
- **Bridge event:** stable Minegasm event identifier plus sanitized effect parameters.

These are internal concepts, not a public wire protocol. Implement them with Java constructs that
remain compatible with the project's shared-engine and Classic-runtime requirements.

Backend-specific SDK classes, wire messages, credentials, and device identifiers must remain inside
their adapter packages.

## 3.3 Routing

Routing should be expressible at three levels:

1. backend enabled or disabled;
2. device or body region enabled, where the backend exposes them;
3. event/layer routing and per-target intensity.

The default should fan a scene out to every enabled compatible backend. Users must be able to route,
for example, combat to bHaptics and Buttplug while routing mining texture only to a vest or audio
transducer.

# 4. Integration requirements

## 4.1 Buttplug

Buttplug remains the production baseline and reference backend.

Requirements:

- preserve Buttplug v4 behavior and all existing tests;
- preserve gamepad rumble through Intiface;
- retain complete `DeviceList` generation handling;
- retain feature-level timing, expiry, calibration, and stop behavior;
- keep loopback as the default server policy;
- introduce no configuration migration for users who enable no additional backend.

The refactor is successful only if a Buttplug-only user observes no behavioral regression.

## 4.2 bHaptics

bHaptics is the first new native capability because it supplies spatial body feedback rather than
more scalar devices.

Initial mappings should be deliberately small and recognizable:

| Minecraft meaning | Suggested bHaptics rendering |
|---|---|
| Player hurt | Short localized impact; use attack direction when reliable |
| Explosion | Wider front/back or full-torso wave based on direction and distance |
| Projectile impact | Localized directional point |
| Mining texture | Subtle hand/arm or low-intensity torso texture |
| Block break | Short completion pulse |
| Low health | Bounded heartbeat pattern |
| XP/advancement | Upward or outward reward pattern |
| Environmental warning | Region-appropriate bounded pattern |

Implementation constraints:

- do not render every event across all vest motors;
- prefer authored events for recognizable effects and direct dot/path output where dynamic spatial
  placement adds value;
- cache or preload patterns outside the Minecraft client thread;
- give every effect a finite duration;
- call the backend stop path on all existing lifecycle and panic conditions;
- display a clear unavailable status on unsupported operating systems;
- keep bHaptics credentials out of logs and diagnostic exports.

The official SDK documentation currently lists Unity, Unreal, Python, and JavaScript rather than a
desktop Java SDK. Begin with a feasibility spike that selects one supportable integration route:

1. an officially documented local protocol usable directly from Java;
2. a small signed companion built on an official bHaptics SDK; or
3. a vendor-supported native binding.

Do not ship an undocumented reverse-engineered protocol as production support without explicitly
accepting its compatibility and maintenance risk.

## 4.3 Local bridge

The local bridge is the preferred extension mechanism for integrations that do not justify code in
Minegasm core.

Recommended first design:

- Minegasm acts as an outbound client rather than opening a public listener;
- default endpoint is loopback only;
- remote endpoints require an explicit opt-in similar to remote Intiface;
- protocol messages carry a schema version;
- messages contain stable event/effect identifiers, monotonic-relative duration, intensity, and
  optional spatial metadata;
- every continuous effect has an expiry or time-to-live;
- `stop-all` is a first-class message;
- WebSocket mode supports acknowledgement and health status;
- OSC mode is a best-effort compatibility option and is never treated as acknowledged delivery;
- queues are bounded and stale messages are dropped;
- gameplay details not required for rendering are not transmitted.

Publish a small reference receiver and protocol examples. The reference implementation should make
it straightforward to build adapters in JavaScript, Python, Rust, or on an ESP32 without loading
their code into Minecraft.

## 4.4 XToys

XToys should be supported for what it uniquely adds: user-authored scripts, online sessions,
mobile-connected devices, and remote control workflows. Its raw device coverage is not by itself a
reason for integration because much of that hardware overlaps Buttplug.

Recommended implementation:

- send stable Minegasm actions and bounded parameters to an XToys webhook;
- publish an official Minegasm XToys script that maps those actions to user-selected toy blocks;
- prefer WebSocket over repeated HTTP requests where the user's setup permits it;
- expose connection state separately from local hardware discovery;
- require explicit remote/network opt-in;
- treat webhook IDs and authorization tokens as secrets;
- never include them in logs, crash reports, screenshots, or exported configs;
- send finite-duration actions so loss of the cloud connection cannot leave a held output relying
  solely on a later stop message.

XToys is a bridge integration, not a `DeviceList` provider. Minegasm should not claim knowledge of
the final devices or users connected inside an XToys session unless XToys explicitly reports that
information through a supported interface.

## 4.5 Audio haptics

Prototype a dedicated, user-selected audio output for event-driven low-frequency haptics. This may
cover:

- Woojer-style audio haptic wearables;
- bass shakers and tactile transducers;
- haptic chairs, cushions, beds, and accessibility hardware;
- haptic headphones that respond to an audio channel.

The prototype should generate Minegasm-authored low-frequency envelopes, not mirror all game audio.
It must use a dedicated selected output where possible, apply a conservative limiter, stop
immediately with Minegasm's universal stop, and avoid taking ownership of the user's main Minecraft
audio device.

Ship only if device selection and stop behavior are reliable across supported platforms.

## 4.6 Razer Sensa/Interhaptics and Skinetic

Treat both as post-bHaptics feasibility candidates.

Evaluate:

- active SDK availability and licensing;
- supported desktop operating systems and CPU architectures;
- whether Java can call the SDK without an unsafe dependency burden;
- redistributable/runtime requirements;
- spatial and waveform capabilities not already covered;
- device discovery and stop guarantees;
- availability of real hardware for automated and manual release testing;
- vendor commitment to backwards compatibility.

Do not announce production support based only on an SDK demo or simulator.

## 4.7 OpenShock

The official Minegasm integration may expose only:

- vibration;
- sound/beep;
- stop.

It must not expose electrical shock as a generic output kind, hidden advanced toggle, configuration
string, webhook action, or automatic fallback. Unknown or newly added OpenShock action types must
fail closed.

Preferred delivery is a restricted preset through the local bridge or OpenShock Desktop, with
Minegasm emitting only explicitly named vibration/sound parameters.

Electrical shock is a distinct risk class. Supporting it would require a separate product and
threat model, explicit per-session arming, independent hard limits, cooldowns, physical-device
confirmation, consent controls, medical-risk communication, and a safety review. Those requirements
are outside this brief.

# 5. Safety, privacy, and security

All new backends inherit the existing Minegasm safety principles:

- master output defaults off;
- uncertainty resolves to stopped output;
- pause, world unload, disconnect, shutdown, reset, panic, watchdog, and transport failure stop
  output;
- no device or network I/O runs on the Minecraft client thread;
- bounded queues prevent memory growth and stale output;
- command expiry uses monotonic time;
- output resumes only after explicit valid backend state;
- every backend has conservative defaults and user-visible caps;
- remote networking is disabled by default;
- no telemetry or Minegasm account is introduced.

Additional multi-backend rules:

- universal stop is broadcast concurrently to every enabled backend;
- a backend that cannot confirm a stop is shown as unhealthy;
- named vendor effects must have known bounded duration;
- cloud bridges must not depend on a future stop message to end an ordinary effect;
- backend credentials use secret-aware configuration fields and redacted diagnostics;
- the UI distinguishes local, LAN, and cloud-backed integrations;
- high-risk output types are excluded rather than represented as a normal intensity scale.

# 6. Configuration and user experience

Add a **Haptic backends** screen above backend-specific settings.

Each backend row should show:

- enabled state;
- connection state;
- local, LAN, or cloud classification;
- setup requirement or companion application;
- connected device/target summary when available;
- test action;
- stop action;
- link to setup and safety documentation.

Backend-specific configuration should be isolated:

- **Buttplug:** existing server, scan, device, feature, and calibration controls.
- **bHaptics:** Player status, application registration status, device/body-region routing, and
  spatial test patterns.
- **Local bridge:** endpoint, protocol, connection health, schema version, and test event.
- **XToys:** webhook details, network warning, connection test, and script setup link.
- **Audio:** output device, master level, limiter, channel test, and mute.
- **OpenShock:** vibration/sound targets only, with an explicit statement that shock is unsupported.

Configuration migration must preserve existing Buttplug settings exactly. All new backends default
disabled until their setup is completed.

# 7. Implementation phases

## Phase 0: architecture and compatibility

- Introduce the backend-neutral effect and lifecycle contracts.
- Move Buttplug-specific types behind the Buttplug adapter boundary.
- Add a multi-backend coordinator and composite status model.
- Preserve current Buttplug configuration and behavior.
- Add fake backends for deterministic tests.
- Document the new boundary with an ADR.

**Exit condition:** all existing Buttplug tests pass, a fake second backend can receive the same
scene concurrently, and universal stop reaches both without blocking.

## Phase 1: bHaptics feasibility and vertical slice

- Confirm a vendor-supported Java-compatible integration route.
- Connect, enumerate supported body targets, play a bounded test effect, and stop.
- Implement hurt, explosion, and low-health mappings.
- Add platform detection and clear unavailable states.
- Test with real hardware.

**Exit condition:** directional effects are observably spatial, all lifecycle stops work, and an
absent or crashed bHaptics Player does not affect Buttplug or Minecraft.

## Phase 2: local bridge

- Specify the versioned message schema.
- Implement outbound loopback WebSocket mode and first-class stop.
- Add optional OSC compatibility mode if justified by target integrations.
- Publish a reference receiver and protocol examples.
- Add fuzz, malformed-message, expiry, disconnect, and queue-bound tests.

**Exit condition:** a reference adapter can receive events, render bounded effects, reconnect, and
reliably handle stop without Minegasm opening a non-loopback listener.

## Phase 3: XToys

- Create the webhook adapter.
- Publish and version an official XToys script.
- Add credential redaction and explicit cloud opt-in.
- Test reconnect, latency, dropped messages, and bounded-duration behavior.

**Exit condition:** a user can follow documented steps to route Minegasm events through XToys
without Minegasm claiming direct ownership of XToys-connected devices.

## Phase 4: audio prototype and ecosystem evaluation

- Prototype dedicated low-frequency audio effects.
- Test representative transducer or wearable hardware.
- Run Razer Sensa/Interhaptics and Skinetic SDK feasibility spikes.
- Promote integrations only when maintainability and real-hardware testing are credible.

# 8. Test and acceptance matrix

Every production backend must pass:

| Area | Required behavior |
|---|---|
| Disabled default | Fresh configuration sends no output |
| Client thread | No network, SDK, device, or blocking work on the Minecraft thread |
| Universal stop | Panic and every lifecycle stop reaches the backend immediately |
| Watchdog | A stalled worker or backend transitions to stopped/unhealthy |
| Expiry | Stale effects are never emitted after their deadline |
| Disconnect | Other backends continue; the failed backend cannot reassert old state |
| Reconnect | Output resumes only against a valid new backend/device generation |
| Queue bounds | Floods remain bounded and prioritize safety/stop commands |
| Caps | Global, backend, target, and event caps apply after all user scaling |
| Logging | Credentials and sensitive device names are redacted |
| Unsupported platform | Clear UI state; no crash or missing-class failure |
| No hardware | Simulator/fake coverage plus explicit real-hardware release status |
| Configuration | Existing Buttplug-only config migrates with no behavior change |

Additional bHaptics acceptance:

- at least three effects demonstrate meaningful spatial differentiation;
- left/right or front/back mappings are tested where the game observation is reliable;
- missing authored patterns fail silently and stop safely;
- Player restart and device removal do not leave active effects.

Additional bridge/XToys acceptance:

- continuous output always has an expiry;
- malformed or incompatible schema messages cannot produce output;
- remote endpoints require explicit opt-in;
- token-bearing configuration and logs pass secret-scanning tests.

# 9. Documentation and release policy

The supported-devices documentation should distinguish:

- **Protocol:** Buttplug v4 through Intiface;
- **Native spatial ecosystem:** bHaptics;
- **Bridge integration:** XToys, OSC, or local WebSocket;
- **Audio-compatible hardware:** tested transducers and wearables;
- **Tested device:** verified by maintainers on real hardware;
- **Community reported:** reported working but not release-tested.

Do not convert an SDK integration into a claim that every device in a vendor catalog is tested.
Publish backend platform limitations, required companion software, network behavior, and whether a
connection is local or cloud-mediated.

OpenShock documentation must state plainly that Minegasm supports vibration and sound only and does
not support electrical shock.

# 10. Risks and open questions

| Risk or question | Required resolution |
|---|---|
| No official desktop Java bHaptics SDK is listed | Complete vendor-supported integration spike before committing to architecture |
| Shared engine targets older Java through Minegasm Classic | Keep backend-neutral domain types compatible; isolate modern/native adapters |
| Vendor SDK native libraries complicate multi-loader packaging | Prototype packaging and unsupported-platform behavior before implementation |
| Multiple backends may duplicate overwhelming feedback | Add backend/event routing and conservative defaults |
| XToys introduces cloud latency and credentials | Explicit opt-in, redaction, finite effects, and honest status semantics |
| OSC has no delivery acknowledgement | Treat as best effort; never depend on it for clearing held output |
| Audio devices vary greatly in power and response | Conservative limiter, calibration, tested-device list, dedicated output |
| Spatial mappings need reliable game direction data | Fall back to neutral/non-directional effects rather than inventing direction |
| Vendor ecosystems may change or disappear | Keep adapters isolated and optional; never make startup depend on them |
| OpenShock can perform dangerous electrical output | Never model or emit shock in the official integration |

# 11. Decisions not to reopen without an ADR

- Buttplug remains the default broad device and gamepad layer.
- Existing Buttplug-supported hardware is not duplicated with direct vendor APIs without a
  capability or reliability justification.
- bHaptics is the first new native spatial backend.
- The engine preserves semantic/spatial information until backend rendering.
- Discovery is optional in the backend contract.
- The local bridge is outbound and loopback-only by default.
- XToys is modeled as a bridge, not as a Minegasm device registry.
- New backends default disabled.
- Electrical shock is not an official Minegasm output.

# 12. Reference sources

- Buttplug v4 protocol specification:
  <https://buttplug.io/docs/spec/>
- Buttplug architecture and feature model:
  <https://buttplug.io/docs/spec/architecture/>
- bHaptics developer documentation:
  <https://docs.bhaptics.com/>
- bHaptics Python SDK guide, including supported platforms and spatial dot/path examples:
  <https://docs.bhaptics.com/sdk/python/guide>
- XToys webhook documentation:
  <https://guide.xtoys.app/tools/webhook.html>
- XToys tools and game-haptics overview:
  <https://guide.xtoys.app/getting-started/using-tools.html>
- OpenShock safety rules:
  <https://wiki.openshock.org/home/safety-rules>
- OpenShock Desktop/ShockOSC setup:
  <https://wiki.openshock.org/guides/shockosc/basic>
- Razer Sensa HD Haptics:
  <https://www.razer.com/technology/razer-sensa>
- Skinetic/Unitouch SDK documentation:
  <https://unitouch.actronika.com/>
