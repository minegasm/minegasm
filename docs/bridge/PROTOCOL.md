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

### output

The authoritative current output, one level per role, in `[0, 1]`. Minegasm sends it whenever the
level changes and re-sends it periodically while output is active; it is device-independent and
carries no Buttplug output kinds or device routing.

```json
{
  "v": 1,
  "type": "output",
  "ttlMs": 6000,
  "roles": {
    "impact": 0.8,
    "reward": 0,
    "texture": 0,
    "warning": 0,
    "ambient": 0,
    "control": 0
  }
}
```

- **Each frame is the full state.** Set every role to the level given and leave the rest of your
  outputs alone only if they are not roles. A role that drops to `0`, or that a frame omits, is off:
  apply it as off. This is what makes a scene ending or being suppressed retract, so you never combine
  a stream of events or track when one ends. There is nothing to "add" or "remove"; you mirror the
  latest snapshot.
- `roles` keys are the six roles Minegasm emits, lowercased: `impact`, `reward`, `texture`, `warning`,
  `ambient`, `control`. Treat a missing key as `0`.
- `ttlMs` is how long to hold these levels without a fresh frame before zeroing everything. **Honor
  it:** the periodic re-send keeps it refreshed, so if it lapses the link is gone and output must
  stop on its own, without depending on a later stop arriving.
- Map each role level to your device however you like (scale, floor for a motor's start threshold).
  Roles let several actuators run at once; route each to whatever output you want in your adapter.

### stop

Sent on any stop or panic. Stop all output immediately.

```json
{ "v": 1, "type": "stop" }
```

## Adapter to Minegasm messages (optional)

An adapter may send messages back on the same connection to report the next link in the chain, so
Minegasm can show more than "the adapter socket is open." These are optional: an adapter that sends
nothing works exactly as before, and Minegasm ignores any message type it does not recognize, so a newer
adapter and an older mod (or the reverse) still interoperate. Do not make them mandatory.

### hello

Sent once when Minegasm connects. It reports the adapter's onward link (`downstream`), which is
`"ready"` when that link is up (for the XToys adapter, the webhook WebSocket is connected) or
`"unavailable"` when it is not.

```json
{ "v": 1, "type": "hello", "downstream": "ready" }
```

### status

Sent whenever the onward link changes (connects or drops), with the same `downstream` field.

```json
{ "v": 1, "type": "status", "downstream": "unavailable" }
```

Minegasm renders this as a distinct step: waiting for the adapter, adapter connected (an adapter that
does not report `downstream`), ready, or adapter up but downstream offline.

## Reference adapter

`reference-adapter.py` is a dependency-free Python adapter that prints each message. Run it, enable
the bridge in Minegasm, and start the game:

```
python docs/bridge/reference-adapter.py
```

It is the smallest thing that proves the protocol and a starting point for a real adapter (drive a
motor, forward to another service, blink an LED). Build your own in any language that can accept a
TCP connection and read lines.
