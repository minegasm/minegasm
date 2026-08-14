// Command xtoys-adapter bridges Minegasm's local bridge to XToys.
//
// Minegasm dials out to this process over plain TCP and sends one JSON object per line: an "output" frame
// carrying the whole current logical destination set, and a "stop" on any stop or panic.
// Each output frame is authoritative: the adapter replaces the complete logical destination set, so a
// scene ending or being suppressed retracts without the adapter combining a stream of per-scene events.
// In the default role routing mode, the adapter combines destinations by role for the included XToys
// script. Destination routing keeps role, body region, and output class separate.
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

const protocolVersion = 1

func main() {
	listen := flag.String("listen", "127.0.0.1:12347", "TCP address to accept Minegasm's bridge on")
	webhook := flag.String("webhook", "", "XToys webhook id (from a Webhook trigger in your XToys script)")
	endpoint := flag.String("endpoint", "wss://webhook.xtoys.app", "XToys webhook WebSocket base URL")
	scale := flag.Int("scale", 100, "intensity sent at full strength (XToys reads 0..scale)")
	min := flag.Int("min", 20, "motor start threshold: any nonzero effect maps to at least this (0 disables)")
	verbose := flag.Bool("verbose", false, "log every message")
	routing := flag.String("routing", "role", "XToys action routing: role or destination")
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
	if *routing != "role" && *routing != "destination" {
		log.Fatal("-routing must be role or destination")
	}

	x := &xtoys{
		wsURL:          strings.TrimRight(*endpoint, "/") + "/" + *webhook,
		scale:          *scale,
		min:            *min,
		verbose:        *verbose,
		routing:        *routing,
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
// output frame is that connection's whole current destination state, so the adapter just replaces this
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
		typ, compatible := protocolType(frame)
		if !compatible {
			log.Printf("[bridge] refusing unsupported protocol version from %s", peer)
			return
		}
		switch typ {
		case "output":
			x.applyOutput(client, frame)
		case "stop":
			x.stopClient(client)
		}
	}
}

func protocolType(frame map[string]json.RawMessage) (string, bool) {
	var version int
	if err := json.Unmarshal(frame["v"], &version); err != nil || version != protocolVersion {
		return "", false
	}
	var typ string
	if err := json.Unmarshal(frame["type"], &typ); err != nil {
		return "", false
	}
	return typ, true
}

type destination struct {
	Role        string  `json:"role"`
	Region      string  `json:"region"`
	OutputClass string  `json:"outputClass"`
	Level       float64 `json:"level"`
}

// levelsOf reads the complete destination snapshot. Role mode keeps compatibility with the shipped
// six-output script; destination mode exposes one XToys action per role, region, and output class so an
// operator can route independent logical destinations without the adapter guessing about devices.
func levelsOf(rawDestinations json.RawMessage, routing string) map[string]float64 {
	var reported []destination
	json.Unmarshal(rawDestinations, &reported)
	out := map[string]float64{}
	if routing == "role" {
		for _, role := range roles {
			out[role] = 0
		}
	}
	for _, d := range reported {
		if !validRole(d.Role) || d.Region == "" || d.OutputClass == "" {
			continue
		}
		key := d.Role
		if routing == "destination" {
			key = d.Role + "-" + d.Region + "-" + d.OutputClass
		}
		if level := clamp01(d.Level); level > out[key] {
			out[key] = level
		}
	}
	return out
}

func validRole(candidate string) bool {
	for _, role := range roles {
		if candidate == role {
			return true
		}
	}
	return false
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
	levels     map[string]float64
	expiry     time.Time
	generation int64
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
	routing string

	mu          sync.Mutex
	writeMu     sync.Mutex
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
	generation     uint64        // changes with every authoritative state transition
	forceResync    bool          // a fresh socket needs a complete snapshot, zeroes included
	writing        bool
	writingLevels  map[string]int
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

// onDisconnectedLocked records uncertainty after a close or failed write. Any known, explicitly
// attempted, or currently in-flight nonzero keeps a zero obligation latched until a complete zero
// snapshot succeeds. Caller holds x.mu.
func (x *xtoys) onDisconnectedLocked(attempted map[string]int) {
	for channel, level := range x.last {
		if level > 0 || attempted[channel] > 0 {
			x.pendingZero = true
		}
		x.last[channel] = -1
	}
	for channel, level := range attempted {
		if level > 0 {
			x.pendingZero = true
		}
		x.last[channel] = -1
	}
	if x.writing {
		for channel, level := range x.writingLevels {
			if level > 0 {
				x.pendingZero = true
			}
			x.last[channel] = -1
		}
	}
	x.forceResync = true
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
	payload, _ := json.Marshal(map[string]interface{}{"v": protocolVersion, "type": typ, "downstream": downstream})
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

// applyOutput replaces one client's authoritative destination state and recomputes output. A frame with
// ttlMs<=0 drops the client's output. The state is namespaced by client, so two clients never overwrite
// each other, and there is one entry per client, so it cannot grow without limit.
func (x *xtoys) applyOutput(client string, frame map[string]json.RawMessage) {
	levels := levelsOf(frame["destinations"], x.routing)
	var ttlMs int64
	var generation int64
	if json.Unmarshal(frame["ttlMs"], &ttlMs) != nil ||
		json.Unmarshal(frame["generation"], &generation) != nil || generation < 0 {
		return
	}
	if ttlMs > maxTTLms {
		ttlMs = maxTTLms // ttl ceiling
	}

	x.mu.Lock()
	if previous, present := x.state[client]; present && generation <= previous.generation {
		x.mu.Unlock()
		return
	}
	if ttlMs <= 0 {
		delete(x.state, client)
	} else {
		x.state[client] = clientOutput{
			levels:     levels,
			expiry:     time.Now().Add(time.Duration(ttlMs) * time.Millisecond),
			generation: generation,
		}
	}
	x.stateChangedLocked()
	x.mu.Unlock()
	x.flushAsync()
}

// stopClient drops only the given client's output and recomputes, so one client disconnecting or panicking
// never clears another still-connected client's output.
func (x *xtoys) stopClient(client string) {
	x.mu.Lock()
	delete(x.state, client)
	x.stateChangedLocked()
	x.mu.Unlock()
	x.flushAsync()
}

// stopAll drops every client's output and releases all output (used by tests).
func (x *xtoys) stopAll() {
	x.mu.Lock()
	x.state = map[string]clientOutput{}
	x.stateChangedLocked()
	x.mu.Unlock()
	x.flushAsync()
}

// stateChangedLocked expires stale clients, advances the authoritative generation, and rearms expiry.
// It never performs downstream I/O. Caller holds x.mu.
func (x *xtoys) stateChangedLocked() {
	now := time.Now()
	for client, s := range x.state {
		if !s.expiry.After(now) {
			delete(x.state, client)
		}
	}
	// A stop or lower authoritative generation must not sit behind a five-second blocked nonzero write.
	// Closing the socket interrupts that writer; reconnect then sends the newest full snapshot. The state
	// transition itself is already committed under this mutex, and ambiguity conservatively owes a zero.
	if x.writing {
		desired := x.desiredLocked()
		if reducesUncommittedOutput(desired, x.writingLevels, x.last) && x.conn != nil {
			x.pendingZero = true
			conn := x.conn
			go conn.Close()
		}
	}
	x.generation++

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
			x.stateChangedLocked()
			x.mu.Unlock()
			x.flushAsync()
		})
	}
}

func reducesUncommittedOutput(next, inFlight, delivered map[string]int) bool {
	for channel, level := range inFlight {
		if next[channel] < level && delivered[channel] != level {
			return true
		}
	}
	return false
}

func (x *xtoys) flushAsync() {
	go x.flush()
}

// desiredLocked combines every client's current authoritative state. Caller holds x.mu.
func (x *xtoys) desiredLocked() map[string]int {
	desired := map[string]int{}
	for _, role := range roles {
		if x.routing == "role" {
			desired[role] = 0
		}
	}
	for _, state := range x.state {
		for channel, raw := range state.levels {
			level := x.scaleLevel(raw)
			if level > desired[channel] {
				desired[channel] = level
			}
		}
	}
	// A channel seen on the previous connection remains part of a complete zero snapshot.
	for channel := range x.last {
		if _, present := desired[channel]; !present {
			desired[channel] = 0
		}
	}
	return desired
}

// flush is the single downstream writer. State is copied under x.mu, but WebSocket writes happen only
// under writeMu and never under the state mutex. If state changes during a write, the generation check
// loops and writes the newest complete state before returning.
func (x *xtoys) flush() {
	x.writeMu.Lock()
	defer x.writeMu.Unlock()
	for {
		x.mu.Lock()
		conn := x.conn
		if conn == nil {
			x.mu.Unlock()
			return
		}
		generation := x.generation
		desired := x.desiredLocked()
		force := x.forceResync
		x.writing = true
		x.writingLevels = desired
		x.mu.Unlock()

		failed := false
		for channel, level := range desired {
			x.mu.Lock()
			previous := x.last[channel]
			x.mu.Unlock()
			if !force && level == previous {
				continue
			}
			payload, _ := json.Marshal(message{Action: "minegasm-" + channel, Intensity: level})
			if x.verbose {
				log.Printf("[xtoys] send %s", payload)
			}
			conn.SetWriteDeadline(time.Now().Add(5 * time.Second))
			if err := conn.WriteMessage(websocket.TextMessage, payload); err != nil {
				log.Printf("[xtoys] send failed: %v", err)
				x.mu.Lock()
				if x.conn == conn {
					x.conn = nil
					x.onDisconnectedLocked(desired)
				}
				x.writing = false
				x.mu.Unlock()
				conn.Close()
				failed = true
				break
			}
			x.mu.Lock()
			x.last[channel] = level
			x.mu.Unlock()
		}
		if failed {
			return
		}

		x.mu.Lock()
		x.writing = false
		if x.conn != conn {
			x.mu.Unlock()
			return
		}
		if generation == x.generation {
			x.forceResync = false
			if allZero(desired) {
				x.pendingZero = false
			}
			x.mu.Unlock()
			return
		}
		x.mu.Unlock()
	}
}

func allZero(levels map[string]int) bool {
	for _, level := range levels {
		if level > 0 {
			return false
		}
	}
	return true
}

// tryDialAndResync opens the WebSocket without holding the mutex (the handshake can block up to its
// timeout), then adopts the connection and asks the single writer to send the current full state.
// Throttling prevents a down server from being hammered. A concurrent stop advances the state generation,
// so the writer either sees the zero immediately or loops and sends it after the older snapshot.
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
	if err != nil {
		x.nextDial = time.Now().Add(x.dialThrottle)
		log.Printf("[xtoys] connect failed: %v", err)
		x.mu.Unlock()
		return
	}
	if x.conn != nil { // a concurrent dial won the race; keep the existing one
		x.mu.Unlock()
		conn.Close()
		return
	}
	x.conn = conn
	x.forceResync = true
	x.generation++
	log.Printf("[xtoys] connected to %s", x.wsURL)
	x.notifyDownstream() // tell the mod the onward link is up now
	x.mu.Unlock()
	go x.readLoop(conn)
	x.flushAsync()
}

// readLoop drains inbound messages so gorilla answers pings and we notice a close; on any read error it
// drops the connection so the next send redials.
func (x *xtoys) readLoop(conn *websocket.Conn) {
	for {
		if _, _, err := conn.ReadMessage(); err != nil {
			x.mu.Lock()
			if x.conn == conn {
				x.conn = nil
				x.onDisconnectedLocked(map[string]int{})
			}
			x.mu.Unlock()
			conn.Close()
			return
		}
	}
}
