package net.minegasm.pack;

import net.minegasm.core.GameEventKind;
import net.minegasm.core.HapticScene;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A shareable set of event to scene mappings (brief 0003 §2.3), the unit users author, export, and
 * import as a single file. It is device-independent authoring data: {@link #resolve} materializes a
 * plain {@link HapticScene} that then flows through the same mixer, scheduler, and {@code SafetyCaps}
 * path as the built-in code packs, so a file pack can never exceed the engine's output ceilings.
 */
public final class ScenePack {

    /** The format version this build writes and can read. A newer file is rejected fail-closed. */
    public static final int SCHEMA_VERSION = 1;

    private final int schemaVersion;
    private final String packId;
    private final String name;
    private final String author;
    private final String description;
    private final List<PackTrigger> triggers;

    public ScenePack(int schemaVersion, String packId, String name, String author, String description,
                     List<PackTrigger> triggers) {
        if (packId == null || packId.trim().isEmpty()) {
            throw new IllegalArgumentException("packId required");
        }
        this.schemaVersion = schemaVersion;
        this.packId = packId;
        this.name = name == null ? "" : name;
        this.author = author == null ? "" : author;
        this.description = description == null ? "" : description;
        this.triggers = triggers == null
                ? Collections.<PackTrigger>emptyList()
                : Collections.unmodifiableList(new ArrayList<>(triggers));
    }

    public int schemaVersion() {
        return schemaVersion;
    }

    public String packId() {
        return packId;
    }

    public String name() {
        return name;
    }

    public String author() {
        return author;
    }

    public String description() {
        return description;
    }

    public List<PackTrigger> triggers() {
        return triggers;
    }

    /**
     * Materialize the scene this pack maps {@code kind} to, at {@code nowNs}. The first trigger for the
     * event wins; an event this pack does not cover returns empty so the caller can fall through.
     */
    public Optional<HapticScene> resolve(GameEventKind kind, long nowNs) {
        return resolve(kind, nowNs, 1f, 1f);
    }

    /**
     * Materialize the scene for {@code kind}, scaling each layer by the user's volume ({@code userGain})
     * and the event's {@code strength} through the layer's strength response (brief 0003 §2.4).
     */
    public Optional<HapticScene> resolve(GameEventKind kind, long nowNs, float userGain, float strength) {
        for (PackTrigger trigger : triggers) {
            if (trigger.event() == kind) {
                return Optional.of(trigger.scene().materialize(packId, kind, nowNs, userGain, strength));
            }
        }
        return Optional.empty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ScenePack)) {
            return false;
        }
        ScenePack other = (ScenePack) o;
        return schemaVersion == other.schemaVersion
                && Objects.equals(packId, other.packId)
                && Objects.equals(name, other.name)
                && Objects.equals(author, other.author)
                && Objects.equals(description, other.description)
                && Objects.equals(triggers, other.triggers);
    }

    @Override
    public int hashCode() {
        return Objects.hash(schemaVersion, packId, name, author, description, triggers);
    }

    @Override
    public String toString() {
        return "ScenePack[schemaVersion=" + schemaVersion + ", packId=" + packId + ", name=" + name
                + ", author=" + author + ", triggers=" + triggers + "]";
    }
}
