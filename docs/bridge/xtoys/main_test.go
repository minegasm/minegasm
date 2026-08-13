package main

import (
	"encoding/json"
	"net"
	"net/http"
	"net/http/httptest"
	"strings"
	"sync"
	"testing"
	"time"

	"github.com/gorilla/websocket"
)

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
		last:           map[string]int{},
		active:         map[string]liveScene{},
		listeners:      map[net.Conn]bool{},
		reconnectEvery: 15 * time.Millisecond,
		dialThrottle:   15 * time.Millisecond,
	}
}

func effectFrame(sceneID, role string, level float64, ttlMs int) map[string]json.RawMessage {
	raw, _ := json.Marshal(map[string]interface{}{
		"sceneId": sceneID,
		"ttlMs":   ttlMs,
		"layers": []map[string]interface{}{
			{"role": role, "primitive": map[string]interface{}{"level": level}},
		},
	})
	var frame map[string]json.RawMessage
	json.Unmarshal(raw, &frame)
	return frame
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
	x.connect()
	go x.reconnectLoop()

	x.applyEffect("t|", effectFrame("d:hit", "impact", 0.8, 5000))
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

func TestOwedZeroIsDeliveredAfterReconnect(t *testing.T) {
	f := &fakeXToys{}
	srv := httptest.NewServer(http.HandlerFunc(f.handler))
	defer srv.Close()

	x := newTestAdapter(wsURLOf(srv))
	x.connect()
	go x.reconnectLoop()

	x.applyEffect("t|", effectFrame("d:hit", "impact", 0.8, 5000))
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

func TestPerRoleTakesStrongestLevel(t *testing.T) {
	f := &fakeXToys{}
	srv := httptest.NewServer(http.HandlerFunc(f.handler))
	defer srv.Close()
	x := newTestAdapter(wsURLOf(srv))
	x.connect()

	// Two live scenes on the same role combine to the stronger level, not overwrite each other.
	x.applyEffect("t|", effectFrame("d:a", "texture", 0.3, 5000))
	x.applyEffect("t|", effectFrame("c:mining", "texture", 0.6, 5000))
	waitFor(t, "the stronger texture level wins", func() bool {
		v, ok := f.latest("minegasm-texture")
		// 0.6 scaled into [20,100] is 68; the weaker 0.3 would be 44.
		return ok && v >= 60
	})
}
