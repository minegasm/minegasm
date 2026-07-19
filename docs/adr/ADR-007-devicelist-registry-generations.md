# ADR-007: Full DeviceList registry generations

**Status:** accepted.

**Context.** Buttplug sends complete `DeviceList` snapshots; device indexes are valid only while the
device remains in the latest snapshot and may be reused.

**Decision.** Replace the registry on every accepted list and increment a generation counter
(`DeviceRegistry`). Scheduled commands capture the generation (`FeatureRef`) and are dropped if it no
longer matches (`DeviceRegistrySnapshot.resolve`, `FeatureScheduler`, `ButtplugProvider.send`). On
removal/reconnect, queued commands for the old generation are cancelled and endpoints cleared. Saved
preferences use a best-effort identity key (name + feature signature), never a raw index.

**Consequences.** A reused index cannot receive a stale command or old calibration. Identity matching
is probabilistic and surfaced to users.
