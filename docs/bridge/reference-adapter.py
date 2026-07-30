#!/usr/bin/env python3
"""Reference adapter for the Minegasm local bridge.

The bridge connects out to this process over plain TCP and sends one JSON object per line: an
`effect` message when a scene fires, and a `stop` message on any stop/panic. This adapter just
prints them, it is the smallest thing that proves the protocol and a starting point for a real
adapter (drive a motor, forward to another API, blink an LED).

No dependencies; standard library only. Run it, then enable the bridge in Minegasm's config
(transport `tcp`, endpoint `tcp://127.0.0.1:12347`) and start the game.

Message shapes (see docs/bridge/PROTOCOL.md):
  {"v":1,"type":"effect","sceneId":...,"event":"HURT","priority":100,"ttlMs":300,"layers":[...]}
  {"v":1,"type":"stop"}

Each effect carries a ttlMs: if you start a continuous output, stop it after ttlMs even if no
further message arrives, so a dropped connection can never leave something running.
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
        if kind == "effect":
            layers = msg.get("layers", [])
            peak = max((layer.get("primitive", {}).get("level", 0.0) for layer in layers), default=0.0)
            print(f"[effect] {msg.get('event')} "
                  f"peak={peak:.2f} ttl={msg.get('ttlMs')}ms layers={len(layers)}")
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
