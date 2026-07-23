package net.minegasm.core;

import java.util.Collections;
import java.util.Set;

/**
 * A device-independent, normalized description of "something meaningful happened": the aggregator's
 * output and the recipe resolver's input. Strength/urgency are normalized {@code [0, 1]} (brief
 * §5.2). Tags carry qualifiers such as {@code "critical"}, {@code "ore"}, or {@code "levelup"}.
 */
public record HapticIntent(
        GameEventKind kind,
        String eventKey,
        float strength,
        float urgency,
        MaterialFeel material,
        SpatialDirection direction,
        Set<String> tags,
        long gameTick,
        long createdAtNs) {

    public HapticIntent {
        if (kind == null) {
            throw new IllegalArgumentException("intent kind required");
        }
        eventKey = eventKey == null ? kind.key() : eventKey;
        strength = net.minegasm.util.HapticMath.clamp01(strength);
        urgency = net.minegasm.util.HapticMath.clamp01(urgency);
        material = material == null ? MaterialFeel.UNKNOWN : material;
        direction = direction == null ? SpatialDirection.NONE : direction;
        tags = tags == null ? Set.of() : Collections.unmodifiableSet(Set.copyOf(tags));
    }

    public boolean hasTag(String tag) {
        return tags.contains(tag);
    }

    /** Builder-style helper for the common case with no material/direction/tags. */
    public static HapticIntent simple(GameEventKind kind, float strength, long gameTick, long nowNs) {
        return new HapticIntent(kind, kind.key(), strength, strength,
                MaterialFeel.UNKNOWN, SpatialDirection.NONE, Set.of(), gameTick, nowNs);
    }
}

