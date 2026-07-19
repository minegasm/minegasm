# Appendix F - Risks and owner decisions

## Immediate owner decisions

- Product name/mod ID.
- Project license.
- Whether the first public build uses Classic or Balanced for new users.
- Whether position support ships in first beta.
- Whether remote/non-loopback Buttplug servers are allowed.
- Distribution/repository mirrors and release automation.

## Technical validation questions

- Which maintained Java client, if any, fully supports Buttplug spec v4?
- Which NeoForge client events remain reliable on unmodified multiplayer servers in 26.1/26.2?
- What exact behavior does current upstream Minegasm label as harvest?
- How does upstream Accumulation decay/hold across events and lifecycle?
- Can a stable device preference identity be obtained through the chosen Buttplug client/Intiface metadata?
- Does Stonecraft’s current template require any special Java 25 or NeoForge 26.2 adjustments?
- Which UI/config library is appropriate and maintained for both variants?

## Scope-control rule

Any new idea must answer:

1. Is it required for Minegasm parity?
2. Is it required for stuck-output safety?
3. Does it unblock vibration MVP?
4. Can it be represented by existing internal interfaces and deferred?

If the answers are no/no/no/yes, defer it.
