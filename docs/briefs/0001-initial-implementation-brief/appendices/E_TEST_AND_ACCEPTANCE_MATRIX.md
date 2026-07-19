# Appendix E - Test and acceptance matrix

## Core timing/scheduler

| Case | Expected result |
|---|---|
| Hurt command expires before dispatch | Dropped; never sent late |
| Mining updates exceed device timing | Latest update retained; no unbounded queue |
| Explosion arrives behind mining commands | Explosion dispatched first; mining ducked/dropped |
| Stop arrives with pending commands | Local state cleared; StopCmd immediate; no reassertion |
| DeviceList removes device | All commands/subscriptions for old generation canceled |
| Device index reused | Treated as new device; no old calibration command sent |
| Pattern phase runs during client lag | Monotonic deadlines used; missed obsolete phases skipped |
| Queue overflow | Lowest-priority/expired dropped; control preserved |

## Event acquisition

| Event | Single-player | Multiplayer without mod | Duplicate prevention | Pause/reset |
|---|---|---|---|---|
| Attack | required | required | target/tick | clear |
| Hurt | required | required | merge window | baseline reset |
| Mining | required | required | continuous key | stop |
| Block break | required | required | position/tick | clear target |
| Place | required | required | position/tick | clear |
| Harvest | required | required | action/position | clear |
| Fishing | required | required | refractory | clear hook |
| XP | required | required | coalesce | baseline reset |
| Advancement | required | required | advancement ID | keep earned set/session |
| Vitality | required | required | edge/repeat | reset predicate |

## Buttplug fixtures

- Empty device list.
- One device/one vibration feature `[0, 20]`.
- One device/two vibration features.
- Two devices with different timing gaps.
- Stroker feature with both Position and HwPositionWithDuration.
- Signed Rotate range.
- Unknown output type.
- Malformed range.
- Device removal and index reuse.
- Error response to OutputCmd.
- Disconnect before Ok.
- MaxPingTime enforcement.
- Out-of-order responses.

## Release acceptance scenarios

1. New user starts with haptics disabled, connects locally, scans, selects device, tests light pulse, enables Balanced mode.
2. Existing user imports old config and receives equivalent Classic mode behavior.
3. Two vibration devices receive a hurt impact simultaneously without selecting only the first device.
4. Mining for 30 seconds remains subtle and does not create backlog; explosion immediately interrupts it.
5. Pause during continuous output causes immediate stop and output does not resume until an active state is re-observed after resume.
6. Intiface is killed while output is active; local engine clears state and UI reports disconnected.
7. Device reconnects with reused index; no stale command or calibration is applied before registry match/enablement.
8. Position device cannot receive gameplay motion before calibration and explicit opt-in.
9. Panic stop works during event flood.
10. Both 26.2 and 26.1.2 artifacts launch and perform the same core behavior.
