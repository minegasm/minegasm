# Appendix A - Minegasm replacement feature-parity matrix

This matrix should be converted into tracked issues and acceptance tests after pinning the upstream Minegasm commit.

| Capability | Existing target | Replacement requirement | Acceptance evidence |
|---|---|---|---|
| Connect to Intiface/Buttplug server | Configurable server URL, connect at startup | Explicit connect/disconnect; optional auto-connect; v4 negotiation; status UI | Fake-server test and manual Intiface test |
| Device scanning | Starts scan, waits, selects available device | Start/stop scan; async; live full DeviceList snapshots | Scan does not block client; devices update live |
| Multiple devices | Historically first compatible vibrator | All enabled compatible features across all devices | Two-device manual test and fake registry test |
| Multiple features | Limited/scalar API | Feature-level registry, ranges, independent output | Multi-feature fixture |
| Attack | Timed intensity state | Attack intent, Classic recipe, Balanced snap | Attack once per action, no held-input spam |
| Hurt | Timed intensity state | Health-delta detection, merge window, expiry | Multiplayer client-only test |
| Mine/block break | Break event, ore boost | Classic break behavior plus optional active mining texture | Ore/non-ore parity and completion test |
| Place | Timed state | Confirmed placement event and soft knock | Failed use produces no output |
| Harvest | Short state, interacts with mine | Pinned compatibility semantics; no double full-strength output | Crop scenarios documented |
| Fishing bite | Bobber motion heuristic | Reliable client bite detector plus fallback heuristic | One pulse per bite |
| XP change | Amount/level-aware | Coalesced XP and level-up accent; Classic formula option | Orb burst and level-up tests |
| Advancement | Frame-dependent duration | Task/goal/challenge patterns; Classic duration option | Client advancement test |
| Vitality | Full or critical status by mode | Derived predicate, edge/repeat policy | Full-health and critical-health tests |
| Normal mode | Event intensity map | Data-driven preset | Snapshot test of values |
| Masochist mode | Damage-focused map | Data-driven preset | Snapshot test of values |
| Hedonist mode | Broad map | Data-driven preset | Snapshot test of values |
| Accumulation mode | Event contributions accumulate | Real-time bounded accumulator with compatibility parameters | Fake-clock decay/contribution tests |
| Custom mode | User event intensities | Per-event enablement/multiplier/routing | Config/UI test |
| Pause behavior | Stops on inferred pause | Direct pause/world lifecycle stop and StopCmd | Active-output pause test |
| Shutdown | Stops/disconnects | Local clear, StopAll, graceful Disconnect | Integration test |
| Multiplayer | Current source supports client use | No server mod required for all core events | Test on unmodified server |
| Config migration | Existing TOML/config | Read-only import preview and atomic new config | Migration fixtures |
| Vibration | Scalar | All enabled Vibrate features, patterns and zero transitions | Exact command fixture |
| Position device | Not broadly modeled | Experimental calibrated Position/HwPosition renderer | Hardware/manual + bounds tests |
| Diagnostics | Limited | Connection, registry, queue, errors, redacted export | UI/manual review |
| Panic stop | Not prominent | Dedicated key/UI action, always highest priority | Stress test |

## Parity sign-off process

1. Pin upstream commit hash and archive relevant public docs.
2. Run the old mod on a supported legacy environment where practical.
3. Record exact event conditions, values, duration, overlap behavior, and mode defaults.
4. Decide whether each quirk is reproduced in `Minegasm Classic` or intentionally documented as corrected.
5. Add an automated test or manual test case for every row.
6. Mark parity complete only when migration users can achieve equivalent behavior without editing raw config.
