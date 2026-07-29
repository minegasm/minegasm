package net.minegasm.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * An immutable raw observation emitted by the Minecraft layer. Carries the game tick for ordering
 * and deduplication and a monotonic timestamp for real-time expiry. The payload is an opaque,
 * read-only map so the observation layer can attach context (block id, damage, xp amount, …)
 * without the core depending on Minecraft types (brief §5.2).
 */
public final class RawGameEvent {

    private final GameEventKind kind;
    private final long gameTick;
    private final long observedAtNs;
    private final Map<String, Object> payload;

    public RawGameEvent(GameEventKind kind, long gameTick, long observedAtNs,
                        Map<String, Object> payload) {
        if (kind == null) {
            throw new IllegalArgumentException("event kind required");
        }
        this.kind = kind;
        this.gameTick = gameTick;
        this.observedAtNs = observedAtNs;
        this.payload = payload == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(payload));
    }

    public GameEventKind kind() {
        return kind;
    }

    public long gameTick() {
        return gameTick;
    }

    public long observedAtNs() {
        return observedAtNs;
    }

    public Map<String, Object> payload() {
        return payload;
    }

    public static RawGameEvent of(GameEventKind kind, long gameTick, long observedAtNs) {
        return new RawGameEvent(kind, gameTick, observedAtNs, Collections.emptyMap());
    }

    @SuppressWarnings("unchecked")
    public <T> Optional<T> get(String key, Class<T> type) {
        Object v = payload.get(key);
        return type.isInstance(v) ? Optional.of((T) v) : Optional.empty();
    }

    public float getFloat(String key, float fallback) {
        Object v = payload.get(key);
        return v instanceof Number ? ((Number) v).floatValue() : fallback;
    }

    public int getInt(String key, int fallback) {
        Object v = payload.get(key);
        return v instanceof Number ? ((Number) v).intValue() : fallback;
    }

    public boolean getBool(String key, boolean fallback) {
        Object v = payload.get(key);
        return v instanceof Boolean ? (Boolean) v : fallback;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RawGameEvent)) {
            return false;
        }
        RawGameEvent other = (RawGameEvent) o;
        return gameTick == other.gameTick
                && observedAtNs == other.observedAtNs
                && kind == other.kind
                && Objects.equals(payload, other.payload);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, gameTick, observedAtNs, payload);
    }

    @Override
    public String toString() {
        return "RawGameEvent[kind=" + kind + ", gameTick=" + gameTick
                + ", observedAtNs=" + observedAtNs + ", payload=" + payload + "]";
    }
}
