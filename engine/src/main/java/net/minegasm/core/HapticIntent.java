package net.minegasm.core;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * A device-independent, normalized description of "something meaningful happened": the aggregator's
 * output and the recipe resolver's input. Strength/urgency are normalized {@code [0, 1]} (brief
 * §5.2). Tags carry qualifiers such as {@code "critical"}, {@code "ore"}, or {@code "levelup"}.
 */
public final class HapticIntent {

    private final GameEventKind kind;
    private final String eventKey;
    private final float strength;
    private final float urgency;
    private final MaterialFeel material;
    private final SpatialDirection direction;
    private final Set<String> tags;
    private final long gameTick;
    private final long createdAtNs;

    public HapticIntent(
            GameEventKind kind,
            String eventKey,
            float strength,
            float urgency,
            MaterialFeel material,
            SpatialDirection direction,
            Set<String> tags,
            long gameTick,
            long createdAtNs) {
        if (kind == null) {
            throw new IllegalArgumentException("intent kind required");
        }
        this.kind = kind;
        this.eventKey = eventKey == null ? kind.key() : eventKey;
        this.strength = net.minegasm.util.HapticMath.clamp01(strength);
        this.urgency = net.minegasm.util.HapticMath.clamp01(urgency);
        this.material = material == null ? MaterialFeel.UNKNOWN : material;
        this.direction = direction == null ? SpatialDirection.NONE : direction;
        this.tags = tags == null
                ? Collections.emptySet()
                : Collections.unmodifiableSet(new LinkedHashSet<>(tags));
        this.gameTick = gameTick;
        this.createdAtNs = createdAtNs;
    }

    public GameEventKind kind() {
        return kind;
    }

    public String eventKey() {
        return eventKey;
    }

    public float strength() {
        return strength;
    }

    public float urgency() {
        return urgency;
    }

    public MaterialFeel material() {
        return material;
    }

    public SpatialDirection direction() {
        return direction;
    }

    public Set<String> tags() {
        return tags;
    }

    public long gameTick() {
        return gameTick;
    }

    public long createdAtNs() {
        return createdAtNs;
    }

    public boolean hasTag(String tag) {
        return tags.contains(tag);
    }

    /** Builder-style helper for the common case with no material/direction/tags. */
    public static HapticIntent simple(GameEventKind kind, float strength, long gameTick, long nowNs) {
        return new HapticIntent(kind, kind.key(), strength, strength,
                MaterialFeel.UNKNOWN, SpatialDirection.NONE, Collections.emptySet(), gameTick, nowNs);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof HapticIntent)) {
            return false;
        }
        HapticIntent other = (HapticIntent) o;
        return Float.compare(strength, other.strength) == 0
                && Float.compare(urgency, other.urgency) == 0
                && gameTick == other.gameTick
                && createdAtNs == other.createdAtNs
                && kind == other.kind
                && Objects.equals(eventKey, other.eventKey)
                && material == other.material
                && direction == other.direction
                && Objects.equals(tags, other.tags);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, eventKey, strength, urgency, material, direction, tags, gameTick,
                createdAtNs);
    }

    @Override
    public String toString() {
        return "HapticIntent[kind=" + kind + ", eventKey=" + eventKey + ", strength=" + strength
                + ", urgency=" + urgency + ", material=" + material + ", direction=" + direction
                + ", tags=" + tags + ", gameTick=" + gameTick + ", createdAtNs=" + createdAtNs + "]";
    }
}
