package main

import (
	"encoding/json"
	"net"
	"net/http"
	"net/http/httptest"
	"strings"
	"sync"
	"sync/atomic"
	"testing"
	"time"

	"github.com/gorilla/websocket"
)

var frameGeneration int64

// fakeXToys is a minimal WebSocket server that records the intensity sent per action and lets a test drop
// the live connection to simulate a downstream outage.
type fakeXToys struct {
	mu       sync.Mutex
	received []message
	conns    []*websocket.Conn
}

func (f *fakeXToys) handler(w http.ResponseWriter, r *http.Request) {
	up := websocket.Upgrader{CheckOrigin: func(*http.Request) bool { return true }}
	c, err := up.Upgrade(w, r, nil)
	if err != nil {
		return
	}
	f.mu.Lock()
	f.conns = append(f.conns, c)
	f.mu.Unlock()
	for {
		_, data, err := c.ReadMessage()
		if err != nil {
			return
		}
		var m message
		if json.Unmarshal(data, &m) == nil {
			f.mu.Lock()
			f.received = append(f.received, m)
			f.mu.Unlock()
		}
	}
}

func (f *fakeXToys) dropAll() {
	f.mu.Lock()
	defer f.mu.Unlock()
	for _, c := range f.conns {
		c.Close()
	}
	f.conns = nil
}

func (f *fakeXToys) latest(action string) (int, bool) {
	f.mu.Lock()
	defer f.mu.Unlock()
	for i := len(f.received) - 1; i >= 0; i-- {
		if f.received[i].Action == action {
			return f.received[i].Intensity, true
		}
	}
	return 0, false
}

func (f *fakeXToys) count() int {
	f.mu.Lock()
	defer f.mu.Unlock()
	return len(f.received)
}

func newTestAdapter(wsURL string) *xtoys {
	return &xtoys{
		wsURL:          wsURL,
		scale:          100,
		min:            20,
		routing:        "role",
		last:           map[string]int{},
		state:          map[string]clientOutput{},
		listeners:      map[net.Conn]bool{},
		reconnectEvery: 15 * time.Millisecond,
		dialThrottle:   15 * time.Millisecond,
	}
}

// outputFrame builds an authoritative destination snapshot.
func outputFrame(levels map[string]float64, ttlMs int) map[string]json.RawMessage {
	destinations := make([]map[string]interface{}, 0, len(levels))
	for role, level := range levels {
		destinations = append(destinations, map[string]interface{}{
			"role": role, "region": "whole_body", "outputClass": "strength", "level": level,
		})
	}
	raw, _ := json.Marshal(map[string]interface{}{
		"v": protocolVersion, "type": "output", "generation": atomic.AddInt64(&frameGeneration, 1),
		"ttlMs": ttlMs, "destinations": destinations,
	})
	var frame map[string]json.RawMessage
	json.Unmarshal(raw, &frame)
	return frame
}

func TestProtocolVersionFailsClosed(t *testing.T) {
	cases := []string{
		`{"type":"output"}`,
		`{"v":1.0,"type":"output"}`,
		`{"v":"1","type":"output"}`,
		`{"v":2,"type":"output"}`,
	}
	for _, raw := range cases {
		var frame map[string]json.RawMessage
		json.Unmarshal([]byte(raw), &frame)
		if _, ok := protocolType(frame); ok {
			t.Fatalf("accepted incompatible frame %s", raw)
		}
	}
	var valid map[string]json.RawMessage
	json.Unmarshal([]byte(`{"v":1,"type":"output"}`), &valid)
	if typ, ok := protocolType(valid); !ok || typ != "output" {
		t.Fatal("rejected protocol v1 output")
	}
}

func TestDisconnectDuringUncommittedNonzeroOwesZero(t *testing.T) {
	x := newTestAdapter("")
	x.mu.Lock()
	x.writing = true
	x.writingLevels = map[string]int{"impact": 84}
	x.onDisconnectedLocked(map[string]int{})
	owed := x.pendingZero
	x.mu.Unlock()

	if !owed {
		t.Fatal("an ambiguous in-flight nonzero must keep a zero obligation")
	}
}

func waitFor(t *testing.T, what string, cond func() bool) {
	t.Helper()
	deadline := time.Now().Add(3 * time.Second)
	for time.Now().Before(deadline) {
		if cond() {
			return
		}
		time.Sleep(5 * time.Millisecond)
	}
	t.Fatalf("timed out waiting for %s", what)
}

func (x *xtoys) offline() bool {
	x.mu.Lock()
	defer x.mu.Unlock()
	return x.conn == nil
}

func wsURLOf(srv *httptest.Server) string {
	return "ws" + strings.TrimPrefix(srv.URL, "http")
}

func TestReconnectResendsCurrentState(t *testing.T) {
	f := &fakeXToys{}
	srv := httptest.NewServer(http.HandlerFunc(f.handler))
	defer srv.Close()

	x := newTestAdapter(wsURLOf(srv))
	x.tryDialAndResync()
	go x.reconnectLoop()

	x.applyOutput("c1", outputFrame(map[string]float64{"impact": 0.8}, 5000))
	waitFor(t, "the impact reaches XToys", func() bool {
		v, ok := f.latest("minegasm-impact")
		return ok && v > 0
	})

	before := f.count()
	f.dropAll() // the downstream link drops while a steady effect is active
	waitFor(t, "the adapter reconnects and resends the active effect", func() bool {
		v, ok := f.latest("minegasm-impact")
		return ok && v > 0 && f.count() > before
	})
}

func TestVanishedRoleRetractsAtTheAdapter(t *testing.T) {
	f := &fakeXToys{}
	srv := httptest.NewServer(http.HandlerFunc(f.handler))
	defer srv.Close()

	x := newTestAdapter(wsURLOf(srv))
	x.tryDialAndResync()

	x.applyOutput("c1", outputFrame(map[string]float64{"impact": 0.8}, 5000))
	waitFor(t, "the impact reaches XToys", func() bool {
		v, ok := f.latest("minegasm-impact")
		return ok && v > 0
	})

	// The mod's next authoritative snapshot drops impact to zero (the effect ended). The adapter must
	// zero the role, not keep the last non-zero level, since every frame is the full state (P1-3).
	x.applyOutput("c1", outputFrame(map[string]float64{"impact": 0}, 5000))
	waitFor(t, "the role retracts to zero", func() bool {
		v, ok := f.latest("minegasm-impact")
		return ok && v == 0
	})
}

func TestOwedZeroIsDeliveredAfterReconnect(t *testing.T) {
	f := &fakeXToys{}
	srv := httptest.NewServer(http.HandlerFunc(f.handler))
	defer srv.Close()

	x := newTestAdapter(wsURLOf(srv))
	x.tryDialAndResync()
	go x.reconnectLoop()

	x.applyOutput("c1", outputFrame(map[string]float64{"impact": 0.8}, 5000))
	waitFor(t, "the impact reaches XToys", func() bool {
		v, ok := f.latest("minegasm-impact")
		return ok && v > 0
	})

	f.dropAll()
	waitFor(t, "the adapter notices the drop", x.offline)

	// The scene ends while offline, so the adapter owes XToys a zero it could not deliver.
	x.stopAll()
	waitFor(t, "the owed zero is delivered on reconnect", func() bool {
		v, ok := f.latest("minegasm-impact")
		return ok && v == 0
	})
}

func TestPerRoleCombinesAcrossClients(t *testing.T) {
	f := &fakeXToys{}
	srv := httptest.NewServer(http.HandlerFunc(f.handler))
	defer srv.Close()
	x := newTestAdapter(wsURLOf(srv))
	x.tryDialAndResync()

	// Two clients driving the same role combine to the stronger level, not overwrite each other.
	x.applyOutput("c1", outputFrame(map[string]float64{"texture": 0.3}, 5000))
	x.applyOutput("c2", outputFrame(map[string]float64{"texture": 0.6}, 5000))
	waitFor(t, "the stronger texture level wins", func() bool {
		v, ok := f.latest("minegasm-texture")
		// 0.6 scaled into [20,100] is 68; the weaker 0.3 would be 44.
		return ok && v >= 60
	})
}
