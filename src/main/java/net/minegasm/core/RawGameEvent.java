package net.minegasm.core;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

/**
 * An immutable raw observation emitted by the Minecraft layer. Carries the game tick for ordering
 * and deduplication and a monotonic timestamp for real-time expiry. The payload is an opaque,
 * read-only map so the observation layer can attach context (block id, damage, xp amount, â€¦)
 * without the core depending on Minecraft types (brief Â§5.2).
 */
public record RawGameEvent(
        GameEventKind kind,
        long gameTick,
        long observedAtNs,
        Map<String, Object> payload) {

    public RawGameEvent {
        if (kind == null) {
            throw new IllegalArgumentException("event kind required");
        }
        payload = payload == null ? Map.of() : Collections.unmodifiableMap(Map.copyOf(payload));
    }

    public static RawGameEvent of(GameEventKind kind, long gameTick, long observedAtNs) {
        return new RawGameEvent(kind, gameTick, observedAtNs, Map.of());
    }

    @SuppressWarnings("unchecked")
    public <T> Optional<T> get(String key, Class<T> type) {
        Object v = payload.get(key);
        return type.isInstance(v) ? Optional.of((T) v) : Optional.empty();
    }

    public float getFloat(String key, float fallback) {
        Object v = payload.get(key);
        return v instanceof Number n ? n.floatValue() : fallback;
    }

    public int getInt(String key, int fallback) {
        Object v = payload.get(key);
        return v instanceof Number n ? n.intValue() : fallback;
    }

    public boolean getBool(String key, boolean fallback) {
        Object v = payload.get(key);
        return v instanceof Boolean b ? b : fallback;
    }
}

