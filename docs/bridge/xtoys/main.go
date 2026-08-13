// Command xtoys-adapter bridges Minegasm's local bridge to XToys.
//
// Minegasm dials out to this process over plain TCP and sends one JSON object per line: an "output" frame
// carrying the whole current level per role, and a "stop" on any stop/panic (see docs/bridge/PROTOCOL.md).
// Each output frame is authoritative: the adapter sets each role to the level it is handed, so a scene
// ending or being suppressed retracts as soon as its role's level drops, without the adapter combining a
// stream of per-scene events. This adapter forwards each role to XToys over the webhook's WebSocket, which
// the included script (xtoys-minegasm.json) matches by action name and uses to drive your outputs.
//
// A role is device-independent (IMPACT, REWARD, TEXTURE, WARNING, AMBIENT, CONTROL). The adapter exposes
// each role as its own XToys output rather than collapsing everything to one level, so several actuators
// can run at once. It makes no device decisions: it sends role -> intensity and you route each role to a
// toy in XToys. XToys' generic output is device-agnostic, so an output can drive a vibrator, stroker, or
// rotator. Do not route it to an e-stim device: this adapter sends a plain scene intensity with none of
// the safeguards a shock output needs (see the README and ADR-016).
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
	"fmt"
	"log"
	"math"
	"net"
	"strings"
	"sync"
	"time"

	"github.com/gorilla/websocket"
)

// maxListeners bounds the number of Minegasm connections we track for downstream status.
const maxListeners = 16

// Hard bounds so a local process, or a remote peer if the operator binds beyond loopback, can't exhaust
// memory. Each Minegasm connection is one authoritative output entry, so bounding connections bounds state.
const (
	maxClients = 4      // concurrent Minegasm connections accepted; more are refused
	maxTTLms   = 60_000 // ttl ceiling per output snapshot
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
		wsURL:          strings.TrimRight(*endpoint, "/") + "/" + *webhook,
		scale:          *scale,
		min:            *min,
		verbose:        *verbose,
		last:           map[string]int{},
		state:          map[string]clientOutput{},
		listeners:      map[net.Conn]bool{},
		reconnectEvery: time.Second,
		dialThrottle:   3 * time.Second,
	}
	x.tryDialAndResync() // best-effort initial dial
	go x.reconnectLoop() // keeps the link alive and resynchronizes after a drop

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
// output frame is that connection's whole current per-role state, so the adapter just replaces this
// client's entry; a dropped connection self-clears on close.
func handle(conn net.Conn, x *xtoys) {
	peer := conn.RemoteAddr()
	if !x.tryAddClient() {
		log.Printf("[bridge] refused %s: too many clients", peer)
		conn.Close()
		return
	}
	defer x.removeClient()
	client := x.nextClientID() // namespaces this connection's output
	log.Printf("[bridge] connected: %s", peer)
	x.addListener(conn) // greet with a hello carrying the current XToys link state
	defer func() {
		log.Printf("[bridge] disconnected: %s", peer)
		x.removeListener(conn)
		x.stopClient(client) // release only this client's output, not another client's
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
		case "output":
			x.applyOutput(client, frame)
		case "stop":
			x.stopClient(client)
		}
	}
}

// levelsOf reads the authoritative per-role levels from an output frame, clamped to [0,1]. Every role is
// present in the result; a role the frame omits maps to 0, so a role the mod dropped retracts.
func levelsOf(rawRoles json.RawMessage) map[string]float64 {
	var reported map[string]float64
	json.Unmarshal(rawRoles, &reported)
	out := map[string]float64{}
	for _, role := range roles {
		out[role] = clamp01(reported[role])
	}
	return out
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

// clientOutput is one Minegasm connection's authoritative per-role levels and when they expire without a
// refresh. The mod sends the whole state on every change and re-sends it periodically, so a live effect
// keeps refreshing the expiry; a dropped or half-open link lets it lapse and the role zeroes.
type clientOutput struct {
	levels map[string]float64
	expiry time.Time
}

// xtoys streams to the XToys webhook over a single WebSocket. Writes are serialized under mu; each client's
// authoritative levels are combined per role (strongest wins) and only changed roles produce traffic. A
// timer fires at the next client expiry so an output self-releases after its ttl even if no further frame
// arrives.
type xtoys struct {
	wsURL   string
	scale   int
	min     int
	verbose bool

	mu          sync.Mutex
	conn        *websocket.Conn
	last        map[string]int // intensity XToys is believed to hold per role; -1 means unknown
	pendingZero bool           // a non-zero role could not be zeroed while offline; owe XToys a zero
	nextDial    time.Time      // throttle: don't hammer a down server on every reconnect attempt
	state       map[string]clientOutput
	timer       *time.Timer
	listeners   map[net.Conn]bool // Minegasm connections to notify of downstream (XToys) state

	reconnectEvery time.Duration // how often the reconnect loop retries (configurable for tests)
	dialThrottle   time.Duration // minimum gap between failed dials
	clientSeq      int64         // hands each Minegasm connection a distinct id to namespace its output
	clients        int           // count of connected Minegasm clients, for the accept limit
}

// tryAddClient admits a Minegasm connection if under the limit. Returns false if it should be refused.
func (x *xtoys) tryAddClient() bool {
	x.mu.Lock()
	defer x.mu.Unlock()
	if x.clients >= maxClients {
		return false
	}
	x.clients++
	return true
}

func (x *xtoys) removeClient() {
	x.mu.Lock()
	defer x.mu.Unlock()
	if x.clients > 0 {
		x.clients--
	}
}

// nextClientID returns a fresh per-connection id so one client's output never collides with another's.
func (x *xtoys) nextClientID() string {
	x.mu.Lock()
	defer x.mu.Unlock()
	x.clientSeq++
	return fmt.Sprintf("c%d", x.clientSeq)
}

// reconnectLoop keeps the downstream link alive independently of output changes. While the socket is down
// and there is either a connected client, live output, or an owed zero, it redials; on success it resends
// the full current state so a steady effect, a role that changed while offline, and a zero owed from an
// expiry or panic all reach XToys after a drop (review follow-up P1-5). The blocking dial runs without the
// mutex held, so a slow or timing-out reconnect never delays a stop the mod is sending (review P1-4).
func (x *xtoys) reconnectLoop() {
	for range time.Tick(x.reconnectEvery) {
		x.mu.Lock()
		need := x.conn == nil && (x.clients > 0 || len(x.state) > 0 || x.pendingZero) &&
			!time.Now().Before(x.nextDial)
		x.mu.Unlock()
		if need {
			x.tryDialAndResync()
		}
	}
}

// resyncAll forces a full resend of every role's current level (zeroes included) after a reconnect, so
// XToys is put into a known state rather than left with whatever it had before the drop. Caller holds mu.
func (x *xtoys) resyncAll() {
	for _, role := range roles {
		x.last[role] = -1 // unknown, so recompute sends every role
	}
	x.pendingZero = false
	x.recompute()
}

// onDisconnected records that the socket dropped: what XToys now holds is unknown, and if any role was
// non-zero we owe it a zero we could not deliver. Caller holds mu.
func (x *xtoys) onDisconnected() {
	for _, role := range roles {
		if x.last[role] > 0 {
			x.pendingZero = true
		}
		x.last[role] = -1
	}
	x.notifyDownstream()
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
// does not hold x.mu. The greeting write happens outside the lock so a slow mod socket can't stall the
// XToys path.
func (x *xtoys) addListener(conn net.Conn) {
	x.mu.Lock()
	if len(x.listeners) >= maxListeners {
		x.mu.Unlock()
		return // too many connections tracked; ignore this one for status (it still drives output)
	}
	x.listeners[conn] = true
	word := x.downstreamWord()
	x.mu.Unlock()
	writeControl(conn, "hello", word)
}

func (x *xtoys) removeListener(conn net.Conn) {
	x.mu.Lock()
	defer x.mu.Unlock()
	delete(x.listeners, conn)
}

// notifyDownstream pushes a status line to every connected mod on an XToys connect/drop. Caller holds
// x.mu; the writes are done on a separate goroutine so a listener that blocks for up to the write deadline
// never holds the XToys mutex.
func (x *xtoys) notifyDownstream() {
	word := x.downstreamWord()
	conns := make([]net.Conn, 0, len(x.listeners))
	for conn := range x.listeners {
		conns = append(conns, conn)
	}
	go func() {
		for _, conn := range conns {
			writeControl(conn, "status", word)
		}
	}()
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

// applyOutput replaces one client's authoritative per-role state and recomputes output. A frame with
// ttlMs<=0 drops the client's output. The state is namespaced by client, so two clients never overwrite
// each other, and there is one entry per client, so it cannot grow without limit.
func (x *xtoys) applyOutput(client string, frame map[string]json.RawMessage) {
	levels := levelsOf(frame["roles"])
	var ttlMs int64
	json.Unmarshal(frame["ttlMs"], &ttlMs)
	if ttlMs > maxTTLms {
		ttlMs = maxTTLms // ttl ceiling
	}

	x.mu.Lock()
	defer x.mu.Unlock()
	if ttlMs <= 0 {
		delete(x.state, client)
	} else {
		x.state[client] = clientOutput{
			levels: levels,
			expiry: time.Now().Add(time.Duration(ttlMs) * time.Millisecond),
		}
	}
	x.recompute()
}

// stopClient drops only the given client's output and recomputes, so one client disconnecting or panicking
// never clears another still-connected client's output.
func (x *xtoys) stopClient(client string) {
	x.mu.Lock()
	defer x.mu.Unlock()
	delete(x.state, client)
	x.recompute()
}

// stopAll drops every client's output and releases all output (used by tests).
func (x *xtoys) stopAll() {
	x.mu.Lock()
	defer x.mu.Unlock()
	x.state = map[string]clientOutput{}
	x.recompute()
}

// recompute expires stale clients, sends any role whose combined level changed, and rearms the expiry
// timer. Caller holds x.mu.
func (x *xtoys) recompute() {
	now := time.Now()
	for client, s := range x.state {
		if !s.expiry.After(now) {
			delete(x.state, client)
		}
	}
	for _, role := range roles {
		var peak float64
		for _, s := range x.state {
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
	for _, s := range x.state {
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

// send writes one role message if the socket is up. On a write failure the socket is dropped and last is
// left unchanged, so the next frame re-sends after a reconnect. Caller holds x.mu.
func (x *xtoys) send(role string, level int) {
	if level < 0 {
		level = 0
	}
	if x.conn == nil {
		return // offline: the reconnect loop dials and resends the full state, so don't dial per frame
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
		x.onDisconnected() // the onward link dropped: state is unknown and a zero may now be owed
		return
	}
	x.last[role] = level
}

// tryDialAndResync opens the WebSocket without holding the mutex (the handshake can block up to its
// timeout), then adopts the connection and resends the full current state under the lock. Throttled so a
// down server is retried at most every few seconds. Because the resync runs entirely under the lock, a
// stop the mod sends is serialized against it: it either clears the state before the resync reads it, or
// runs after and sends the zeros itself, so an owed zero is never lost across a reconnect (review P1-4).
func (x *xtoys) tryDialAndResync() {
	x.mu.Lock()
	if x.conn != nil || time.Now().Before(x.nextDial) {
		x.mu.Unlock()
		return
	}
	x.mu.Unlock()

	dialer := websocket.Dialer{HandshakeTimeout: 5 * time.Second}
	conn, _, err := dialer.Dial(x.wsURL, nil)

	x.mu.Lock()
	defer x.mu.Unlock()
	if err != nil {
		x.nextDial = time.Now().Add(x.dialThrottle)
		log.Printf("[xtoys] connect failed: %v", err)
		return
	}
	if x.conn != nil { // a concurrent dial won the race; keep the existing one
		conn.Close()
		return
	}
	x.conn = conn
	log.Printf("[xtoys] connected to %s", x.wsURL)
	go x.readLoop(conn)
	x.notifyDownstream() // tell the mod the onward link is up now
	x.resyncAll()        // resend the full state to the freshly connected socket
}

// readLoop drains inbound messages so gorilla answers pings and we notice a close; on any read error it
// drops the connection so the next send redials.
func (x *xtoys) readLoop(conn *websocket.Conn) {
	for {
		if _, _, err := conn.ReadMessage(); err != nil {
			x.mu.Lock()
			if x.conn == conn {
				x.conn = nil
				x.onDisconnected() // state is unknown and a zero may now be owed
			}
			x.mu.Unlock()
			conn.Close()
			return
		}
	}
}
