# ADR-010: Local-only, no-telemetry privacy posture

**Status:** accepted.

**Decision.** No telemetry, accounts, analytics, cloud services, or remote control. Default to a
loopback Buttplug URL; non-loopback requires explicit opt-in (`MinegasmClient.connect`). Redact server
URL credentials/query in logs and do not require device names in logs. Config may contain intimate
device information and is stored locally only, never uploaded. No inbound listening server. Diagnostic
export offers a redact-device-identity option. Device state is never exposed to multiplayer servers.

**Consequences.** The mod is safe to use on shared/streamed setups; nothing intimate leaves the machine
without a deliberate user action.
