#!/usr/bin/env python3
"""Reference adapter for the Minegasm local bridge.

The bridge connects out to this process over plain TCP and sends one JSON object per line: an
`output` message with the complete current logical destination set, and a `stop` message on any stop or panic. This
adapter just prints them, it is the smallest thing that proves the protocol and a starting point for
a real adapter (drive a motor, forward to another API, blink an LED).

No dependencies; standard library only. Run it, then enable the bridge in Minegasm's config
(transport `tcp`, endpoint `tcp://127.0.0.1:12347`) and start the game.

Message shapes (see docs/bridge/PROTOCOL.md):
  {"v":1,"type":"output","generation":42,"ttlMs":6000,
   "destinations":[{"role":"impact","region":"genital","outputClass":"strength","level":0.8}]}
  {"v":1,"type":"stop"}

Each output frame is the full state. Treat a missing destination as off. The ttlMs is how long to hold
the levels without a fresh frame before zeroing,
so a dropped connection can never leave something running.
"""

import argparse
import json
import socketserver

PROTOCOL_VERSION = 1


class Handler(socketserver.StreamRequestHandler):
    def handle(self):
        peer = self.client_address
        print(f"[bridge] connected: {peer[0]}:{peer[1]}")
        try:
            for raw in self.rfile:
                line = raw.decode("utf-8", "replace").strip()
                if not line:
                    continue
                self.on_line(line)
        finally:
            print(f"[bridge] disconnected: {peer[0]}:{peer[1]}")

    def on_line(self, line):
        try:
            msg = json.loads(line)
        except json.JSONDecodeError:
            print(f"[bridge] ignoring non-JSON line: {line!r}")
            return
        if msg.get("v") != PROTOCOL_VERSION:
            print(f"[bridge] unsupported protocol version {msg.get('v')}, ignoring")
            return
        kind = msg.get("type")
        if kind == "output":
            destinations = msg.get("destinations", [])
            active = [d for d in destinations if d.get("level")]
            print(f"[output] generation={msg.get('generation')} ttl={msg.get('ttlMs')}ms "
                  f"{active if active else 'all off'}")
        elif kind == "stop":
            print("[stop] stop-all")
        else:
            print(f"[bridge] unknown message type {kind!r}, ignoring")


def main():
    parser = argparse.ArgumentParser(description="Minegasm local bridge reference adapter")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=12347)
    args = parser.parse_args()

    with socketserver.ThreadingTCPServer((args.host, args.port), Handler) as server:
        server.daemon_threads = True
        print(f"[bridge] listening on tcp://{args.host}:{args.port} (Ctrl-C to quit)")
        try:
            server.serve_forever()
        except KeyboardInterrupt:
            print("\n[bridge] shutting down")


if __name__ == "__main__":
    main()
