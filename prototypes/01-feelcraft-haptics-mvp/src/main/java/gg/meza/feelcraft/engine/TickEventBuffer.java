package gg.meza.feelcraft.engine;
import gg.meza.feelcraft.core.GameHapticEvent;
import java.util.ArrayList;
import java.util.List;
public final class TickEventBuffer {
    private static final int MAX_EVENTS_PER_TICK = 128;
    private final List<GameHapticEvent> events = new ArrayList<>();
    public void record(GameHapticEvent event) { if (events.size() < MAX_EVENTS_PER_TICK) events.add(event); else if (event.priority() >= 80) { events.remove(0); events.add(event); } }
    public List<GameHapticEvent> drain() { List<GameHapticEvent> copy = List.copyOf(events); events.clear(); return copy; }
    public void clear() { events.clear(); }
}
