// Command xtoys-adapter bridges Minegasm's local bridge to XToys.
//
// Minegasm dials out to this process over plain TCP and sends one JSON object per line: an "effect"
// when a scene fires and a "stop" on any stop/panic (see docs/bridge/PROTOCOL.md). This adapter turns
// each effect into a per-role intensity and forwards it to XToys over the webhook's WebSocket, which the
// included script (xtoys-minegasm.json) matches by action name and uses to drive your outputs.
//
// A scene layer carries a device-independent role (IMPACT, REWARD, TEXTURE, WARNING, AMBIENT, CONTROL).
// The adapter exposes each role as its own XToys output rather than collapsing everything to one level,
// so several actuators can run at once. It makes no device decisions: it sends role -> intensity and you
// route each role to a toy in XToys. XToys' generic output is device-agnostic, so an output drives
// whatever you connect (vibrator, stroker, e-stim, rotator), not vibration alone.
//
// Transport is XToys' webhook WebSocket: one long-lived connection to
//
//	wss://webhook.xtoys.app/<webhook-id>
//
// over which the adapter writes a JSON message per role that changed:
//
//	{"action":"minegasm-<role>","intensity":<0-100>}
//
// A persistent socket avoids a TLS handshake per update and streams smoothly. The socket reconnects on
// its own if XToys restarts. The script has a Webhook trigger per role that reads `intensity` (0-100) and
// sets that role's output, so 0 stops it.
package main

import (
	"bufio"
	"encoding/json"
	"flag"
	"log"
	"math"
	"net"
	"strings"
	"sync"
	"time"

	"github.com/gorilla/websocket"
)

// roles is the fixed set of layer roles Minegasm emits (net.minegasm.core.HapticRole), lowercased to
// use as XToys action names. The shipped script has a matching Webhook trigger and output per entry.
var roles = []string{"impact", "reward", "texture", "warning", "ambient", "control"}

func main() {
	listen := flag.String("listen", "127.0.0.1:12347", "TCP address to accept Minegasm's bridge on")
	webhook := flag.String("webhook", "", "XToys webhook id (from a Webhook trigger in your XToys script)")
	endpoint := flag.String("endpoint", "wss://webhook.xtoys.app", "XToys webhook WebSocket base URL")
	scale := flag.Int("scale", 100, "intensity sent at full strength (XToys reads 0..scale)")
	min := flag.Int("min", 20, "motor start threshold: any nonzero effect maps to at least this (0 disables)")
	verbose := flag.Bool("verbose", false, "log every message")
	flag.Parse()

	if *webhook == "" {
		log.Fatal("missing -webhook: create a Webhook trigger in XToys and pass its id")
	}
	if *min < 0 {
		*min = 0
	}
	if *min > *scale {
		*min = *scale
	}

	x := &xtoys{
		wsURL:     strings.TrimRight(*endpoint, "/") + "/" + *webhook,
		scale:     *scale,
		min:       *min,
		verbose:   *verbose,
		last:      map[string]int{},
		active:    map[string]liveScene{},
		listeners: map[net.Conn]bool{},
	}
	x.connect() // best-effort; send() redials if this fails or the socket later drops

	ln, err := net.Listen("tcp", *listen)
	if err != nil {
		log.Fatalf("listen %s: %v", *listen, err)
	}
	log.Printf("[xtoys] listening on %s, streaming to %s (roles %v)", *listen, x.wsURL, roles)
	for {
		conn, err := ln.Accept()
		if err != nil {
			log.Printf("[xtoys] accept: %v", err)
			continue
		}
		go handle(conn, x)
	}
}

// handle reads the line-delimited bridge protocol from one Minegasm connection and drives XToys. Each
// effect frame is one scene (Minegasm forwards scenes independently, not a combined state), so the adapter
// tracks scenes separately and holds each for its own ttlMs; a dropped connection self-clears on close.
func handle(conn net.Conn, x *xtoys) {
	peer := conn.RemoteAddr()
	log.Printf("[bridge] connected: %s", peer)
	x.addListener(conn) // greet with a hello carrying the current XToys link state
	defer func() {
		log.Printf("[bridge] disconnected: %s", peer)
		x.removeListener(conn)
		x.stopAll()
		conn.Close()
	}()

	scanner := bufio.NewScanner(conn)
	scanner.Buffer(make([]byte, 0, 64*1024), 1<<20)
	for scanner.Scan() {
		line := scanner.Bytes()
		if len(line) == 0 {
			continue
		}
		var frame map[string]json.RawMessage
		if json.Unmarshal(line, &frame) != nil {
			continue
		}
		var typ string
		json.Unmarshal(frame["type"], &typ)
		switch typ {
		case "effect":
			x.applyEffect(frame)
		case "stop":
			x.stopAll()
		}
	}
}

// sceneKey identifies a scene across its update frames: continuous scenes update the same continuousKey,
// discrete scenes are keyed by sceneId so a repeat replaces (refreshes) the prior one.
func sceneKey(frame map[string]json.RawMessage) string {
	if raw, ok := frame["continuousKey"]; ok {
		var key string
		if json.Unmarshal(raw, &key); key != "" {
			return "c:" + key
		}
	}
	var sceneID string
	json.Unmarshal(frame["sceneId"], &sceneID)
	return "d:" + sceneID
}

// perRole is the strongest primitive level for each role across a scene's layers, in [0,1]. Every role is
// present in the result; roles with no layer in this scene map to 0. Scaling and the start-threshold floor
// are applied later, on the value combined across all live scenes.
func perRole(rawLayers json.RawMessage) map[string]float64 {
	var layers []struct {
		Role      string `json:"role"`
		Primitive struct {
			Level *float64 `json:"level"`
			From  *float64 `json:"from"`
			To    *float64 `json:"to"`
			Beats []struct {
				Level float64 `json:"level"`
			} `json:"beats"`
		} `json:"primitive"`
	}
	json.Unmarshal(rawLayers, &layers)

	peak := map[string]float64{}
	for _, l := range layers {
		role := strings.ToLower(l.Role)
		peak[role] = math.Max(peak[role], primitiveLevel(l.Primitive))
	}

	out := map[string]float64{}
	for _, role := range roles {
		out[role] = clamp01(peak[role])
	}
	return out
}

// primitiveLevel is a primitive's representative level in [0,1]: its steady level, the larger endpoint of
// a ramp, or the strongest beat.
func primitiveLevel(p struct {
	Level *float64 `json:"level"`
	From  *float64 `json:"from"`
	To    *float64 `json:"to"`
	Beats []struct {
		Level float64 `json:"level"`
	} `json:"beats"`
}) float64 {
	switch {
	case p.Level != nil:
		return *p.Level
	case p.From != nil || p.To != nil:
		max := 0.0
		if p.From != nil {
			max = math.Max(max, *p.From)
		}
		if p.To != nil {
			max = math.Max(max, *p.To)
		}
		return max
	default:
		max := 0.0
		for _, b := range p.Beats {
			max = math.Max(max, b.Level)
		}
		return max
	}
}

func clamp01(v float64) float64 {
	if v < 0 {
		return 0
	}
	if v > 1 {
		return 1
	}
	return v
}

// message is one webhook payload over the socket.
type message struct {
	Action    string `json:"action"`
	Intensity int    `json:"intensity"`
}

// liveScene is one scene the mod currently has active: its per-role levels and when it expires (now +
// the frame's ttlMs). The output for a role is the strongest level across every live scene, so a
// transient impact and a steady texture coexist instead of overwriting each other.
type liveScene struct {
	levels map[string]float64
	expiry time.Time
}

// xtoys streams to the XToys webhook over a single WebSocket. Writes are serialized under mu; live scenes
// are combined per role and only changed roles produce traffic. A timer fires at the next scene expiry so
// an effect self-releases after its ttl even if no further frame arrives.
type xtoys struct {
	wsURL   string
	scale   int
	min     int
	verbose bool

	mu        sync.Mutex
	conn      *websocket.Conn
	last      map[string]int
	nextDial  time.Time // throttle: don't hammer a down server on every frame
	active    map[string]liveScene
	timer     *time.Timer
	listeners map[net.Conn]bool // Minegasm connections to notify of downstream (XToys) state
}

// downstreamWord is what the mod is told about the onward XToys link. Caller holds x.mu.
func (x *xtoys) downstreamWord() string {
	if x.conn != nil {
		return "ready"
	}
	return "unavailable"
}

// writeControl sends one control line (hello/status) back to a Minegasm connection so it can show the
// whole chain, not just that this adapter is running. Best-effort with a short deadline so a slow mod
// socket can't stall the XToys path (see docs/bridge/PROTOCOL.md).
func writeControl(conn net.Conn, typ, downstream string) {
	payload, _ := json.Marshal(map[string]interface{}{"v": 1, "type": typ, "downstream": downstream})
	conn.SetWriteDeadline(time.Now().Add(2 * time.Second))
	conn.Write(append(payload, '\n'))
}

// addListener registers a Minegasm connection and greets it with the current downstream state. Caller
// does not hold x.mu.
func (x *xtoys) addListener(conn net.Conn) {
	x.mu.Lock()
	defer x.mu.Unlock()
	x.listeners[conn] = true
	writeControl(conn, "hello", x.downstreamWord())
}

func (x *xtoys) removeListener(conn net.Conn) {
	x.mu.Lock()
	defer x.mu.Unlock()
	delete(x.listeners, conn)
}

// notifyDownstream pushes a status line to every connected mod on an XToys connect/drop. Caller holds x.mu.
func (x *xtoys) notifyDownstream() {
	word := x.downstreamWord()
	for conn := range x.listeners {
		writeControl(conn, "status", word)
	}
}

// scaleLevel maps a role level in [0,1] to an XToys intensity. Zero stays zero (off); any nonzero level
// maps into [min, scale], so a faint effect (e.g. an impact at 0.04) still clears the motor's start
// threshold instead of sending an imperceptible 4.
func (x *xtoys) scaleLevel(v float64) int {
	if v <= 0 {
		return 0
	}
	return x.min + int(math.Round(v*float64(x.scale-x.min)))
}

// applyEffect records one scene frame and recomputes output. A frame with ttlMs<=0 drops the scene.
func (x *xtoys) applyEffect(frame map[string]json.RawMessage) {
	key := sceneKey(frame)
	levels := perRole(frame["layers"])
	var ttlMs int64
	json.Unmarshal(frame["ttlMs"], &ttlMs)

	x.mu.Lock()
	defer x.mu.Unlock()
	if ttlMs <= 0 {
		delete(x.active, key)
	} else {
		x.active[key] = liveScene{levels: levels, expiry: time.Now().Add(time.Duration(ttlMs) * time.Millisecond)}
	}
	x.recompute()
}

// stopAll drops every live scene and releases all output.
func (x *xtoys) stopAll() {
	x.mu.Lock()
	defer x.mu.Unlock()
	x.active = map[string]liveScene{}
	x.recompute()
}

// recompute expires stale scenes, sends any role whose combined level changed, and rearms the expiry
// timer. Caller holds x.mu.
func (x *xtoys) recompute() {
	now := time.Now()
	for key, s := range x.active {
		if !s.expiry.After(now) {
			delete(x.active, key)
		}
	}
	for _, role := range roles {
		var peak float64
		for _, s := range x.active {
			if v := s.levels[role]; v > peak {
				peak = v
			}
		}
		level := x.scaleLevel(peak)
		if level != x.last[role] {
			x.send(role, level)
		}
	}

	if x.timer != nil {
		x.timer.Stop()
		x.timer = nil
	}
	var earliest time.Time
	for _, s := range x.active {
		if earliest.IsZero() || s.expiry.Before(earliest) {
			earliest = s.expiry
		}
	}
	if !earliest.IsZero() {
		delay := earliest.Sub(now)
		if delay < 0 {
			delay = 0
		}
		x.timer = time.AfterFunc(delay, func() {
			x.mu.Lock()
			defer x.mu.Unlock()
			x.recompute()
		})
	}
}

// send writes one role message, dialing lazily if the socket is down. On a write failure the socket is
// dropped and last is left unchanged, so the next frame re-sends after a reconnect. Caller holds x.mu.
func (x *xtoys) send(role string, level int) {
	if level < 0 {
		level = 0
	}
	if x.conn == nil && !x.dial() {
		return // still down; drop this update, retry on a later frame
	}
	payload, _ := json.Marshal(message{Action: "minegasm-" + role, Intensity: level})
	if x.verbose {
		log.Printf("[xtoys] send %s", payload)
	}
	x.conn.SetWriteDeadline(time.Now().Add(5 * time.Second))
	if err := x.conn.WriteMessage(websocket.TextMessage, payload); err != nil {
		log.Printf("[xtoys] send failed: %v", err)
		x.conn.Close()
		x.conn = nil
		x.notifyDownstream() // the onward link just dropped
		return
	}
	x.last[role] = level
}

// connect dials the socket at startup, logging the outcome. Caller does not hold x.mu.
func (x *xtoys) connect() {
	x.mu.Lock()
	defer x.mu.Unlock()
	x.dial()
}

// dial opens the WebSocket and starts a reader to service control frames and notice a close. Throttled so
// a down server is retried at most every few seconds. Returns whether a connection is now open. Caller
// holds x.mu.
func (x *xtoys) dial() bool {
	if x.conn != nil {
		return true
	}
	if time.Now().Before(x.nextDial) {
		return false
	}
	dialer := websocket.Dialer{HandshakeTimeout: 5 * time.Second}
	conn, _, err := dialer.Dial(x.wsURL, nil)
	if err != nil {
		x.nextDial = time.Now().Add(3 * time.Second)
		log.Printf("[xtoys] connect failed: %v", err)
		return false
	}
	x.conn = conn
	log.Printf("[xtoys] connected to %s", x.wsURL)
	x.notifyDownstream() // tell the mod the onward link is up now
	go x.readLoop(conn)
	return true
}

// readLoop drains inbound messages so gorilla answers pings and we notice a close; on any read error it
// drops the connection so the next send redials.
func (x *xtoys) readLoop(conn *websocket.Conn) {
	for {
		if _, _, err := conn.ReadMessage(); err != nil {
			x.mu.Lock()
			if x.conn == conn {
				x.conn = nil
				x.notifyDownstream() // the onward link just dropped
			}
			x.mu.Unlock()
			conn.Close()
			return
		}
	}
}
