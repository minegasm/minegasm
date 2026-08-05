# ADR-019: Native Buttplug provider on classic via a bundled WebSocket library

**Status:** accepted.

**Context.** The `native` Buttplug provider is the alternative to `buttplug4j`: instead of the
buttplug4j client (which pulls in Jetty and Jackson), it speaks the Buttplug protocol over a plain
WebSocket. On the modern loaders that WebSocket is the JDK's `java.net.http.WebSocket`, so `native`
there is genuinely dependency-free. That API arrived in Java 11, and the classic loaders target Java 8
(Minecraft 1.7.10 through 1.16.5), so classic has shipped only `buttplug4j` and its dashboard has no
adapter toggle. Offering `native` on classic closes that gap: a lighter runtime footprint once it is
selected, and the same adapter control the loaders otherwise share.

Java 8 has no built-in WebSocket client, so classic needs one from somewhere: a hand-rolled RFC 6455
client, or a small third-party library bundled into the jar. We chose the library.

The device transport is safety-critical. `SAFETY.md` states inbound Buttplug frame size is capped and
malformed frames are rejected, and the modern transport enforces a hard 1 MiB cap while reassembling.
Any classic transport has to hold the same line, which turns out to be the deciding constraint between
libraries.

**Decision.**

1. Ship `native` on classic, backed by **Java-WebSocket** (`org.java-websocket:Java-WebSocket`),
   relocated and shaded into each classic jar the same way buttplug4j and Gson already are. A
   `ClassicWebSocketTransport` in `classic/common` implements the engine's `ButtplugTransport`, so the
   existing `ButtplugProvider` drives it unchanged; a shared `ClassicProviderFactory` selects `native`
   or `buttplug4j` from `buttplug.client`, mirroring the modern `ProviderFactory`. The classic
   dashboard gains the same adapter toggle the modern dashboard has. Default was `buttplug4j` at the time; it has since been flipped so `native` is the default (see the
CHANGELOG and ADR-006's update note).

2. Java-WebSocket over the lighter `nv-websocket-client`, on the frame cap. `nv-websocket-client`'s
   `setMaxPayloadSize` only splits outgoing frames; its read path reads a frame at the length the
   header declares, with no incoming limit, so a single oversized frame allocates unbounded before any
   application callback runs. Java-WebSocket checks the declared length against a configurable
   `maxFrameSize` before allocating (`Draft_6455(extensions, maxFrameSize)`,
   `translateSingleFrameCheckLengthLimit`, `LimitExceededException`), the same fail-closed posture the
   modern path keeps. Its bundled server also lets the transport be tested against a loopback WebSocket,
   the way the local bridge is tested against a loopback socket.

**Consequences.**

- `buttplug.client=native` now means two different implementations by loader: the dependency-free JDK
  WebSocket on modern, a bundled library on classic. The config value stays portable and the behaviour
  is the same; only the footprint differs. This note is the record of that overload.
- Classic jars carry one more relocated dependency. It replaces nothing (buttplug4j is still the
  default and still bundled), so the jar grows; `native` is the lighter runtime only once selected,
  since buttplug4j's Jetty/Jackson stack is then unused.
- The frame cap is configured on the library (`maxFrameSize`) rather than enforced by hand. A loopback
  test in the `1.12.2-forge` test source set exercises connect, send, receive, and oversize-frame
  rejection, confirming the cap fires on the decode path rather than only that the setter exists.
  Physical hardware is still untested.
