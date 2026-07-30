# Local bridge protocol

The local bridge lets Minegasm drive an external adapter you run yourself, without building that
device's protocol into the mod. It is the extension point for XToys, DIY hardware, and, later, a
restricted OpenShock path (see brief 0002 §4.3 and ADR-016).

Minegasm is the client: it connects out to your adapter and never opens a listening port. The
default endpoint is loopback, and a non-loopback endpoint is refused unless you enable "allow
remote", the same rule as a remote Intiface server.

## Transport

The default transport is **TCP with newline-delimited JSON**: one JSON object per line, UTF-8,
terminated by `\n`. It is dependency-free on every loader (Classic included), reliable, and ordered.

A `bridge.transport` config selector chooses the transport. Other transports can be added behind the
same message format (for example a modern-only WebSocket), where only the framing differs; the JSON
below stays the same.

Connection settings live under `bridge` in the config: `enabled` (off by default), `url` (default
`tcp://127.0.0.1:12347`), `transport` (default `tcp`), and `allowRemote` (off).

## Messages

Every message is a single-line JSON object with a protocol version `v` (currently `1`). An adapter
should refuse a version it does not recognize rather than guess.

### effect

Sent when a scene fires. It carries the scene's device-independent content; it deliberately does not
include Buttplug output kinds or device routing.

```json
{
  "v": 1,
  "type": "effect",
  "sceneId": "hurt:HURT",
  "event": "HURT",
  "priority": 100,
  "ttlMs": 300,
  "continuousKey": "mining",
  "layers": [
    {
      "layerId": "hit",
      "role": "IMPACT",
      "coupling": "MAX",
      "priority": 100,
      "startOffsetMs": 0,
      "expiresAfterMs": 300,
      "primitive": { "type": "impulse", "level": 0.8, "durationMs": 250, "attackMs": 10, "releaseMs": 60 }
    }
  ]
}
```

- `event` is the game event kind (`HURT`, `ATTACK`, `MINING_ACTIVE`, `XP_GAIN`, …).
- `ttlMs` is the scene's remaining lifetime. **Honor it:** if you start a continuous output, stop it
  after `ttlMs` even if nothing else arrives, so a dropped connection can never leave output running.
- `continuousKey` is present only for continuous scenes (mining texture, accumulation, stroke). The
  same key updates the same ongoing effect.
- `layers[].primitive.type` is one of `impulse`, `texture`, `rumble`, `sweep`, `beat`, `hold`,
  `oscillation`. The simplest adapter reads each primitive's `level` and pulses; a richer one can use
  the role, timing, and per-primitive shape fields.

### stop

Sent on any stop or panic. Stop all output immediately.

```json
{ "v": 1, "type": "stop" }
```

## Reference adapter

`reference-adapter.py` is a dependency-free Python adapter that prints each message. Run it, enable
the bridge in Minegasm, and start the game:

```
python docs/bridge/reference-adapter.py
```

It is the smallest thing that proves the protocol and a starting point for a real adapter (drive a
motor, forward to another service, blink an LED). Build your own in any language that can accept a
TCP connection and read lines.
