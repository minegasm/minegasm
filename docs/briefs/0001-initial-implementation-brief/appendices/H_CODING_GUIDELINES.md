# Appendix H - Coding guidelines

- Java 25 language level, but avoid clever preview features unless clearly beneficial and supported by the build.
- Immutable records/value objects across thread boundaries.
- Constructor injection; no global service locator for engine/provider.
- All time comes from an injected monotonic `Clock` abstraction in testable code.
- No `Thread.sleep` in production scheduling or tests.
- No Minecraft classes in core domain/protocol scheduler packages.
- No Buttplug client-library classes outside adapter packages.
- No unchecked normalization values; clamp at explicit boundaries and assert invariants.
- Every queue is bounded and documents its overflow policy.
- Every externally initiated asynchronous request has timeout/cancellation handling.
- Logging uses structured event IDs and redaction helpers.
- Do not log full protocol payloads at normal levels.
- Use sealed hierarchies for primitives, commands, stop selections, and connection states.
- Prefer table/data-driven recipes and conflicts over event-name switch chains.
- Version-specific code must have a shared contract and tests where possible.
- Comments explain safety/timing reasoning, not obvious syntax.
